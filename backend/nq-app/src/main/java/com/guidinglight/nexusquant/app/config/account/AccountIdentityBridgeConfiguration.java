package com.guidinglight.nexusquant.app.config.account;

import com.guidinglight.nexusquant.account.infra.jdbc.CanonicalLegacyAccountBridgeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** SIM 创建链与既有 LIVE 调用方共享同一个兼容账户身份桥。 */
@Configuration(proxyBeanMethods = false)
public class AccountIdentityBridgeConfiguration {

    @Bean
    public CanonicalLegacyAccountBridgeService canonicalLegacyAccountBridgeService(JdbcTemplate jdbc) {
        return new CanonicalLegacyAccountBridgeService(jdbc);
    }
}
