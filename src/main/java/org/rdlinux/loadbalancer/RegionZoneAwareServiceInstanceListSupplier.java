package org.rdlinux.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 区域和机房感知的服务实例列表提供者
 * <p>
 * 实现基于区域(region)、机房(zone)和可访问区域机房列表(available-region-zones)的服务实例过滤策略:
 * 1. 优先选择同区域同机房的实例(最优先,无需检查可访问列表)
 * 2. 如果同机房不可用,选择可访问区域机房的实例(检查 available-region-zones)
 * 3. 如果可访问区域机房没有实例,选择同区域所有机房的实例(忽略访问限制)
 * 4. 如果同区域没有可用实例,选择其他区域的实例
 * <p>
 * 服务实例可以通过 metadata 配置可访问的区域和机房列表来限制访问:
 * 
 * <pre>
 * eureka:
 *   instance:
 *     metadata-map:
 *       region: A
 *       zone: zone-1
 *       # 格式: region1:zone1,zone2;region2:zone3,zone4
 *       available-region-zones: "A:zone-1,zone-2;B:zone-1"  # 允许 A 区域的 zone-1、zone-2 和 B 区域的 zone-1 访问
 * </pre>
 * <p>
 * 配合 RoundRobinLoadBalancer 使用,实现区域和机房感知的负载均衡
 */
@Slf4j
public class RegionZoneAwareServiceInstanceListSupplier implements ServiceInstanceListSupplier {

    /**
     * Eureka 元数据 key: 可访问的区域和机房列表
     * 格式: region1:zone1,zone2;region2:zone3,zone4
     * 例如: A:zone-1,zone-2;B:zone-1
     */
    private static final String METADATA_KEY_AVAILABLE_REGION_ZONES = "available-region-zones";

    private final ServiceInstanceListSupplier delegate;
    private final String localZone;
    private final String localRegion;

    /**
     * 缓存访问控制结果
     * Key: instanceId + ":" + region + ":" + zone
     * Value: 是否可访问
     */
    private final Map<String, Boolean> accessCache = new ConcurrentHashMap<>();

    public RegionZoneAwareServiceInstanceListSupplier(
            ServiceInstanceListSupplier delegate,
            ConfigurableApplicationContext context) {

        this.delegate = delegate;

        // 直接读取当前服务自己注册到 Eureka 的 metadata
        ServiceInstance localInstance = context.getBean(ServiceInstance.class);
        this.localZone = localInstance.getMetadata().get(RegionZoneContext.METADATA_KEY_ZONE);
        this.localRegion = localInstance.getMetadata().get(RegionZoneContext.METADATA_KEY_REGION);

        log.info("RegionZoneAwareServiceInstanceListSupplier initialized with localRegion={}, localZone={}",
                this.localRegion, this.localZone);
    }

    @Override
    public String getServiceId() {
        return this.delegate.getServiceId();
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return this.delegate.get()
                .map(this::filterByRegionAndZone);
    }

    /**
     * 获取当前请求的区域
     * <p>
     * 优先从 ThreadLocal 中获取(由 RegionZoneFilter 设置),
     * 如果 ThreadLocal 中没有,则使用服务实例自身的区域
     *
     * @return 当前区域
     */
    private String getCurrentRegion() {
        String region = RegionZoneContext.getRegion();
        return region != null ? region : this.localRegion;
    }

    /**
     * 获取当前请求的机房
     * <p>
     * 优先从 ThreadLocal 中获取(由 RegionZoneFilter 设置),
     * 如果 ThreadLocal 中没有,则使用服务实例自身的机房
     *
     * @return 当前机房
     */
    private String getCurrentZone() {
        String zone = RegionZoneContext.getZone();
        return zone != null ? zone : this.localZone;
    }

