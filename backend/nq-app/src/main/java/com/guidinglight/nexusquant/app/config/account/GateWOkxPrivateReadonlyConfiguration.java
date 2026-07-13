package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.gatew.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateReadonlyProbeService;
import com.guidinglight.nexusquant.adapter.okx.service.JdkOkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GateW-2 显式 composition root。
 *
 * <p>只有 profile=gatew-okx-readonly、feature flag=true 且 LIVE=false 时装配；Bean 创建不读取
 * credential、不执行 probe、不访问网络，也不注册 scheduler/runner/mutating adapter。</p>
 */
@Configuration
@Profile("gatew-okx-readonly")
@ConditionalOnProperty(
        prefix = "nq.gatew.okx-private-readonly",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@ConditionalOnProperty(
        prefix = "nq.env-safety",
        name = "live-enabled",
        havingValue = "false",
        matchIfMissing = false
)
public class GateWOkxPrivateReadonlyConfiguration {

    @Bean
    public OkxPrivateReadTransport gateWOkxPrivateReadTransport(ObjectMapper objectMapper) {
        return new JdkOkxPrivateReadTransport(objectMapper, Clock.systemUTC());
    }

    @Bean
    public OkxPrivateCredentialExecutor gateWOkxPrivateCredentialExecutor(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AccountCredentialRuntimeProperties properties,
            OkxPrivateReadTransport transport
    ) {
        return new JdbcOkxPrivateCredentialExecutor(
                jdbcTemplate,
                objectMapper,
                properties.getMasterKey(),
                transport
        );
    }

    @Bean
    public OkxPrivateReadonlyProbeService gateWOkxPrivateReadonlyProbeService(
            ExchangeAccountRepository exchangeAccountRepository,
            OkxPrivateCredentialExecutor credentialExecutor
    ) {
        return new OkxPrivateReadonlyProbeService(
                exchangeAccountRepository,
                credentialExecutor,
                Clock.systemUTC()
        );
    }
}
