package org.rdlinux.loadbalancer;

/**
 * 区域和机房上下文
 * <p>
 * 使用 ThreadLocal 存储当前请求的区域和机房信息
 * 支持从 HTTP 请求头动态获取区域和机房,实现请求级别的负载均衡策略
 */
public class RegionZoneContext {

    /**
     * Eureka 元数据 key: 区域
     */
    public static final String METADATA_KEY_REGION = "region";

    /**
     * Eureka 元数据 key: 机房
     */
    public static final String METADATA_KEY_ZONE = "zone";

    /**
     * HTTP 请求头 key: 区域
     */
    public static final String HEADER_KEY_REGION = "X-Region";

    /**
     * HTTP 请求头 key: 机房
     */
    public static final String HEADER_KEY_ZONE = "X-Zone";

    private static final ThreadLocal<String> REGION_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ZONE_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程的区域
     *
     * @param region 区域
     */
    public static void setRegion(String region) {
        REGION_HOLDER.set(region);
    }

    /**
     * 获取当前线程的区域
     *
     * @return 区域,如果未设置则返回 null
     */
    public static String getRegion() {
        return REGION_HOLDER.get();
    }

    /**
     * 设置当前线程的机房
     *
     * @param zone 机房
     */
    public static void setZone(String zone) {
        ZONE_HOLDER.set(zone);
    }

    /**
     * 获取当前线程的机房
     *
     * @return 机房,如果未设置则返回 null
     */
    public static String getZone() {
        return ZONE_HOLDER.get();
    }

    /**
     * 清理当前线程的区域和机房信息
     * <p>
     * 必须在请求结束时调用,避免内存泄漏
     */
    public static void clear() {
        REGION_HOLDER.remove();
        ZONE_HOLDER.remove();
    }
}
