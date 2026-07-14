package com.guidinglight.nexusquant.trading.application.orderpreview;

import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleFreshness;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleFreshnessEvaluator;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * DryRunOrderPreviewService 编排 OKX Spot LIMIT-only 的 internal diagnostic preview。
 *
 * <p>生命周期由 Spring 管理，实例无共享可变状态且线程安全。它只依赖 bounded local read port 与
 * pure freshness evaluator；不依赖 network、credential、account、risk mutation、order write、audit、
 * ledger 或 event port。该服务没有事务和持久化副作用，不提供任何交易授权。</p>
 */
@Service
public final class DryRunOrderPreviewService {

    private static final Logger log = LoggerFactory.getLogger(DryRunOrderPreviewService.class);
    private static final String OKX = "OKX";
    private static final String SPOT = "SPOT";
    private static final String USDT = "USDT";

    private final InstrumentCatalogReadPort instrumentCatalogReadPort;
    private final VenueRuleFreshnessEvaluator freshnessEvaluator;

    public DryRunOrderPreviewService(
            InstrumentCatalogReadPort instrumentCatalogReadPort,
            VenueRuleFreshnessEvaluator freshnessEvaluator
    ) {
        this.instrumentCatalogReadPort = Objects.requireNonNull(
                instrumentCatalogReadPort,
                "instrumentCatalogReadPort must not be null"
        );
        this.freshnessEvaluator = Objects.requireNonNull(freshnessEvaluator, "freshnessEvaluator must not be null");
    }

