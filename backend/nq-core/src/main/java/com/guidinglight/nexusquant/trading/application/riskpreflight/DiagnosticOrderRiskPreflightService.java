package com.guidinglight.nexusquant.trading.application.riskpreflight;

import com.guidinglight.nexusquant.trading.application.orderpreview.DryRunOrderPreviewResult;
import com.guidinglight.nexusquant.trading.application.orderpreview.OrderPreviewStatus;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationResult;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationTaxonomy;
import com.guidinglight.nexusquant.trading.application.riskpreflight.RiskPreflightFactBundle.CredentialMetadataSummary;
import com.guidinglight.nexusquant.trading.application.riskpreflight.RiskPreflightFactBundle.LocalAccountMetadataSnapshot;
import com.guidinglight.nexusquant.trading.application.riskpreflight.RiskPreflightFactBundle.MarketdataQualitySnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * GateW-3 side-effect-free risk preflight evaluator。
 *
 * <p>职责仅为组合调用方提供的 immutable preview、reconciliation 与 local metadata snapshots。
 * 本类不是 Spring bean，只有 injected Clock 依赖；没有 repository、HTTP、credential、risk registry、
 * order、ledger、audit 或 event port，也不会构造 PlaceOrderCommand。实例无共享可变状态且线程安全。</p>
 */
public final class DiagnosticOrderRiskPreflightService {

    private static final String OKX = "OKX";
    private static final String SPOT = "SPOT";

    private final Clock clock;

    public DiagnosticOrderRiskPreflightService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成一次 pure diagnostic result。
     *
     * <p>相同 request 与同一 Clock snapshot 返回相同结果；方法不读取系统默认时间、不执行 IO、
     * 不消费 stateful risk state，也没有事务或写侧副作用。未来 evaluationTime fail-closed。</p>
     *
     * @param request internal immutable request
     * @return execution 永久 blocked 的 immutable result
     * @throws IllegalArgumentException evaluationTime 晚于 injected Clock 时抛出稳定、无敏感信息的异常
     */
    public DiagnosticOrderRiskPreflightResult evaluate(DiagnosticOrderRiskPreflightRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant clockNow = clock.instant();
        if (request.evaluationTime().isAfter(clockNow)) {
            throw new IllegalArgumentException("evaluationTime must not be in the future");
        }

        Evaluation evaluation = Evaluation.create(request.evaluationTime());
        evaluatePreview(request.orderPreviewResult(), evaluation);
        evaluateReconciliation(request.reconciliationResult(), request.evaluationTime(), evaluation);
        evaluateLocalAccount(
                request.facts().localAccountMetadata(),
                request.diagnosticEnvironment(),
                evaluation
        );
        evaluateCredentialMetadata(request.facts().credentialMetadata(), evaluation);
        evaluateMarketdataQuality(request.facts().marketdataQuality(), evaluation);
        return evaluation.result();
    }