    /**
     * 根据区域、机房和可访问区域机房列表过滤服务实例
     * <p>
     * 优先级策略:
     * 1. 同区域同机房(最优先,无需检查可访问列表)
     * 2. 可访问区域机房(在服务配置的 available-region-zones 中,或未配置时同区域所有机房)
     * 3. 同区域所有机房(忽略 available-region-zones 限制,作为降级策略)
     * 4. 所有区域(兜底策略)
     * <p>
     * 服务实例可以通过 metadata 配置 available-region-zones 来限制哪些区域和机房可以访问:
     * - 配置 available-region-zones: 只允许列表中的区域和机房访问
     * - 不配置 available-region-zones: 允许同区域所有机房访问
     * <p>
     * 格式: region1:zone1,zone2;region2:zone3,zone4
     * 例如: A:zone-1,zone-2;B:zone-1
     *
     * @param instances 原始服务实例列表
     * @return 过滤后的服务实例列表
     */
    private List<ServiceInstance> filterByRegionAndZone(List<ServiceInstance> instances) {

        if (instances == null || instances.isEmpty()) {
            log.debug("No instances available for service: {}", getServiceId());
            return instances;
        }

        // 获取当前请求的区域和机房(优先使用 ThreadLocal 中的值)
        String currentRegion = getCurrentRegion();
        String currentZone = getCurrentZone();

        log.debug("Filtering {} instances for service: {}, currentRegion={}, currentZone={}",
                instances.size(), getServiceId(), currentRegion, currentZone);

        // 1️⃣ 优先选择: 同区域同机房(无需检查 available-zones)
        List<ServiceInstance> sameZone = instances.stream()
                .filter(i -> {
                    String instanceRegion = i.getMetadata().get(RegionZoneContext.METADATA_KEY_REGION);
                    String instanceZone = i.getMetadata().get(RegionZoneContext.METADATA_KEY_ZONE);
                    return Objects.equals(currentRegion, instanceRegion)
                            && Objects.equals(currentZone, instanceZone);
                })
                .collect(Collectors.toList());

        if (!sameZone.isEmpty()) {
            log.debug("Found {} instances in same region({}) and zone({})",
                    sameZone.size(), currentRegion, currentZone);
            return sameZone;
        }

        // 2️⃣ 次优选择: 同区域可用机房
        // 包括: 配置了 available-region-zones 且当前区域和机房在列表中,或未配置 available-region-zones
        List<ServiceInstance> sameRegionAvailableZones = instances.stream()
                .filter(i -> {
                    String instanceRegion = i.getMetadata().get(RegionZoneContext.METADATA_KEY_REGION);

                    // 必须是同区域
                    if (!Objects.equals(currentRegion, instanceRegion)) {
                        return false;
                    }

                    // 检查当前调用方的区域和机房是否在实例的可访问列表中
                    // 如果未配置 available-region-zones, isRegionZoneAvailable 只允许同区域访问
                    return isRegionZoneAvailable(i, currentRegion, currentZone);
                })
                .collect(Collectors.toList());

        if (!sameRegionAvailableZones.isEmpty()) {
            log.debug("Found {} instances in same region({}) with available zones for zone({})",
                    sameRegionAvailableZones.size(), currentRegion, currentZone);
            return sameRegionAvailableZones;
        }

        // 3️⃣ 降级选择: 同区域所有机房(忽略 available-zones 限制)
        // 这一步只会匹配到配置了 available-zones 但当前机房不在列表中的实例
        List<ServiceInstance> sameRegion = instances.stream()
                .filter(i -> {
                    String instanceRegion = i.getMetadata().get(RegionZoneContext.METADATA_KEY_REGION);
                    return Objects.equals(currentRegion, instanceRegion);
                })
                .collect(Collectors.toList());

        if (!sameRegion.isEmpty()) {
            log.debug("Found {} instances in same region({}) (ignoring available-zones restriction)",
                    sameRegion.size(), currentRegion);
            return sameRegion;
        }

        // 4️⃣ 兜底策略: 返回所有其他区域的实例
        log.debug("No instances found in same region({}), returning all {} instances from other regions",
                currentRegion, instances.size());
        return instances;
    }

    /**
     * 检查指定的区域和机房是否在服务实例的可访问列表中
     * <p>
     * 服务实例通过 metadata 中的 available-region-zones 配置可访问的区域和机房列表
     * 格式: region1:zone1,zone2;region2:zone3,zone4
     * 例如: A:zone-1,zone-2;B:zone-1
     * <p>
     * 如果没有配置 available-region-zones,则认为对同区域所有机房可用
     * <p>
     * 使用缓存机制避免重复解析相同实例的配置
     *
     * @param instance 服务实例
     * @param region   要检查的区域
     * @param zone     要检查的机房
     * @return 如果该区域和机房可访问返回 true,否则返回 false
     */
    private boolean isRegionZoneAvailable(ServiceInstance instance, String region, String zone) {
        // 生成缓存 key: instanceId:region:zone
        String cacheKey = instance.getInstanceId() + ":" + region + ":" + zone;

        // 使用 computeIfAbsent 保证线程安全,避免重复计算
        return accessCache.computeIfAbsent(cacheKey, key -> {
            String availableRegionZones = instance.getMetadata().get(METADATA_KEY_AVAILABLE_REGION_ZONES);

            // 如果没有配置 available-region-zones,认为对同区域所有机房可用
            if (availableRegionZones == null || availableRegionZones.trim().isEmpty()) {
                // 只允许同区域访问
                String instanceRegion = instance.getMetadata().get(RegionZoneContext.METADATA_KEY_REGION);
                return Objects.equals(instanceRegion, region);
            }

            // 解析配置: region1:zone1,zone2;region2:zone3,zone4
            String[] regionEntries = availableRegionZones.split(";");
            for (String regionEntry : regionEntries) {
                String[] parts = regionEntry.split(":");
                if (parts.length != 2) {
                    continue; // 格式不正确,跳过
                }

                String configRegion = parts[0].trim();
                String configZones = parts[1].trim();

                // 检查区域是否匹配
                if (!configRegion.equals(region)) {
                    continue;
                }

                // 检查机房是否在列表中
                String[] zones = configZones.split(",");
                for (String configZone : zones) {
                    if (configZone.trim().equals(zone)) {
                        return true;
                    }
                }
            }

            return false;
        });
    }
}
