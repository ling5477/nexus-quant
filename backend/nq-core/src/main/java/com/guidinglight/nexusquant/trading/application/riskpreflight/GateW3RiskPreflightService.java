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
import java.util.List;
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
public final class GateW3RiskPreflightService {

    private static final String OKX = "OKX";
    private static final String SPOT = "SPOT";

    private final Clock clock;

    public GateW3RiskPreflightService(Clock clock) {
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
    public GateW3RiskPreflightResult evaluate(GateW3RiskPreflightRequest request) {
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
            evaluation.structuralStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
            evaluation.venueFactStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
            evaluation.pureRiskStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
            evaluation.blocker(GateW3RiskPreflightFindingCode.ORDER_PREVIEW_NOT_EVALUATED);
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
            evaluation.pureRiskStatus = GateW3RiskPreflightStatus.BLOCKED;
            evaluation.blocker(GateW3RiskPreflightFindingCode.ORDER_PREVIEW_BLOCKED);
            return;
        }
        if (preview.structuralStatus() == OrderPreviewStatus.NOT_EVALUATED
                || preview.venueFactStatus() == OrderPreviewStatus.NOT_EVALUATED) {
            evaluation.pureRiskStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
            evaluation.blocker(GateW3RiskPreflightFindingCode.ORDER_PREVIEW_NOT_EVALUATED);
            return;
        }
        if (preview.structuralStatus() == OrderPreviewStatus.UNKNOWN
                || preview.venueFactStatus() == OrderPreviewStatus.UNKNOWN) {
            evaluation.pureRiskStatus = GateW3RiskPreflightStatus.UNKNOWN;
            return;
        }
        evaluation.pureRiskStatus = GateW3RiskPreflightStatus.PASS;
    }

