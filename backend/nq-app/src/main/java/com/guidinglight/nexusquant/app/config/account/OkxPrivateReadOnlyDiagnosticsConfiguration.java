package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateReadonlyProbeService;
import com.guidinglight.nexusquant.adapter.okx.service.JdkOkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.app.config.CapabilityPropertyResolver;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * OKX private read-only diagnostics 的显式 composition root。
 *
 * <p>只有受控 read-only profile、feature flag=true，且 CI/no-outbound/LIVE/交易写侧全部显式关闭时装配；
 * Bean 创建不读取 credential、不执行 probe、不访问网络，也不注册 scheduler/runner/mutating adapter。</p>
 */
@Configuration
@Profile({"okx-private-readonly-diagnostics", "gatew-okx-readonly", "gatew-okx-readonly-soak"})
@Conditional(
        OkxPrivateReadOnlyDiagnosticsConfiguration.OkxPrivateReadOnlyDiagnosticsEnabledCondition.class
)
@ConditionalOnProperty(
        prefix = "nq.env-safety",
        name = {"ci", "live-enabled", "real-exchange-enabled", "real-client-enabled", "real-provider-enabled"},
        havingValue = "false",
        matchIfMissing = false
)
@ConditionalOnProperty(
        prefix = "nq.env-safety",
        name = "no-outbound",
        havingValue = "false",
        matchIfMissing = false
)
public class OkxPrivateReadOnlyDiagnosticsConfiguration {

    static final String STABLE_PREFIX = "nq.okx.private-readonly-diagnostics";
    static final String LEGACY_PREFIX = "nq.gatew.okx-private-readonly";

    @Bean
    public OkxPrivateReadTransport okxPrivateReadOnlyTransport(ObjectMapper objectMapper) {
        return new JdkOkxPrivateReadTransport(objectMapper, Clock.systemUTC());
    }

    @Bean
    public OkxPrivateCredentialExecutor okxPrivateReadOnlyCredentialExecutor(
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
    public OkxPrivateReadonlyProbeService okxPrivateReadOnlyProbeService(
            ExchangeAccountRepository exchangeAccountRepository,
            OkxPrivateCredentialExecutor credentialExecutor,
            KillSwitchService killSwitchService
    ) {
        return new OkxPrivateReadonlyProbeService(
                exchangeAccountRepository,
                credentialExecutor,
                killSwitchService,
                Clock.systemUTC()
        );
    }

    static final class OkxPrivateReadOnlyDiagnosticsEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return matches(context, "enabled", true)
                    && matches(context, "order-submission-enabled", false)
                    && matches(context, "transfer-enabled", false)
                    && matches(context, "withdraw-enabled", false);
        }

        private static boolean matches(ConditionContext context, String name, boolean required) {
            return CapabilityPropertyResolver.matchesExactBoolean(
                    context.getEnvironment(),
                    STABLE_PREFIX + "." + name,
                    LEGACY_PREFIX + "." + name,
                    required
            );
        }
    }
}
