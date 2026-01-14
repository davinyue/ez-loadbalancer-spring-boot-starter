package org.rdlinux.loadbalancer.filter;

import org.rdlinux.loadbalancer.RegionZoneContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 区域和机房过滤器
 * <p>
 * 从 HTTP 请求头中读取区域和机房信息,并存储到 ThreadLocal 中
 * 支持动态的负载均衡策略,可以根据请求头指定的区域和机房进行服务调用
 * <p>
 * 请求头:
 * - X-Region: 区域标识
 * - X-Zone: 机房标识
 */
public class RegionZoneFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RegionZoneFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("RegionZoneFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest) {
                HttpServletRequest httpRequest = (HttpServletRequest) request;

                // 从请求头中读取区域和机房信息
                String region = httpRequest.getHeader(RegionZoneContext.HEADER_KEY_REGION);
                String zone = httpRequest.getHeader(RegionZoneContext.HEADER_KEY_ZONE);

                // 存储到 ThreadLocal
                if (region != null && !region.trim().isEmpty()) {
                    RegionZoneContext.setRegion(region.trim());
                    log.debug("Set region from request header: {}", region);
                }

                if (zone != null && !zone.trim().isEmpty()) {
                    RegionZoneContext.setZone(zone.trim());
                    log.debug("Set zone from request header: {}", zone);
                }
            }

            // 继续执行后续过滤器和请求处理
            chain.doFilter(request, response);
        } finally {
            // 请求结束后清理 ThreadLocal,避免内存泄漏
            RegionZoneContext.clear();
            log.trace("Cleared RegionZoneContext");
        }
    }

    @Override
    public void destroy() {
        log.info("RegionZoneFilter destroyed");
    }
}