    private static void evaluateReconciliation(
            ReconciliationResult reconciliation,
            Instant evaluationTime,
            Evaluation evaluation
    ) {
        if (reconciliation == null) {
            evaluation.reconciliationStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.RECONCILIATION_NOT_EVALUATED);
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
            evaluation.reconciliationStatus = GateW3RiskPreflightStatus.PASS;
            return;
        }
        evaluation.reconciliationStatus = GateW3RiskPreflightStatus.BLOCKED;
        evaluation.blocker(GateW3RiskPreflightFindingCode.RECONCILIATION_BLOCKED);
        if (!reconciliation.differences().isEmpty()) {
            evaluation.blocker(GateW3RiskPreflightFindingCode.RECONCILIATION_MISMATCH);
        }
    }

    private static void evaluateLocalAccount(
            LocalAccountMetadataSnapshot account,
            String diagnosticEnvironment,
            Evaluation evaluation
    ) {
        if (!account.configured()) {
            evaluation.localAccountStatus = GateW3RiskPreflightStatus.BLOCKED;
            evaluation.blocker(GateW3RiskPreflightFindingCode.LOCAL_ACCOUNT_UNCONFIGURED);
            return;
        }
        boolean scopeMatches = OKX.equals(normalize(account.exchange()))
                && SPOT.equals(normalize(account.marketType()))
                && diagnosticEnvironment.equals(normalize(account.tradeEnvironment()));
        if (!scopeMatches) {
            evaluation.localAccountStatus = GateW3RiskPreflightStatus.BLOCKED;
            evaluation.blocker(GateW3RiskPreflightFindingCode.LOCAL_ACCOUNT_SCOPE_MISMATCH);
            return;
        }
        if (!"ACTIVE".equals(normalize(account.localStatus()))) {
            evaluation.localAccountStatus = GateW3RiskPreflightStatus.BLOCKED;
            evaluation.blocker(GateW3RiskPreflightFindingCode.LOCAL_ACCOUNT_DISABLED);
            return;
        }
        evaluation.localAccountStatus = GateW3RiskPreflightStatus.PASS;
    }

    private static void evaluateCredentialMetadata(
            CredentialMetadataSummary credential,
            Evaluation evaluation
    ) {
        if (!credential.configured() || credential.activeSummaryCount() == 0) {
            evaluation.credentialMetadataStatus = GateW3RiskPreflightStatus.BLOCKED;
            evaluation.blocker(GateW3RiskPreflightFindingCode.CREDENTIAL_METADATA_UNCONFIGURED);
            return;
        }
        long distinctTypes = credential.credentialTypes().stream()
                .map(GateW3RiskPreflightService::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        if (credential.activeSummaryCount() != credential.credentialTypes().size()
                || distinctTypes != credential.credentialTypes().size()) {
            evaluation.credentialMetadataStatus = GateW3RiskPreflightStatus.BLOCKED;
            evaluation.blocker(GateW3RiskPreflightFindingCode.CREDENTIAL_METADATA_CONFLICT);
            return;
        }
        evaluation.credentialMetadataStatus = GateW3RiskPreflightStatus.PASS;
    }

    private static void evaluateMarketdataQuality(
            MarketdataQualitySnapshot marketdata,
            Evaluation evaluation
    ) {
        switch (marketdata.quality()) {
            case OK -> evaluation.marketdataQualityStatus = GateW3RiskPreflightStatus.PASS;
            case WARNING -> {
                evaluation.marketdataQualityStatus = GateW3RiskPreflightStatus.UNKNOWN;
                evaluation.warning(GateW3RiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK);
            }
            case BLOCKED -> {
                evaluation.marketdataQualityStatus = GateW3RiskPreflightStatus.BLOCKED;
                evaluation.blocker(GateW3RiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK);
            }
            case UNKNOWN -> {
                evaluation.marketdataQualityStatus = GateW3RiskPreflightStatus.UNKNOWN;
                evaluation.unknown(GateW3RiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK);
            }
        }
    }

    private static GateW3RiskPreflightStatus map(OrderPreviewStatus status) {
        return switch (status) {
            case PASS -> GateW3RiskPreflightStatus.PASS;
            case BLOCKED -> GateW3RiskPreflightStatus.BLOCKED;
            case UNKNOWN -> GateW3RiskPreflightStatus.UNKNOWN;
            case NOT_EVALUATED -> GateW3RiskPreflightStatus.NOT_EVALUATED;
        };
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static final class Evaluation {
        private final Instant evaluationTime;
        private final Set<GateW3RiskPreflightFindingCode> blockers = new LinkedHashSet<>();
        private final Set<GateW3RiskPreflightFindingCode> warnings = new LinkedHashSet<>();
        private final Set<GateW3RiskPreflightFindingCode> unknowns = new LinkedHashSet<>();
        private final Set<GateW3RiskPreflightFindingCode> notEvaluated = new LinkedHashSet<>();
        private GateW3RiskPreflightStatus structuralStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
        private GateW3RiskPreflightStatus venueFactStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
        private GateW3RiskPreflightStatus reconciliationStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
        private GateW3RiskPreflightStatus localAccountStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
        private GateW3RiskPreflightStatus credentialMetadataStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
        private GateW3RiskPreflightStatus marketdataQualityStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;
        private GateW3RiskPreflightStatus pureRiskStatus = GateW3RiskPreflightStatus.NOT_EVALUATED;

        private Evaluation(Instant evaluationTime) {
            this.evaluationTime = evaluationTime;
        }

        private static Evaluation create(Instant evaluationTime) {
            Evaluation evaluation = new Evaluation(evaluationTime);
            evaluation.unknown(GateW3RiskPreflightFindingCode.MIN_NOTIONAL_UNKNOWN);
            evaluation.unknown(GateW3RiskPreflightFindingCode.FEE_UNKNOWN);
            evaluation.unknown(GateW3RiskPreflightFindingCode.REMOTE_PERMISSION_UNKNOWN);
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.BALANCE_NOT_EVALUATED);
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.POSITION_NOT_EVALUATED);
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.DAILY_LOSS_NOT_EVALUATED);
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.OPEN_ORDERS_RISK_NOT_EVALUATED);
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.KILL_SWITCH_NOT_EVALUATED);
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.DUPLICATE_REQUEST_NOT_EVALUATED);
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.RATE_LIMIT_NOT_EVALUATED);
            evaluation.notEvaluated(GateW3RiskPreflightFindingCode.STATEFUL_RISK_PIPELINE_NOT_EVALUATED);
            evaluation.blocker(GateW3RiskPreflightFindingCode.EXECUTION_NOT_AUTHORIZED);
            return evaluation;
        }

        private void blocker(GateW3RiskPreflightFindingCode code) {
            moveTo(code, blockers);
        }

        private void warning(GateW3RiskPreflightFindingCode code) {
            moveTo(code, warnings);
        }

        private void unknown(GateW3RiskPreflightFindingCode code) {
            moveTo(code, unknowns);
        }

        private void notEvaluated(GateW3RiskPreflightFindingCode code) {
            moveTo(code, notEvaluated);
        }

        private void moveTo(
                GateW3RiskPreflightFindingCode code,
                Set<GateW3RiskPreflightFindingCode> target
        ) {
            blockers.remove(code);
            warnings.remove(code);
            unknowns.remove(code);
            notEvaluated.remove(code);
            target.add(code);
        }

        private GateW3RiskPreflightResult result() {
            return new GateW3RiskPreflightResult(
                    evaluationTime,
                    structuralStatus,
                    venueFactStatus,
                    reconciliationStatus,
                    localAccountStatus,
                    credentialMetadataStatus,
                    marketdataQualityStatus,
                    pureRiskStatus,
                    GateW3RiskPreflightStatus.NOT_EVALUATED,
                    GateW3RiskPreflightStatus.NOT_EVALUATED,
                    GateW3RiskPreflightStatus.UNKNOWN,
                    GateW3RiskPreflightStatus.BLOCKED,
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
