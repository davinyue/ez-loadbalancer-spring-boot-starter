package org.rdlinux.loadbalancer.autoconfigure;

import org.rdlinux.loadbalancer.filter.RegionZoneFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet 环境下的 LoadBalancer 配置
 * <p>
 * 只在 Servlet API 存在时才会加载此配置类
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "javax.servlet.Filter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletLoadBalancerConfiguration {

    /**
     * 注册 RegionZoneFilter (Servlet 版本)
     * <p>
     * 从 HTTP 请求头中读取区域和机房信息,支持动态负载均衡
     * <p>
     * 优先级设置为 Integer.MIN_VALUE + 6,确保在链路追踪之后执行
     *
     * @return FilterRegistrationBean
     */
    @Bean
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
}
