package com.guidinglight.nexusquant.app.config.livecontrol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.JdkOkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.app.config.account.AccountCredentialRuntimeProperties;
import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.infra.KillSwitchGuardedProviderObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.infra.okx.OkxPilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import java.time.Clock;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 只读 provider observation capability 的唯一显式 composition root。
 *
 * <p>capability 与全部安全开关必须显式配置，缺失即不装配。LIVE/real runtime/mutation flags
 * 必须精确为 false。Bean 构造不读取 credential、不连接 DB、不调用 OKX；真实采集只能经 primary
 * observation authority，且调用期要求 durable kill switch 为 ENGAGED。SpotExecutionProviderPort、
 * worker 与 business scheduler 不在此图中。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "nq.runtime.provider-observation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@ConditionalOnProperty(
        prefix = "nq.runtime.provider-observation",
        name = {"order-submission-enabled", "cancel-enabled", "transfer-enabled", "withdraw-enabled"},
        havingValue = "false",
        matchIfMissing = false
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
public class ReadOnlyProviderObservationConfiguration {

    @Bean
    public ReadOnlyProviderObservationRuntimeIdentity readOnlyProviderObservationRuntimeIdentity(
            @Value("${nq.runtime.provider-observation.release-id}") String releaseId,
            @Value("${nq.runtime.provider-observation.source-commit}") String sourceCommit,
            @Value("${nq.runtime.provider-observation.capability-identity}") String capability,
            @Value("${server.address}") String bindAddress
    ) {
        return new ReadOnlyProviderObservationRuntimeIdentity(
                releaseId, sourceCommit, capability, bindAddress, Runtime.version().feature());
    }

    @Bean
    @ConditionalOnMissingBean(OkxPrivateReadTransport.class)
    public OkxPrivateReadTransport readOnlyProviderObservationTransport(ObjectMapper objectMapper) {
        return new JdkOkxPrivateReadTransport(objectMapper, Clock.systemUTC());
    }

    @Bean
    public OkxPrivateCredentialExecutor readOnlyProviderObservationCredentialExecutor(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AccountCredentialRuntimeProperties properties,
            OkxPrivateReadTransport transport
    ) {
        return new JdbcOkxPrivateCredentialExecutor(
                jdbcTemplate, objectMapper, properties.getMasterKey(), transport);
    }

    @Bean
    @Primary
    public PilotPrerequisiteObservationAuthority readOnlyProviderObservationAuthority(
            OkxPrivateCredentialExecutor credentialExecutor,
            KillSwitchService killSwitchService,
            com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService
                    instrumentCatalogService,
            ReadOnlyProviderObservationRuntimeIdentity runtimeIdentity,
            @Value("${NQ_GATEY_RELEASE_MANIFEST_SHA256}") String releaseManifestSha256
    ) {
        return new KillSwitchGuardedProviderObservationAuthority(
                new OkxPilotPrerequisiteObservationAuthority(
                        credentialExecutor, instrumentCatalogService,
                        runtimeIdentity.releaseId(), releaseManifestSha256),
                killSwitchService
        );
    }

    /**
     * Deployment health 只读取既有 runtime identity、kill snapshot 与 Bean 装配事实，不产生授权或外联。
     */
    @Bean
    public ReadOnlyRuntimeDiagnosticEndpoint readOnlyRuntimeDiagnosticEndpoint(
            ReadOnlyProviderObservationRuntimeIdentity identity,
            KillSwitchService killSwitchService,
            Environment environment,
            ListableBeanFactory beanFactory
    ) {
        BooleanSupplier mutationRuntimeBound = () ->
                beanFactory.getBeanNamesForType(SpotExecutionProviderPort.class, false, false).length > 0
                        || beanFactory.getBeanNamesForType(TradingAdapter.class, false, false).length > 0;
        return new ReadOnlyRuntimeDiagnosticEndpoint(
                identity,
                killSwitchService,
                environment,
                Clock.systemUTC(),
                mutationRuntimeBound
        );
    }
}
