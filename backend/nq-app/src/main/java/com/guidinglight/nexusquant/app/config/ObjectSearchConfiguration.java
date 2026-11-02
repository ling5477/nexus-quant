package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.search.application.ObjectSearchService;
import com.guidinglight.nexusquant.search.infra.JdbcObjectSearchRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 在组合根装配搜索服务和只读持久化实现；保持应用层与JDBC边界，不加入市场或交易执行器。
 */
@Configuration(proxyBeanMethods = false)
public class ObjectSearchConfiguration {
    @Bean
    public ObjectSearchService objectSearchService(JdbcTemplate jdbc) {
        return new ObjectSearchService(new JdbcObjectSearchRepository(jdbc));
    }
}
