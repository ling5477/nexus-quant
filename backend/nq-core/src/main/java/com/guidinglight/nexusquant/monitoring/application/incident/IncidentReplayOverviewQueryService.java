package com.guidinglight.nexusquant.monitoring.application.incident;

import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplayOverviewReadModel.BoundaryMessage;
import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplayOverviewReadModel.EvidenceAnchor;
import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplayOverviewReadModel.LatestEvidence;
import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplayOverviewReadModel.NextStep;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewFacts;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewFacts.LatestEvidenceFact;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewQueryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IncidentReplayOverviewQueryService 组装 GateS-6 Incident / Replay overview read model。
 *
 * <p>职责：只读聚合本地 Shadow / Paper / Replay evidence，输出 incident-like diagnostic
 * summary。该 service 不创建 incident、不创建 alert、不追加 event、不生成 replay、不调用 runner /
 * scheduler / adapter，不读取 credential，也不修改 account/order/ledger 状态。
 */
@Service
public class IncidentReplayOverviewQueryService {

    private static final Pattern SENSITIVE_TEXT_PATTERN = Pattern.compile(
            "api[_-]?key|secret|passphrase|token|private[_ -]?key|credentialMaterial|"
                    + "decrypted[_-]?payload|encrypted[_-]?payload|private endpoint|"
                    + "realOrderId|realAccountBalance|authorizedForTrading|tradingReady|"
                    + "liveReady|tradeApproved|ready\\s+to\\s+trade|live\\s+ready|"
                    + "trade[\\s_-]+approved|can\\s+trade|placeOrder|cancelOrder|withdraw|transfer",
            Pattern.CASE_INSENSITIVE
    );

    private final IncidentReplayOverviewQueryPort queryPort;
    private final Clock clock;

    /** 生产构造器。 */
    @Autowired
    public IncidentReplayOverviewQueryService(IncidentReplayOverviewQueryPort queryPort) {
        this(queryPort, Clock.systemUTC());
    }

