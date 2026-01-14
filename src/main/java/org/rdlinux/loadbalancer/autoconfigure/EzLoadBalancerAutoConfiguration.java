package org.rdlinux.loadbalancer.autoconfigure;

import org.rdlinux.loadbalancer.RegionZoneAwareServiceInstanceListSupplier;
import org.rdlinux.loadbalancer.filter.RegionZoneFilter;
import org.rdlinux.loadbalancer.filter.RegionZoneWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * EZ LoadBalancer 自动配置类
 * <p>
 * 当类路径中存在 DiscoveryClient 和 ServiceInstanceListSupplier 时自动启用
 * <p>
 * 功能:
 * 1. 配置基于区域和机房感知的负载均衡策略
 * 2. 根据应用类型注册对应的 Filter 支持动态负载均衡
 * - Servlet 应用: 注册 RegionZoneFilter
 * - Reactive 应用(如 Gateway): 注册 RegionZoneWebFilter
 * <p>
 * 负载均衡策略:
 * 1. 使用 RegionZoneAwareServiceInstanceListSupplier 过滤服务实例
 * 2. 配合默认的 RoundRobinLoadBalancer 实现轮询负载均衡
 * <p>
 * 实例选择优先级:
 * - 优先选择同区域同机房的实例
 * - 如果同机房没有可用实例,选择同区域其他机房的实例
 * - 如果同区域没有可用实例,选择其他区域的实例
 */
@AutoConfiguration
@ConditionalOnClass({ DiscoveryClient.class, ServiceInstanceListSupplier.class })
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients(defaultConfiguration = EzLoadBalancerAutoConfiguration.class)
public class EzLoadBalancerAutoConfiguration {

    /**
     * 注册区域和机房感知的服务实例列表提供者
     * <p>
     * 该 Bean 会被 Spring Cloud LoadBalancer 自动使用
     * 配合默认的 RoundRobinLoadBalancer 实现区域和机房感知的轮询负载均衡
     * <p>
     * Spring Cloud LoadBalancer 会为每个服务自动注入正确的 Environment(包含 serviceId)
     *
     * @param discoveryClient 服务发现客户端 (Eureka/Nacos/Consul/Zookeeper)
     * @param environment     Spring Environment(包含当前服务的 serviceId)
     * @param context         Spring 应用上下文
     * @return 区域和机房感知的服务实例列表提供者
     */
    @Bean
    public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
            DiscoveryClient discoveryClient,
            Environment environment,
            ConfigurableApplicationContext context) {

        // 首先创建默认的 DiscoveryClientServiceInstanceListSupplier
        // 它会从服务发现客户端获取服务实例列表
        // Environment 中包含了当前要查询的服务的 serviceId
        DiscoveryClientServiceInstanceListSupplier delegate = new DiscoveryClientServiceInstanceListSupplier(
                discoveryClient, environment);

        // 使用 RegionZoneAwareServiceInstanceListSupplier 包装默认提供者
        // 实现区域和机房感知的实例过滤
        return new RegionZoneAwareServiceInstanceListSupplier(delegate, context);
    }

    /**
     * 注册 RegionZoneFilter (Servlet 版本)
     * <p>
     * 只在 Servlet Web 应用中启用
     * 从 HTTP 请求头中读取区域和机房信息,支持动态负载均衡
     * <p>
     * 优先级设置为 Integer.MIN_VALUE + 6,确保在链路追踪之后执行
     *
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<RegionZoneFilter> regionZoneFilter() {
        FilterRegistrationBean<RegionZoneFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RegionZoneFilter());
        registration.addUrlPatterns("/*");
        registration.setName("regionZoneFilter");
        // TraceWebServletAutoConfiguration配置traceWebFilter时,
        // 指定了该filter的默认顺序为TraceHttpAutoConfiguration.TRACING_FILTER_ORDER,
        // 它的值为Integer.MIN_VALUE + 5， 此处配置为Integer.MIN_VALUE + 6是为了让链路追踪拦截器
        // 在本拦截器的前面执行, 方便初始化链路追踪信息
        registration.setOrder(Integer.MIN_VALUE + 6);
        return registration;
    }

    /**
     * 注册 RegionZoneWebFilter (WebFlux 版本)
     * <p>
     * 只在 Reactive Web 应用中启用 (如 Spring Cloud Gateway)
     * 从 HTTP 请求头中读取区域和机房信息,支持动态负载均衡
     * <p>
     * 执行顺序通过 Ordered 接口指定为 Integer.MIN_VALUE + 6,确保在链路追踪之后执行
     *
     * @return RegionZoneWebFilter
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public RegionZoneWebFilter regionZoneWebFilter() {
        return new RegionZoneWebFilter();
    }
}