    /**
     * 对原始 LIMIT 请求执行只读、确定性的结构与 local venue-fact 检查。
     *
     * <p>方法不幂等写入，因为它完全无写侧；相同 request、catalog snapshot 与 evaluator 配置返回
     * 相同结果。任何 local read/schema/checksum/freshness 缺口均 fail-closed。risk/account/balance 不会
     * 被调用或假设，execution readiness 恒为 BLOCKED。</p>
     *
     * @param request internal preview 请求
     * @return 分维度状态与互斥分类集合；绝不包含 order id
     */
    public DryRunOrderPreviewResult preview(DryRunOrderPreviewRequest request) {
        Evaluation evaluation = Evaluation.create();
        if (request == null) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.INPUT_REQUIRED);
            return evaluation.result();
        }

        validateRequest(request, evaluation);
        if (evaluation.hasStructuralBlocker()) {
            return evaluation.result();
        }

        List<InstrumentCatalogItem> matches;
        try {
            matches = instrumentCatalogReadPort.findByExchangeAndSymbols(OKX, List.of(request.symbol().trim()));
        } catch (RuntimeException exception) {
            // 只记录稳定错误码，不打印查询参数、原始异常或潜在敏感上下文。
            log.warn("order preview local fact read failed, errorCode=LOCAL_FACT_READ_FAILED");
            evaluation.venueBlocker(OrderPreviewFindingCode.LOCAL_FACT_READ_FAILED);
            return evaluation.result();
        }
        if (matches.size() != 1) {
            evaluation.venueBlocker(OrderPreviewFindingCode.INSTRUMENT_NOT_FOUND);
            return evaluation.result();
        }

        InstrumentCatalogItem item = matches.getFirst();
        if (!OKX.equals(item.exchangeCode())) {
            evaluation.venueBlocker(OrderPreviewFindingCode.INSTRUMENT_NOT_FOUND);
            return evaluation.result();
        }
        if (!SPOT.equals(item.instrumentType())) {
            evaluation.venueBlocker(OrderPreviewFindingCode.INSTRUMENT_TYPE_NOT_SUPPORTED);
            return evaluation.result();
        }

        evaluateVenueFacts(item, request, evaluation);
        return evaluation.result();
    }

    private static void validateRequest(DryRunOrderPreviewRequest request, Evaluation evaluation) {
        if (!OKX.equals(normalize(request.exchange()))) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.EXCHANGE_NOT_SUPPORTED);
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.SYMBOL_REQUIRED);
        }
        if (request.side() == null) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.SIDE_NOT_SUPPORTED);
        }
        if (request.orderType() != DryRunOrderPreviewRequest.OrderType.LIMIT) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.ORDER_TYPE_NOT_SUPPORTED);
        }
        if (request.requestedLimitPrice() == null || request.requestedLimitPrice().signum() <= 0) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.INVALID_PRICE);
        }
        if (request.requestedQuantity() == null || request.requestedQuantity().signum() <= 0) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.INVALID_QUANTITY);
        }
        if (request.evaluationTime() == null) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.EVALUATION_TIME_REQUIRED);
        }
        if (request.traceId() == null || request.traceId().isBlank()) {
            evaluation.structuralBlocker(OrderPreviewFindingCode.TRACE_ID_REQUIRED);
        }
        if (request.requestedLimitPrice() != null && request.requestedLimitPrice().signum() > 0
                && request.requestedQuantity() != null && request.requestedQuantity().signum() > 0) {
            evaluation.grossNotional = request.requestedLimitPrice().multiply(request.requestedQuantity());
        }
    }

    private void evaluateVenueFacts(
            InstrumentCatalogItem item,
            DryRunOrderPreviewRequest request,
            Evaluation evaluation
    ) {
        VenueRuleFreshness freshness = freshnessEvaluator.evaluateAt(item, request.evaluationTime());
        if (freshness.availability() != VenueRuleFreshness.Availability.AVAILABLE) {
            evaluation.venueBlocker(mapFreshnessReason(freshness.blockingReason()));
            addMissingMaximumFacts(item, evaluation);
            return;
        }

        if (isMisaligned(request.requestedLimitPrice(), item.tickSize())) {
            evaluation.venueBlocker(OrderPreviewFindingCode.INVALID_TICK_ALIGNMENT);
        }
        if (isMisaligned(request.requestedQuantity(), item.stepSize())) {
            evaluation.venueBlocker(OrderPreviewFindingCode.INVALID_STEP_ALIGNMENT);
        }
        if (request.requestedQuantity().compareTo(item.minQuantity()) < 0) {
            evaluation.venueBlocker(OrderPreviewFindingCode.BELOW_MIN_QUANTITY);
        }
        if (item.maxLimitQuantity() == null) {
            evaluation.unknown(OrderPreviewFindingCode.MAX_LIMIT_QUANTITY_UNKNOWN);
        } else if (request.requestedQuantity().compareTo(item.maxLimitQuantity()) > 0) {
            evaluation.venueBlocker(OrderPreviewFindingCode.ABOVE_MAX_LIMIT_QUANTITY);
        }

        if (item.maxLimitNotionalUsd() == null || !USDT.equals(item.quoteAsset())) {
            // 没有受信任 FX snapshot 时，非 USDT quote 不得与 USD limit 做隐式换算。
            evaluation.unknown(OrderPreviewFindingCode.MAX_LIMIT_NOTIONAL_UNKNOWN);
        } else if (evaluation.grossNotional.compareTo(item.maxLimitNotionalUsd()) > 0) {
            evaluation.venueBlocker(OrderPreviewFindingCode.ABOVE_MAX_LIMIT_NOTIONAL);
        }
    }

    private static void addMissingMaximumFacts(InstrumentCatalogItem item, Evaluation evaluation) {
        if (item.maxLimitQuantity() == null) {
            evaluation.unknown(OrderPreviewFindingCode.MAX_LIMIT_QUANTITY_UNKNOWN);
        }
        if (item.maxLimitNotionalUsd() == null || !USDT.equals(item.quoteAsset())) {
            evaluation.unknown(OrderPreviewFindingCode.MAX_LIMIT_NOTIONAL_UNKNOWN);
        }
    }

    private static OrderPreviewFindingCode mapFreshnessReason(String reason) {
        if ("INSTRUMENT_NOT_LIVE".equals(reason)) {
            return OrderPreviewFindingCode.INSTRUMENT_NOT_LIVE;
        }
        if ("SOURCE_MISMATCH".equals(reason) || "SCHEMA_VERSION_MISSING_OR_MISMATCH".equals(reason)) {
            return OrderPreviewFindingCode.VENUE_RULE_SCHEMA_UNSUPPORTED;
        }
        if ("CHECKSUM_MISSING_OR_CONFLICT".equals(reason)) {
            return OrderPreviewFindingCode.VENUE_RULE_CHECKSUM_INVALID;
        }
        if ("FRESH_UNTIL_EXCEEDED".equals(reason)
                || "OBSERVED_AT_IN_FUTURE".equals(reason)
                || "STALE_AFTER_INVALID".equals(reason)) {
            return OrderPreviewFindingCode.VENUE_RULE_FACTS_STALE;
        }
        return OrderPreviewFindingCode.VENUE_RULE_FACTS_MISSING;
    }

    private static boolean isMisaligned(BigDecimal value, BigDecimal increment) {
        return value.remainder(increment).compareTo(BigDecimal.ZERO) != 0;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static final class Evaluation {

        private final List<OrderPreviewFindingCode> structuralBlockers = new ArrayList<>();
        private final List<OrderPreviewFindingCode> venueBlockers = new ArrayList<>();
        private final List<OrderPreviewFindingCode> unknowns = new ArrayList<>();
        private BigDecimal grossNotional;

        private static Evaluation create() {
            Evaluation evaluation = new Evaluation();
            evaluation.unknown(OrderPreviewFindingCode.MIN_NOTIONAL_UNKNOWN);
            evaluation.unknown(OrderPreviewFindingCode.FEE_UNKNOWN);
            evaluation.unknown(OrderPreviewFindingCode.ACCOUNT_PERMISSION_UNKNOWN);
            return evaluation;
        }

        private void structuralBlocker(OrderPreviewFindingCode code) {
            addUnique(structuralBlockers, code);
        }

        private void venueBlocker(OrderPreviewFindingCode code) {
            addUnique(venueBlockers, code);
        }

        private void unknown(OrderPreviewFindingCode code) {
            addUnique(unknowns, code);
        }

        private boolean hasStructuralBlocker() {
            return !structuralBlockers.isEmpty();
        }

        private DryRunOrderPreviewResult result() {
            List<OrderPreviewFindingCode> blockers = new ArrayList<>(structuralBlockers);
            blockers.addAll(venueBlockers);
            blockers.add(OrderPreviewFindingCode.EXECUTION_NOT_AUTHORIZED);
            List<OrderPreviewFindingCode> notEvaluated = List.of(
                    OrderPreviewFindingCode.BALANCE_NOT_EVALUATED,
                    OrderPreviewFindingCode.RISK_PIPELINE_NOT_EVALUATED
            );
            return new DryRunOrderPreviewResult(
                    structuralBlockers.isEmpty() ? OrderPreviewStatus.PASS : OrderPreviewStatus.BLOCKED,
                    venueStatus(),
                    OrderPreviewStatus.NOT_EVALUATED,
                    OrderPreviewStatus.UNKNOWN,
                    OrderPreviewStatus.BLOCKED,
                    true,
                    true,
                    false,
                    grossNotional,
                    blockers,
                    List.of(),
                    unknowns,
                    notEvaluated
            );
        }

        private OrderPreviewStatus venueStatus() {
            if (!structuralBlockers.isEmpty()) {
                return OrderPreviewStatus.NOT_EVALUATED;
            }
            return venueBlockers.isEmpty() ? OrderPreviewStatus.PASS : OrderPreviewStatus.BLOCKED;
        }

        private static void addUnique(List<OrderPreviewFindingCode> target, OrderPreviewFindingCode code) {
            if (!target.contains(code)) {
                target.add(code);
            }
        }
    }
}
