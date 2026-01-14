package org.rdlinux.loadbalancer.autoconfigure;

import org.rdlinux.loadbalancer.filter.RegionZoneWebFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reactive 环境下的 LoadBalancer 配置
 * <p>
 * 只在 WebFlux API 存在时才会加载此配置类
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveLoadBalancerConfiguration {

    /**
     * 注册 RegionZoneWebFilter (WebFlux 版本)
     * <p>
     * 从 HTTP 请求头中读取区域和机房信息,支持动态负载均衡
     * <p>
     * 执行顺序通过 Ordered 接口指定为 Integer.MIN_VALUE + 6,确保在链路追踪之后执行
     *
     * @return RegionZoneWebFilter
     */
    @Bean
    public RegionZoneWebFilter regionZoneWebFilter() {
        return new RegionZoneWebFilter();
    }
}
