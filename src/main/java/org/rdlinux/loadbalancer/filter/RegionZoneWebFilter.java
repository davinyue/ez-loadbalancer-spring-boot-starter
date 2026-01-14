package org.rdlinux.loadbalancer.filter;

import org.rdlinux.loadbalancer.RegionZoneContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 区域和机房过滤器 (WebFlux 版本)
 * <p>
 * 从 HTTP 请求头中读取区域和机房信息,并存储到 ThreadLocal 中
 * 支持动态的负载均衡策略,可以根据请求头指定的区域和机房进行服务调用
 * <p>
 * 适用于 Spring Cloud Gateway 等基于 WebFlux 的响应式应用
 * <p>
 * 请求头:
 * - X-Region: 区域标识
 * - X-Zone: 机房标识
 * <p>
 * 执行顺序: Integer.MIN_VALUE + 6 (在链路追踪之后执行)
 */
public class RegionZoneWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RegionZoneWebFilter.class);

    @NonNull
    @Override
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        // 从请求头中读取区域和机房信息
        String region = exchange.getRequest().getHeaders().getFirst(RegionZoneContext.HEADER_KEY_REGION);
        String zone = exchange.getRequest().getHeaders().getFirst(RegionZoneContext.HEADER_KEY_ZONE);

        // 存储到 ThreadLocal
        if (region != null && !region.trim().isEmpty()) {
            RegionZoneContext.setRegion(region.trim());
            log.debug("Set region from request header: {}", region);
        }

        if (zone != null && !zone.trim().isEmpty()) {
            RegionZoneContext.setZone(zone.trim());
            log.debug("Set zone from request header: {}", zone);
        }

        // 继续执行后续过滤器和请求处理,并在完成后清理 ThreadLocal
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // 请求结束后清理 ThreadLocal,避免内存泄漏
                    RegionZoneContext.clear();
                    log.trace("Cleared RegionZoneContext");
                });
    }

    @Override
    public int getOrder() {
        // 与 Servlet 版本保持一致的顺序
        // 确保在链路追踪之后执行 (TraceHttpAutoConfiguration.TRACING_FILTER_ORDER =
        // Integer.MIN_VALUE + 5)
        return Integer.MIN_VALUE + 6;
    }
}