    private static void evaluatePreview(DryRunOrderPreviewResult preview, Evaluation evaluation) {
        if (preview == null) {
            evaluation.structuralStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
            evaluation.venueFactStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
            evaluation.pureRiskStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.ORDER_PREVIEW_NOT_EVALUATED);
            return;
        }
        evaluation.structuralStatus = map(preview.structuralStatus());
        evaluation.venueFactStatus = map(preview.venueFactStatus());
        boolean unsafeContract = !preview.diagnosticOnly()
                || !preview.noSideEffect()
                || preview.orderSubmitted()
                || preview.executionReadiness() != OrderPreviewStatus.BLOCKED;
        if (unsafeContract
                || preview.structuralStatus() == OrderPreviewStatus.BLOCKED
                || preview.venueFactStatus() == OrderPreviewStatus.BLOCKED) {
            evaluation.pureRiskStatus = DiagnosticOrderRiskPreflightStatus.BLOCKED;
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.ORDER_PREVIEW_BLOCKED);
            return;
        }
        if (preview.structuralStatus() == OrderPreviewStatus.NOT_EVALUATED
                || preview.venueFactStatus() == OrderPreviewStatus.NOT_EVALUATED) {
            evaluation.pureRiskStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.ORDER_PREVIEW_NOT_EVALUATED);
            return;
        }
        if (preview.structuralStatus() == OrderPreviewStatus.UNKNOWN
                || preview.venueFactStatus() == OrderPreviewStatus.UNKNOWN) {
            evaluation.pureRiskStatus = DiagnosticOrderRiskPreflightStatus.UNKNOWN;
            return;
        }
        evaluation.pureRiskStatus = DiagnosticOrderRiskPreflightStatus.PASS;
    }

    private static void evaluateReconciliation(
            ReconciliationResult reconciliation,
            Instant evaluationTime,
            Evaluation evaluation
    ) {
        if (reconciliation == null) {
            evaluation.reconciliationStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.RECONCILIATION_NOT_EVALUATED);
            return;
        }
        boolean safeContract = reconciliation.diagnosticOnly()
                && reconciliation.readOnly()
                && reconciliation.noSideEffect()
                && !reconciliation.repairPerformed()
                && !reconciliation.orderSubmitted()
                && "BLOCKED".equals(reconciliation.executionReadiness());
        boolean onlyExecutionBlocker = reconciliation.blockers().stream()
                .allMatch(finding -> finding.taxonomy() == ReconciliationTaxonomy.EXECUTION_NOT_AUTHORIZED);
        boolean clean = safeContract
                && !reconciliation.evaluatedAt().isAfter(evaluationTime)
                && reconciliation.differences().isEmpty()
                && reconciliation.warnings().isEmpty()
                && reconciliation.unknowns().isEmpty()
                && reconciliation.notEvaluated().isEmpty()
                && onlyExecutionBlocker
                && "SNAPSHOT_MATCHED_AT_EVALUATION_TIME".equals(reconciliation.snapshotAssessment());
        if (clean) {
            evaluation.reconciliationStatus = DiagnosticOrderRiskPreflightStatus.PASS;
            return;
        }
        evaluation.reconciliationStatus = DiagnosticOrderRiskPreflightStatus.BLOCKED;
        evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.RECONCILIATION_BLOCKED);
        if (!reconciliation.differences().isEmpty()) {
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.RECONCILIATION_MISMATCH);
        }
    }

    private static void evaluateLocalAccount(
            LocalAccountMetadataSnapshot account,
            String diagnosticEnvironment,
            Evaluation evaluation
    ) {
        if (!account.configured()) {
            evaluation.localAccountStatus = DiagnosticOrderRiskPreflightStatus.BLOCKED;
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.LOCAL_ACCOUNT_UNCONFIGURED);
            return;
        }
        boolean scopeMatches = OKX.equals(normalize(account.exchange()))
                && SPOT.equals(normalize(account.marketType()))
                && diagnosticEnvironment.equals(normalize(account.tradeEnvironment()));
        if (!scopeMatches) {
            evaluation.localAccountStatus = DiagnosticOrderRiskPreflightStatus.BLOCKED;
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.LOCAL_ACCOUNT_SCOPE_MISMATCH);
            return;
        }
        if (!"ACTIVE".equals(normalize(account.localStatus()))) {
            evaluation.localAccountStatus = DiagnosticOrderRiskPreflightStatus.BLOCKED;
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.LOCAL_ACCOUNT_DISABLED);
            return;
        }
        evaluation.localAccountStatus = DiagnosticOrderRiskPreflightStatus.PASS;
    }

    private static void evaluateCredentialMetadata(
            CredentialMetadataSummary credential,
            Evaluation evaluation
    ) {
        if (!credential.configured() || credential.activeSummaryCount() == 0) {
            evaluation.credentialMetadataStatus = DiagnosticOrderRiskPreflightStatus.BLOCKED;
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.CREDENTIAL_METADATA_UNCONFIGURED);
            return;
        }
        long distinctTypes = credential.credentialTypes().stream()
                .map(DiagnosticOrderRiskPreflightService::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        if (credential.activeSummaryCount() != credential.credentialTypes().size()
                || distinctTypes != credential.credentialTypes().size()) {
            evaluation.credentialMetadataStatus = DiagnosticOrderRiskPreflightStatus.BLOCKED;
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.CREDENTIAL_METADATA_CONFLICT);
            return;
        }
        evaluation.credentialMetadataStatus = DiagnosticOrderRiskPreflightStatus.PASS;
    }

    private static void evaluateMarketdataQuality(
            MarketdataQualitySnapshot marketdata,
            Evaluation evaluation
    ) {
        switch (marketdata.quality()) {
            case OK -> evaluation.marketdataQualityStatus = DiagnosticOrderRiskPreflightStatus.PASS;
            case WARNING -> {
                evaluation.marketdataQualityStatus = DiagnosticOrderRiskPreflightStatus.UNKNOWN;
                evaluation.warning(DiagnosticOrderRiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK);
            }
            case BLOCKED -> {
                evaluation.marketdataQualityStatus = DiagnosticOrderRiskPreflightStatus.BLOCKED;
                evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK);
            }
            case UNKNOWN -> {
                evaluation.marketdataQualityStatus = DiagnosticOrderRiskPreflightStatus.UNKNOWN;
                evaluation.unknown(DiagnosticOrderRiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK);
            }
        }
    }

    private static DiagnosticOrderRiskPreflightStatus map(OrderPreviewStatus status) {
        return switch (status) {
            case PASS -> DiagnosticOrderRiskPreflightStatus.PASS;
            case BLOCKED -> DiagnosticOrderRiskPreflightStatus.BLOCKED;
            case UNKNOWN -> DiagnosticOrderRiskPreflightStatus.UNKNOWN;
            case NOT_EVALUATED -> DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
        };
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static final class Evaluation {
        private final Instant evaluationTime;
        private final Set<DiagnosticOrderRiskPreflightFindingCode> blockers = new LinkedHashSet<>();
        private final Set<DiagnosticOrderRiskPreflightFindingCode> warnings = new LinkedHashSet<>();
        private final Set<DiagnosticOrderRiskPreflightFindingCode> unknowns = new LinkedHashSet<>();
        private final Set<DiagnosticOrderRiskPreflightFindingCode> notEvaluated = new LinkedHashSet<>();
        private DiagnosticOrderRiskPreflightStatus structuralStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
        private DiagnosticOrderRiskPreflightStatus venueFactStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
        private DiagnosticOrderRiskPreflightStatus reconciliationStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
        private DiagnosticOrderRiskPreflightStatus localAccountStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
        private DiagnosticOrderRiskPreflightStatus credentialMetadataStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
        private DiagnosticOrderRiskPreflightStatus marketdataQualityStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;
        private DiagnosticOrderRiskPreflightStatus pureRiskStatus = DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED;

        private Evaluation(Instant evaluationTime) {
            this.evaluationTime = evaluationTime;
        }

        private static Evaluation create(Instant evaluationTime) {
            Evaluation evaluation = new Evaluation(evaluationTime);
            evaluation.unknown(DiagnosticOrderRiskPreflightFindingCode.MIN_NOTIONAL_UNKNOWN);
            evaluation.unknown(DiagnosticOrderRiskPreflightFindingCode.FEE_UNKNOWN);
            evaluation.unknown(DiagnosticOrderRiskPreflightFindingCode.REMOTE_PERMISSION_UNKNOWN);
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.BALANCE_NOT_EVALUATED);
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.POSITION_NOT_EVALUATED);
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.DAILY_LOSS_NOT_EVALUATED);
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.OPEN_ORDERS_RISK_NOT_EVALUATED);
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.KILL_SWITCH_NOT_EVALUATED);
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.DUPLICATE_REQUEST_NOT_EVALUATED);
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.RATE_LIMIT_NOT_EVALUATED);
            evaluation.notEvaluated(DiagnosticOrderRiskPreflightFindingCode.STATEFUL_RISK_PIPELINE_NOT_EVALUATED);
            evaluation.blocker(DiagnosticOrderRiskPreflightFindingCode.EXECUTION_NOT_AUTHORIZED);
            return evaluation;
        }

        private void blocker(DiagnosticOrderRiskPreflightFindingCode code) {
            moveTo(code, blockers);
        }

        private void warning(DiagnosticOrderRiskPreflightFindingCode code) {
            moveTo(code, warnings);
        }

        private void unknown(DiagnosticOrderRiskPreflightFindingCode code) {
            moveTo(code, unknowns);
        }

        private void notEvaluated(DiagnosticOrderRiskPreflightFindingCode code) {
            moveTo(code, notEvaluated);
        }

        private void moveTo(
                DiagnosticOrderRiskPreflightFindingCode code,
                Set<DiagnosticOrderRiskPreflightFindingCode> target
        ) {
            blockers.remove(code);
            warnings.remove(code);
            unknowns.remove(code);
            notEvaluated.remove(code);
            target.add(code);
        }

        private DiagnosticOrderRiskPreflightResult result() {
            return new DiagnosticOrderRiskPreflightResult(
                    evaluationTime,
                    structuralStatus,
                    venueFactStatus,
                    reconciliationStatus,
                    localAccountStatus,
                    credentialMetadataStatus,
                    marketdataQualityStatus,
                    pureRiskStatus,
                    DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED,
                    DiagnosticOrderRiskPreflightStatus.NOT_EVALUATED,
                    DiagnosticOrderRiskPreflightStatus.UNKNOWN,
                    DiagnosticOrderRiskPreflightStatus.BLOCKED,
                    true,
                    true,
                    true,
                    false,
                    false,
                    new ArrayList<>(blockers),
                    new ArrayList<>(warnings),
                    new ArrayList<>(unknowns),
                    new ArrayList<>(notEvaluated)
            );
        }
    }
}