    IncidentReplayOverviewQueryService(IncidentReplayOverviewQueryPort queryPort, Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 Incident / Replay overview。
     *
     * <p>事务：read-only。副作用：无。空事实表返回 severity=NONE 的稳定概览，不抛 500。返回值固定
     * notTradingAuthorization=true，不能解释为交易授权。
     *
     * @param traceId 当前请求 trace id
     * @return GateS-6 read model
     */
    @Transactional(readOnly = true)
    public IncidentReplayOverviewReadModel overview(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        IncidentReplayOverviewFacts facts = queryPort.loadOverviewFacts();
        IncidentReplaySeverity severity = incidentSeverity(facts);
        List<LatestEvidence> latestEvidence = facts.latestEvidence().stream()
                .limit(8)
                .map(this::latestEvidence)
                .toList();

        return new IncidentReplayOverviewReadModel(
                Instant.now(clock),
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                facts.totalEvidenceItems(),
                facts.shadowEventCount(),
                facts.consistencyDivergenceCount(),
                facts.paperAlertCount(),
                facts.recoveryEventCount(),
                facts.replayEventCount(),
                latestEvidence,
                severity,
                blockers(),
                warnings(facts),
                nextSteps(facts, severity),
                evidenceAnchors(latestEvidence),
                traceId
        );
    }

    private IncidentReplaySeverity incidentSeverity(IncidentReplayOverviewFacts facts) {
        if (facts.totalEvidenceItems() == 0) {
            return IncidentReplaySeverity.NONE;
        }
        if (facts.criticalPaperAlertCount() > 0) {
            return IncidentReplaySeverity.CRITICAL;
        }
        if (facts.highPaperAlertCount() > 0 || facts.consistencyDivergenceCount() > 0) {
            return IncidentReplaySeverity.HIGH;
        }
        if (facts.paperAlertCount() > 0 || facts.recoveryEventCount() > 0) {
            return IncidentReplaySeverity.WARNING;
        }
        if (facts.shadowEventCount() > 0 || facts.replayEventCount() > 0) {
            return IncidentReplaySeverity.INFO;
        }
        return IncidentReplaySeverity.UNKNOWN;
    }

    private LatestEvidence latestEvidence(LatestEvidenceFact fact) {
        return new LatestEvidence(
                safeText(fact.evidenceType()),
                safeText(fact.sourceId()),
                safeText(fact.sourceStatus()),
                safeText(fact.summary()),
                fact.occurredAt(),
                safeText(fact.traceId())
        );
    }

    private List<BoundaryMessage> blockers() {
        return List.of(
                message(
                        "LIVE_DISABLED",
                        "CRITICAL",
                        "LIVE is disabled; incident replay overview is diagnostic only.",
                        "SYSTEM_BOUNDARY",
                        null
                ),
                message(
                        "REAL_PROVIDER_NOT_IMPLEMENTED",
                        "CRITICAL",
                        "Real provider is not implemented.",
                        "SYSTEM_BOUNDARY",
                        null
                ),
                message(
                        "PRIVATE_TRADING_NOT_IMPLEMENTED",
                        "CRITICAL",
                        "Private trading adapter is not implemented.",
                        "SYSTEM_BOUNDARY",
                        null
                ),
                message(
                        "NOT_TRADING_AUTHORIZATION",
                        "CRITICAL",
                        "Incident replay overview is not trading authorization.",
                        "SYSTEM_BOUNDARY",
                        null
                )
        );
    }

    private List<BoundaryMessage> warnings(IncidentReplayOverviewFacts facts) {
        List<BoundaryMessage> warnings = new ArrayList<>();
        warnings.add(message(
                "INCIDENT_REPLAY_DIAGNOSTIC_ONLY",
                "WARNING",
                "Incident severity is diagnostic priority only and does not approve trading.",
                "INCIDENT_REPLAY",
                null
        ));
        warnings.add(message(
                "SOURCE_NOT_AVAILABLE",
                "INFO",
                "Dedicated incident creation table is not implemented; overview is composed from local facts.",
                "INCIDENT_SYSTEM",
                null
        ));
        warnings.add(message(
                "SOURCE_NOT_AVAILABLE",
                "INFO",
                "Runtime readiness is represented by fixed boundary flags; no runtime incident fact table is read.",
                "RUNTIME_READINESS",
                null
        ));
        if (facts.totalEvidenceItems() == 0) {
            warnings.add(message(
                    "NO_LOCAL_EVIDENCE",
                    "INFO",
                    "No Shadow, Paper alert, recovery or replay facts are currently available.",
                    "LOCAL_FACTS",
                    null
            ));
        }
        return warnings;
    }

    private List<NextStep> nextSteps(IncidentReplayOverviewFacts facts, IncidentReplaySeverity severity) {
        List<NextStep> steps = new ArrayList<>();
        steps.add(new NextStep(
                "REVIEW_INCIDENT_REPLAY_BOUNDARY",
                "backend",
                "Review diagnostic-only and no-side-effect incident replay boundary",
                "Boundary remains acknowledged before any future monitoring or replay action",
                true
        ));
        steps.add(new NextStep(
                "KEEP_READ_MODEL_GET_ONLY",
                "backend",
                "Keep this endpoint GET-only and backed by SELECT-only local fact queries",
                "No POST/PUT/PATCH/DELETE endpoint, runner trigger, scheduler trigger or adapter call is added",
                true
        ));
        if (facts.consistencyDivergenceCount() > 0 || severity == IncidentReplaySeverity.HIGH
                || severity == IncidentReplaySeverity.CRITICAL) {
            steps.add(new NextStep(
                    "REVIEW_DIVERGENCE_OR_ALERT_EVIDENCE",
                    "operator",
                    "Review divergence or alert evidence in existing read-only views",
                    "Incident-like priority is reviewed without creating incident, replay or trade action",
                    false
            ));
        }
        steps.add(new NextStep(
                "IMPLEMENT_DEDICATED_INCIDENT_SOURCE_LATER",
                "backend",
                "Add a dedicated incident fact source only in a separate migration-approved task",
                "Future source is reviewed separately and does not alter this read-only baseline",
                false
        ));
        return steps;
    }

    private List<EvidenceAnchor> evidenceAnchors(List<LatestEvidence> latestEvidence) {
        if (latestEvidence.isEmpty()) {
            return List.of(new EvidenceAnchor("INCIDENT_REPLAY", null, "NO_EVIDENCE", Instant.now(clock), null));
        }
        return latestEvidence.stream()
                .map(item -> new EvidenceAnchor(
                        item.evidenceType(),
                        item.sourceId(),
                        item.sourceStatus(),
                        item.occurredAt(),
                        null
                ))
                .toList();
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return SENSITIVE_TEXT_PATTERN.matcher(normalized).find() ? "[filtered diagnostic text]" : normalized;
    }

    private BoundaryMessage message(String code, String severity, String message, String sourceType, String sourceId) {
        return new BoundaryMessage(code, severity, message, sourceType, sourceId);
    }
}
