package com.guidinglight.nexusquant.observability.config;

import com.guidinglight.nexusquant.observability.web.TraceIdFilter;
import jakarta.servlet.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public Filter traceIdFilter() {
        return new TraceIdFilter();
    }
}
