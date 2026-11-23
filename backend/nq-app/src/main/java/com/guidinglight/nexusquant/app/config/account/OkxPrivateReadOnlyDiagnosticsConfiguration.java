package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxAccountFactsObservationService;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxAccountFeeReadPort;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxCurrentPublicRuleReader;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateReadonlyProbeService;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxAccountFactsReadTransport;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.app.config.StableCapabilityPropertyResolver;
import com.guidinglight.nexusquant.livecontrol.deployment.policy.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * OKX private read-only diagnostics 的显式 composition root。
 *
 * <p>只有受控 read-only profile、feature flag=true，且 CI/no-outbound/LIVE/交易写侧全部显式关闭时装配；
 * Bean 创建不读取 credential、不执行 probe、不访问网络，也不注册 scheduler/runner/mutating adapter。</p>
 */
@Configuration
@Profile({
    "okx-private-readonly-diagnostics & !scoped-okx-private-readonly",
    "scoped-okx-private-readonly & !okx-private-readonly-diagnostics"
})
@Conditional(
        OkxPrivateReadOnlyDiagnosticsConfiguration.OkxPrivateReadOnlyDiagnosticsEnabledCondition
                .class)
@ConditionalOnProperty(
        prefix = "nq.env-safety",
        name = {
            "ci",
            "live-enabled",
            "real-exchange-enabled",
            "real-client-enabled",
            "real-provider-enabled"
        },
        havingValue = "false",
        matchIfMissing = false)
@ConditionalOnProperty(
        prefix = "nq.env-safety",
        name = "no-outbound",
        havingValue = "false",
        matchIfMissing = false)
public class OkxPrivateReadOnlyDiagnosticsConfiguration {

    static final String STABLE_PREFIX = "nq.okx.private-readonly-diagnostics";

    @Bean
    public AccountFeeReadPort accountFeeReadPort(
            OkxPrivateCredentialExecutor executor,
            KillSwitchService killSwitch,
            OkxPrivateReadOnlyPermissionProbeProperties properties,
            Environment environment,
            @Value("${nq.live-control.scoped-credential.maximum-permission-probe-age:PT1H}")
                    String maximumProbeAge) {
        long owner = environment.getProperty("nq.account-fees.okx.owner-user-id", Long.class, 0L);
        long account =
                environment.getProperty("nq.account-fees.okx.exchange-account-id", Long.class, 0L);
        long credential =
                environment.getProperty("nq.account-fees.okx.credential-id", Long.class, 0L);
        String symbols = environment.getProperty("nq.account-fees.okx.allowed-instruments", "");
        if (!properties.enabled()
                || properties.expectedIp() == null
                || owner <= 0
                || account <= 0
                || credential <= 0
                || symbols.isBlank()) return AccountFeeReadPort.disabled();
        var permission =
                environment.acceptsProfiles(Profiles.of("scoped-okx-private-readonly"))
                        ? ScopedCredentialCapabilityPolicy.PermissionScope.TRADE
                        : ScopedCredentialCapabilityPolicy.PermissionScope.READ_ONLY;
        return new OkxAccountFeeReadPort(
                executor,
                killSwitch,
                new ScopedCredentialCapabilityPolicy(parsePositiveDuration(maximumProbeAge)),
                permission,
                Clock.systemUTC(),
                properties.expectedIp(),
                owner,
                account,
                credential,
                Arrays.stream(symbols.split(","))
                        .map(String::trim)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    @Bean
    public OkxAccountFactsReadTransport okxPrivateReadOnlyTransport(ObjectMapper objectMapper) {
        return new OkxAccountFactsReadTransport(objectMapper, Clock.systemUTC());
    }

    @Bean
    public OkxCurrentPublicRuleReader okxCurrentPublicRuleReader() {
        return new OkxCurrentPublicRuleReader(Clock.systemUTC());
    }

    @Bean
    public OkxAccountFactsObservationService okxAccountFactsObservationService(
            ExchangeAccountRepository accountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            OkxPrivateCredentialExecutor executor,
            OkxAccountFactsReadTransport transport,
            KillSwitchService killSwitch,
            OkxCurrentPublicRuleReader publicRuleReader,
            JdbcTemplate jdbcTemplate,
            OkxPrivateReadOnlyPermissionProbeProperties permissionProperties,
            Environment environment,
            @Value("${nq.live-control.scoped-credential.maximum-permission-probe-age:PT1H}")
                    String maximumProbeAge) {
        var permissionScope =
                environment.acceptsProfiles(Profiles.of("scoped-okx-private-readonly"))
                        ? ScopedCredentialCapabilityPolicy.PermissionScope.TRADE
                        : ScopedCredentialCapabilityPolicy.PermissionScope.READ_ONLY;
        return new OkxAccountFactsObservationService(
                accountRepository,
                credentialRepository,
                executor,
                transport,
                killSwitch,
                new ScopedCredentialCapabilityPolicy(parsePositiveDuration(maximumProbeAge)),
                permissionScope,
                publicRuleReader,
                jdbcTemplate,
                Clock.systemUTC(),
                permissionProperties.expectedIp());
    }

    @Bean
    public OkxPrivateCredentialExecutor okxPrivateReadOnlyCredentialExecutor(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AccountCredentialRuntimeProperties properties,
            OkxPrivateReadTransport transport) {
        return new JdbcOkxPrivateCredentialExecutor(
                jdbcTemplate, objectMapper, properties.getMasterKey(), transport);
    }

    @Bean
    public OkxPrivateReadonlyProbeService okxPrivateReadOnlyProbeService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            OkxPrivateCredentialExecutor credentialExecutor,
            KillSwitchService killSwitchService,
            @Value("${nq.live-control.scoped-credential.maximum-permission-probe-age:PT1H}")
                    String maximumProbeAge) {
        return new OkxPrivateReadonlyProbeService(
                exchangeAccountRepository,
                credentialExecutor,
                killSwitchService,
                Clock.systemUTC(),
                credentialRepository,
                new ScopedCredentialCapabilityPolicy(parsePositiveDuration(maximumProbeAge)));
    }

    private static Duration parsePositiveDuration(String value) {
        Duration parsed = Duration.parse(value);
        if (parsed.isZero() || parsed.isNegative()) {
            throw new IllegalArgumentException("permission probe age must be positive");
        }
        return parsed;
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
            return StableCapabilityPropertyResolver.matchesExactBoolean(
                    context.getEnvironment(), STABLE_PREFIX + "." + name, required);
        }
    }
}
