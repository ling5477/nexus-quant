package com.guidinglight.nexusquant.observability.config;

import com.guidinglight.nexusquant.observability.web.TraceIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

/**
 * ObservabilityAutoConfiguration 统一装配可观测性基础组件。
 */
@Configuration
public class ObservabilityAutoConfiguration {

    /**
     * 注册 trace 过滤器。
     *
     * @return HTTP 请求链路 trace 过滤器
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
