package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.CredentialPermissionProbeService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialVerifier;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeCredentialPermissionProbePort;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.probe.NoRealExchangeCredentialPermissionProbePort;
import com.guidinglight.nexusquant.account.infra.probe.OkxRealReadonlyPermissionProbePort;
import com.guidinglight.nexusquant.account.infra.verification.StructuralExchangeAccountCredentialVerifier;
import com.guidinglight.nexusquant.app.config.CapabilityPropertyResolver;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * AccountModuleConfiguration 负责 exchange account 域内 Bean 装配。
 */
@Configuration
@EnableConfigurationProperties(AccountCredentialRuntimeProperties.class)
public class AccountModuleConfiguration {

    private static final String STABLE_READ_ONLY_PREFIX = "nq.okx.private-readonly-diagnostics";
    private static final String LEGACY_READ_ONLY_PREFIX = "nq.gatew.okx-private-readonly";

    @Bean
    public ExchangeAccountRepository exchangeAccountRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcExchangeAccountRepository(jdbcTemplate);
    }

    @Bean
    public ExchangeAccountQueryService exchangeAccountQueryService(ExchangeAccountRepository exchangeAccountRepository) {
        return new ExchangeAccountQueryService(exchangeAccountRepository);
    }

    @Bean
    public ExchangeAccountCommandService exchangeAccountCommandService(ExchangeAccountRepository exchangeAccountRepository) {
        return new ExchangeAccountCommandService(exchangeAccountRepository);
    }

    @Bean
    public ExchangeAccountCredentialRepository exchangeAccountCredentialRepository(
            JdbcTemplate jdbcTemplate,
            AccountCredentialRuntimeProperties properties
    ) {
        return new JdbcExchangeAccountCredentialRepository(jdbcTemplate, properties.getMasterKey());
    }

    @Bean
    public ExchangeAccountCredentialVerifier exchangeAccountCredentialVerifier(
            ObjectMapper objectMapper,
            AccountCredentialRuntimeProperties properties
    ) {
        return new StructuralExchangeAccountCredentialVerifier(objectMapper, properties.getVerificationMode());
    }

    @Bean
    public ExchangeAccountCredentialCommandService exchangeAccountCredentialCommandService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ExchangeAccountCredentialVerifier exchangeAccountCredentialVerifier,
            ObjectMapper objectMapper
    ) {
        return new ExchangeAccountCredentialCommandService(
                exchangeAccountRepository,
                exchangeAccountCredentialRepository,
                exchangeAccountCredentialVerifier,
                objectMapper
        );
    }

    @Bean
    public ExchangeAccountCredentialVerificationService exchangeAccountCredentialVerificationService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ExchangeAccountCredentialVerifier exchangeAccountCredentialVerifier
    ) {
        return new ExchangeAccountCredentialVerificationService(
                exchangeAccountRepository,
                exchangeAccountCredentialRepository,
                exchangeAccountCredentialVerifier
        );
    }

    @Bean
    public ExchangeCredentialPermissionProbePort exchangeCredentialPermissionProbePort(
            ObjectProvider<OkxPrivateCredentialExecutor> credentialExecutorProvider,
            OkxPrivateReadOnlyPermissionProbeProperties permissionProperties,
            Environment environment
    ) {
        OkxPrivateCredentialExecutor executor = credentialExecutorProvider.getIfAvailable();
        if (!permissionProperties.enabled()
                || !(executor instanceof JdbcOkxPrivateCredentialExecutor)
                || !environment.acceptsProfiles(Profiles.of(
                "okx-private-readonly-diagnostics",
                "gatew-okx-readonly-soak"
        ))
                || !exactBoolean(environment, "nq.env-safety.ci", false)
                || !exactBoolean(environment, "nq.env-safety.real-exchange-enabled", false)
                || !exactBoolean(environment, "nq.env-safety.live-enabled", false)
                || !exactBoolean(environment, "nq.env-safety.real-client-enabled", false)
                || !exactBoolean(environment, "nq.env-safety.real-provider-enabled", false)
                || !exactBoolean(environment, "nq.env-safety.no-outbound", false)
                || !exactReadOnlyBoolean(environment, "order-submission-enabled", false)
                || !exactReadOnlyBoolean(environment, "transfer-enabled", false)
                || !exactReadOnlyBoolean(environment, "withdraw-enabled", false)) {
            return new NoRealExchangeCredentialPermissionProbePort();
        }
        try {
            return new OkxRealReadonlyPermissionProbePort(
                    executor,
                    permissionProperties.expectedIp(),
                    Clock.systemUTC()
            );
        } catch (IllegalArgumentException ex) {
            // expectedIp 缺失/非法时回落 NoReal；错误信息不得回显配置原值。
            return new NoRealExchangeCredentialPermissionProbePort();
        }
    }

    @Bean
    public OkxPrivateReadOnlyPermissionProbeProperties okxPrivateReadOnlyPermissionProbeProperties(
            Environment environment
    ) {
        String stablePrefix = STABLE_READ_ONLY_PREFIX + ".permission-probe";
        String legacyPrefix = LEGACY_READ_ONLY_PREFIX + ".permission-probe";
        boolean enabled = CapabilityPropertyResolver.matchesExactBoolean(
                environment,
                stablePrefix + ".enabled",
                legacyPrefix + ".enabled",
                true
        );
        String expectedIp = CapabilityPropertyResolver.failClosed(
                environment,
                stablePrefix + ".expected-ip",
                legacyPrefix + ".expected-ip",
                null
        );
        return new OkxPrivateReadOnlyPermissionProbeProperties(enabled, expectedIp);
    }

    @Bean
    public CredentialPermissionProbeService credentialPermissionProbeService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ExchangeCredentialPermissionProbePort exchangeCredentialPermissionProbePort,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        return new CredentialPermissionProbeService(
                exchangeAccountRepository,
                exchangeAccountCredentialRepository,
                exchangeCredentialPermissionProbePort,
                objectMapper,
                new TransactionTemplate(transactionManager)
        );
    }

    private static boolean exactBoolean(Environment environment, String name, boolean required) {
        String value = environment.getProperty(name);
        return value != null && Boolean.toString(required).equalsIgnoreCase(value.trim());
    }

    private static boolean exactReadOnlyBoolean(Environment environment, String name, boolean required) {
        return CapabilityPropertyResolver.matchesExactBoolean(
                environment,
                STABLE_READ_ONLY_PREFIX + "." + name,
                LEGACY_READ_ONLY_PREFIX + "." + name,
                required
        );
    }
}
