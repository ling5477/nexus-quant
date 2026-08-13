package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentAdmissionService;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerOperationSafetyGate;
import com.guidinglight.nexusquant.livecontrol.deployment.KillSwitchPropagationPolicy;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.livecontrol.deployment.infra.okx.OkxPrivateReadonlyEndpointPolicyEvidenceFactory;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotEndpointGuard;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.TrustedRootStrategyArtifactVerifier;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.VerifiedOpenStrategyArtifactReader;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Future worker deployment boundary 的非默认 composition root。
 *
 * <p>Bean 创建只装配纯 policy/verifier，不读取 credential、不打开 artifact、不访问网络、不 claim/send、
 * 不启动进程。LIVE 必须显式为 false；actual start 仍需运行时 evidence 通过 Java admission。</p>
 */
@Configuration
@Profile("worker-deployment-admission")
@ConditionalOnProperty(
        prefix = "nq.live-control.worker-deployment-admission",
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
public class WorkerDeploymentBoundaryConfiguration {

    @Bean
    public KillSwitchPropagationPolicy workerKillSwitchPropagationPolicy(
            @Value("${nq.live-control.worker-deployment-admission.kill-maximum-age:PT30S}") String maximumAge
    ) {
        return new KillSwitchPropagationPolicy(parsePositiveDuration(maximumAge));
    }

    @Bean
    public ScopedCredentialCapabilityPolicy scopedCredentialCapabilityPolicy(
            @Value("${nq.live-control.worker-deployment-admission.permission-probe-maximum-age:PT1H}") String maximumAge
    ) {
        return new ScopedCredentialCapabilityPolicy(parsePositiveDuration(maximumAge));
    }

    @Bean
    public VerifiedOpenStrategyArtifactReader verifiedOpenStrategyArtifactReader(
            TrustedRootStrategyArtifactVerifier verifier,
            @Value("${nq.live-control.worker-deployment-admission.max-immutable-snapshot-bytes:67108864}")
            String maxSnapshotBytes
    ) {
        return new VerifiedOpenStrategyArtifactReader(verifier, parsePositiveLong(maxSnapshotBytes));
    }

    @Bean
    public OkxPrivateReadonlyEndpointPolicyEvidenceFactory okxPrivateReadonlyEndpointPolicyEvidenceFactory() {
        return new OkxPrivateReadonlyEndpointPolicyEvidenceFactory(new OkxSpotEndpointGuard());
    }

    @Bean
    public WorkerOperationSafetyGate workerOperationSafetyGate(
            KillSwitchService killSwitchService,
            KillSwitchPropagationPolicy propagationPolicy
    ) {
        return new WorkerOperationSafetyGate(killSwitchService, propagationPolicy, Clock.systemUTC());
    }

    @Bean
    public WorkerDeploymentAdmissionService workerDeploymentAdmissionService(
            KillSwitchService killSwitchService,
            KillSwitchPropagationPolicy propagationPolicy,
            ScopedCredentialCapabilityPolicy credentialPolicy
    ) {
        return new WorkerDeploymentAdmissionService(
                killSwitchService, propagationPolicy, credentialPolicy, Clock.systemUTC());
    }

    private static Duration parsePositiveDuration(String value) {
        Duration parsed = Duration.parse(value);
        if (parsed.isZero() || parsed.isNegative()) {
            throw new IllegalArgumentException("worker deployment duration must be positive");
        }
        return parsed;
    }

    private static long parsePositiveLong(String value) {
        long parsed = Long.parseLong(value);
        if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("immutable snapshot limit is invalid");
        }
        return parsed;
    }
}
