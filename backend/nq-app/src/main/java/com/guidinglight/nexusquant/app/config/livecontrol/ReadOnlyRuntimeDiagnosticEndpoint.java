package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.core.env.Environment;

/**
 * GateY qualification runtime 的只读诊断端点，复用唯一 runtime identity 与 durable kill snapshot。
 *
 * <p>该端点只用于 loopback deployment health verification，不产生交易授权，不访问 credential 或
 * provider。无法从 production runtime 可靠观测的计数必须返回 {@code NOT_INSTRUMENTED}，不得伪造为零。</p>
 */
@Endpoint(id = "readonlyproviderobservation")
public final class ReadOnlyRuntimeDiagnosticEndpoint {

    private static final DiagnosticCounter NOT_INSTRUMENTED =
            new DiagnosticCounter("NOT_INSTRUMENTED", null);

    private final ReadOnlyProviderObservationRuntimeIdentity identity;
    private final KillSwitchService killSwitchService;
    private final Environment environment;
    private final Clock clock;
    private final BooleanSupplier mutationRuntimeBound;
    private final Instant startedAt;

    public ReadOnlyRuntimeDiagnosticEndpoint(
            ReadOnlyProviderObservationRuntimeIdentity identity,
            KillSwitchService killSwitchService,
            Environment environment,
            Clock clock,
            BooleanSupplier mutationRuntimeBound
    ) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.killSwitchService = Objects.requireNonNull(killSwitchService, "killSwitchService must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.mutationRuntimeBound = Objects.requireNonNull(
                mutationRuntimeBound,
                "mutationRuntimeBound must not be null"
        );
        this.startedAt = clock.instant();
    }

    /**
     * @return 脱敏、无授权能力的当前 runtime 诊断事实
     */
    @ReadOperation(produces = "application/json")
    public RuntimeDiagnostic read() {
        KillSwitchSnapshot kill = killSwitchService.snapshot();
        Boolean tradingComponents = environment.getProperty(
                "nq.runtime.trading-components.enabled",
                Boolean.class
        );
        Boolean live = environment.getProperty("nq.env-safety.live-enabled", Boolean.class);
        String deploymentProfile = environment.getProperty(
                "nq.runtime.provider-observation.deployment-profile",
                "UNKNOWN"
        );
        return new RuntimeDiagnostic(
                identity.sourceCommit(),
                identity.releaseId(),
                identity.javaMajor(),
                deploymentProfile,
                identity.capability(),
                identity.bindAddress(),
                true,
                tradingComponents == null || tradingComponents,
                live == null || live,
                kill.status().name(),
                kill.version(),
                kill.updatedAt(),
                kill.observedAt(),
                mutationRuntimeBound.getAsBoolean(),
                NOT_INSTRUMENTED,
                NOT_INSTRUMENTED,
                NOT_INSTRUMENTED,
                NOT_INSTRUMENTED,
                NOT_INSTRUMENTED,
                NOT_INSTRUMENTED,
                NOT_INSTRUMENTED,
                NOT_INSTRUMENTED,
                NOT_INSTRUMENTED,
                startedAt,
                clock.instant(),
                true,
                false,
                true
        );
    }

    /**
     * 单个计数器的可观测语义；未建立可靠 instrumentation 时 value 必须为空。
     */
    public record DiagnosticCounter(String status, Long value) {
        public DiagnosticCounter {
            if (!"OBSERVED".equals(status) && !"NOT_INSTRUMENTED".equals(status)) {
                throw new IllegalArgumentException("counter status is invalid");
            }
            if (("OBSERVED".equals(status) && value == null)
                    || ("NOT_INSTRUMENTED".equals(status) && value != null)
                    || (value != null && value < 0)) {
                throw new IllegalArgumentException("counter value is inconsistent with status");
            }
        }
    }

    /**
     * Deployment verifier 消费的稳定只读响应；不包含 env、JDBC、credential 或 provider payload。
     */
    public record RuntimeDiagnostic(
            String sourceCommit,
            String releaseId,
            int javaMajor,
            String qualificationProfile,
            String capabilityIdentity,
            String bindAddress,
            boolean providerObservationEnabled,
            boolean tradingComponentsEnabled,
            boolean liveEnabled,
            String killSwitch,
            long killSwitchVersion,
            Instant killSwitchUpdatedAt,
            Instant killSwitchObservedAt,
            boolean mutationRuntimeBound,
            DiagnosticCounter credentialMetadataReads,
            DiagnosticCounter credentialMaterialReads,
            DiagnosticCounter decryptCount,
            DiagnosticCounter okxGetCount,
            DiagnosticCounter okxPostCount,
            DiagnosticCounter executionIntentDelta,
            DiagnosticCounter executionReceiptDelta,
            DiagnosticCounter orderDelta,
            DiagnosticCounter ledgerDelta,
            Instant startedAt,
            Instant generatedAt,
            boolean diagnosticOnly,
            boolean tradingAuthorization,
            boolean noSideEffect
    ) {
    }
}
