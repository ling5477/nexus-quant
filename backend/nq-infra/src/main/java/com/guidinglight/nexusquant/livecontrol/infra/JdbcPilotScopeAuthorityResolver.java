package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeAuthorityResolver;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionState;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionStateRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

/**
 * GateY-6D formal SoR resolver。数据库只读取脱敏 reference facts；runtime identity 来自 server-owned exact 配置。
 */
@Repository
public class JdbcPilotScopeAuthorityResolver implements PilotScopeAuthorityResolver {

    private static final String PREFIX = "nq.live-control.pilot-materialization.";

    private final ExchangeAccountRepository accountRepository;
    private final ExchangeAccountCredentialRepository credentialRepository;
    private final StrategyReleaseAdmissionStateRepository admissionRepository;
    private final LiveControlRepository liveControlRepository;
    private final Environment environment;

    public JdbcPilotScopeAuthorityResolver(
            ExchangeAccountRepository accountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            StrategyReleaseAdmissionStateRepository admissionRepository,
            LiveControlRepository liveControlRepository,
            Environment environment
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.credentialRepository = Objects.requireNonNull(credentialRepository);
        this.admissionRepository = Objects.requireNonNull(admissionRepository);
        this.liveControlRepository = Objects.requireNonNull(liveControlRepository);
        this.environment = Objects.requireNonNull(environment);
    }

    @Override
    public ResolvedAuthority resolve(
            AuthenticatedLiveControlActor actor,
            PilotScopeMaterializationCommand command
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        rejectDriftingReference(command.strategyReleaseId());

        ExchangeAccountSummary account = accountRepository.findByIdForOwner(
                        actor.userId(), command.exchangeAccountId())
                .orElseThrow(() -> denied("PILOT_ACCOUNT_REFERENCE_MISMATCH"));
        if (!"OKX".equals(account.exchangeCode()) || !"LIVE".equals(account.tradeEnv())
                || !"ACTIVE".equals(account.status())) {
            throw denied("PILOT_ACCOUNT_REFERENCE_MISMATCH");
        }

        ExchangeAccountCredentialSummary credential = credentialRepository.findByCredentialIdForOwner(
                        actor.userId(), command.exchangeAccountId(), command.credentialReference())
                .orElseThrow(() -> denied("PILOT_CREDENTIAL_REFERENCE_MISMATCH"));
        if (!Objects.equals(credential.credentialId(), command.credentialReference())
                || !Objects.equals(credential.exchangeAccountId(), command.exchangeAccountId())
                || !"OKX_API_V5".equals(credential.credentialType())
                || !"ACTIVE".equals(credential.credentialStatus())
                || !credential.isActive()
                || !"VERIFIED".equals(credential.verificationStatus())
                || !"SUCCEEDED".equals(credential.permissionProbeStatus())
                || !"TRADE".equals(credential.permissionScope())
                || credential.withdrawEnabled()
                || !"PASSED".equals(credential.ipAllowlistProbeStatus())
                || credential.lastPermissionProbeAt() == null
                || credential.revokedAt() != null
                || credential.rotatedAt() != null) {
            throw denied("PILOT_CREDENTIAL_REFERENCE_MISMATCH");
        }

        StrategyReleaseAdmissionState admission = admissionRepository.loadByPublishRecordId(
                command.strategyReleaseId());
        if (!admission.identityBound()
                || admission.admissionRevision() != command.releaseAdmissionRevision()
                || !same(admission.releaseArtifactDigest(), command.releaseDigest())) {
            throw denied("PILOT_RELEASE_REFERENCE_MISMATCH");
        }

        RiskLimitSet risk = liveControlRepository.findRiskLimitSet(command.risk().riskLimitSetId())
                .orElseThrow(() -> denied("PILOT_RISK_REFERENCE_MISMATCH"));
        if (!same(risk.canonicalDigest(), command.risk().riskLimitSetDigest())) {
            throw denied("PILOT_RISK_REFERENCE_MISMATCH");
        }
        return new ResolvedAuthority(risk, resolveRuntimeAuthority());
    }

    private ResolvedScopeBindings resolveRuntimeAuthority() {
        try {
            return new ResolvedScopeBindings(
                    requiredText("instrument-metadata-digest"),
                    requiredText("instrument-source-identity"),
                    requiredText("instrument-source-schema-version"),
                    requiredLong("instrument-maximum-age-ms"),
                    requiredText("fee-schedule-digest"),
                    requiredText("fee-tier"),
                    PilotScopeBinding.FeeEvidenceClass.valueOf(requiredText("fee-evidence-class")),
                    requiredText("fee-source-identity"),
                    requiredText("fee-source-schema-version"),
                    requiredLong("fee-maximum-age-ms"),
                    requiredText("balance-source-identity"),
                    requiredText("balance-source-schema-version"),
                    requiredLong("balance-maximum-age-ms"),
                    requiredText("clock-source-identity"),
                    requiredText("clock-source-schema-version"),
                    requiredLong("clock-maximum-age-ms"),
                    requiredText("signed-timestamp-source"),
                    requiredLong("maximum-tolerated-skew-ms"),
                    requiredText("endpoint-policy-version"),
                    requiredText("endpoint-policy-digest"),
                    requiredText("provider-contract-identity"),
                    requiredText("provider-artifact-digest"),
                    requiredText("worker-identity"),
                    requiredText("worker-release-digest")
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new LiveControlException(
                    "PILOT_RUNTIME_AUTHORITY_NOT_CONFIGURED",
                    "exact server-owned pilot runtime authority is not configured"
            );
        }
    }

    private String requiredText(String key) {
        String stored = environment.getProperty(PREFIX + key);
        if (stored == null || stored.isBlank()) {
            throw new IllegalStateException("missing runtime authority");
        }
        String value = stored.trim();
        if ("latest".equalsIgnoreCase(value)
                || "current".equalsIgnoreCase(value)
                || "HEAD".equalsIgnoreCase(value)) {
            throw new IllegalStateException("drifting runtime authority");
        }
        return value;
    }

    private long requiredLong(String key) {
        try {
            return Long.parseLong(requiredText(key));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("invalid runtime authority", exception);
        }
    }

    private static void rejectDriftingReference(String value) {
        if (value == null || value.isBlank()
                || "latest".equalsIgnoreCase(value.trim())
                || "current".equalsIgnoreCase(value.trim())
                || "HEAD".equalsIgnoreCase(value.trim())) {
            throw denied("PILOT_DRIFTING_REFERENCE_FORBIDDEN");
        }
    }

    private static boolean same(String stored, String supplied) {
        return stored != null && supplied != null && MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static LiveControlException denied(String code) {
        return new LiveControlException(code, "exact pilot authority resolution failed");
    }
}
