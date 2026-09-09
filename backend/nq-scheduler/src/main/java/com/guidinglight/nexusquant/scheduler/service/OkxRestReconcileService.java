package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.observability.operational.OperationalObservation;
import com.guidinglight.nexusquant.observability.operational.SafeOperationalObservation;
import static com.guidinglight.nexusquant.observability.operational.OperationalObservation.Operation.*;
import static com.guidinglight.nexusquant.observability.operational.OperationalObservation.Signal.*;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.model.AdapterTradeReport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.OrderLifecycleService;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OkxRestReconcileService 负责 GateC-1 的 REST-only 同步器。
 * <p>
 * Why:
 * WS 还未接入时，非终态订单只能靠 `getOrder + fills` 推进；
 * 但同步器仍必须复用 core 的状态机与 ledger 的幂等能力，不能直接改 orders 或直接写账本表。
 */
@Component
@ConditionalOnProperty(
        prefix = "nq.runtime.trading-components",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class OkxRestReconcileService {

    private static final String SOURCE = "nq-scheduler.okx-rest-reconcile";
    private static final int DEFAULT_LIMIT = 100;

    private final OrderCommandService orderCommandService;
    private final OrderLifecycleService orderLifecycleService;
    private final OkxExchangeAdapter okxExchangeAdapter;
    private final TradeRepository tradeRepository;
    private final TradeLedgerGateway tradeLedgerGateway;
    private final EventPublisherPort eventPublisherPort;
    private final com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final OperationalObservation observation;

    /**
     * @param orderCommandService   订单编排服务
     * @param orderLifecycleService 订单生命周期入口
     * @param okxExchangeAdapter    OKX adapter
     * @param tradeRepository       trades 仓储
     * @param tradeLedgerGateway    ledger 网关
     * @param eventPublisherPort    事件事实链追加端口
     * @param auditLogRepository    审计仓储
     */
    public OkxRestReconcileService(
            OrderCommandService orderCommandService,
            OrderLifecycleService orderLifecycleService,
            OkxExchangeAdapter okxExchangeAdapter,
            TradeRepository tradeRepository,
            TradeLedgerGateway tradeLedgerGateway,
            EventPublisherPort eventPublisherPort,
            AuditLogRepository auditLogRepository
    ) {
        this(orderCommandService, orderLifecycleService, okxExchangeAdapter, tradeRepository, tradeLedgerGateway, eventPublisherPort, auditLogRepository, OperationalObservation.NOOP);
    }

    @Autowired
    public OkxRestReconcileService(
            OrderCommandService orderCommandService,
            OrderLifecycleService orderLifecycleService,
            OkxExchangeAdapter okxExchangeAdapter,
            TradeRepository tradeRepository,
            TradeLedgerGateway tradeLedgerGateway,
            EventPublisherPort eventPublisherPort,
            AuditLogRepository auditLogRepository,
            OperationalObservation observation
    ) {
        this.observation = new SafeOperationalObservation(observation);
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.orderLifecycleService = Objects.requireNonNull(orderLifecycleService, "orderLifecycleService must not be null");
        this.okxExchangeAdapter = Objects.requireNonNull(okxExchangeAdapter, "okxExchangeAdapter must not be null");
        this.tradeRepository = Objects.requireNonNull(tradeRepository, "tradeRepository must not be null");
        this.tradeLedgerGateway = Objects.requireNonNull(tradeLedgerGateway, "tradeLedgerGateway must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 定时执行 OKX REST reconcile。
     */
    @Scheduled(
            fixedDelayString = "${nq.okx.reconcile.fixed-delay-ms:5000}",
            initialDelayString = "${nq.okx.reconcile.initial-delay-ms:5000}"
    )
    public void scheduledReconcile() {
        reconcileOnce(DEFAULT_LIMIT);
    }

    /**
     * 执行一次同步。
     *
     * @param limit 单次扫描的订单候选总上限，含 CANCELLED；所有状态共享一次查询预算
     * @return 本次新写入的 trade 数量
     */
    public int reconcileOnce(int limit) {
        observation.record(OKX_RECONCILE, ATTEMPT, 1);
        try {
            int result = reconcileObservedFacts(limit);
            // 成功表示本轮调用正常完成；订单未找到或账本拒绝等事实仍由独立 unresolved 指标表达。
            observation.record(OKX_RECONCILE, SUCCESS, 1);
            return result;
        } catch (RuntimeException ex) {
            observation.record(OKX_RECONCILE, FAILURE, 1);
            throw ex;
        }
    }

    private int reconcileObservedFacts(int limit) {
        int newTrades = 0;
        for (OrderRecord order : orderCommandService.reserveReconciliationCandidates("OKX",
                List.of(
                        OrderStatus.SENT,
                        OrderStatus.ACCEPTED,
                        OrderStatus.PARTIALLY_FILLED,
                        OrderStatus.CANCEL_REQUESTED,
                        OrderStatus.CANCEL_REJECTED,
                        OrderStatus.FILLED,
                        OrderStatus.CANCELLED
                ),
                limit
        )) {
            if (!"OKX".equals(order.venue())) {
                continue;
            }
            if (order.status() == OrderStatus.FILLED) {
                if (!canReconcileFilledOrder(order)) {
                    continue;
                }
                newTrades += reconcileFilledOrder(order, limit);
                continue;
            }
            if (order.status() == OrderStatus.CANCELLED) {
                newTrades += reconcileCancelledOrder(order, limit);
                continue;
            }
            newTrades += reconcileSingleOrder(order, limit);
        }
        return newTrades;
    }

    /**
     * FILLED order 只要保留稳定 venue identity，就继续进入有界的 Trade/Ledger 收敛检查。
     */
    private boolean canReconcileFilledOrder(OrderRecord order) {
        return order.externalOrderId() != null && !order.externalOrderId().isBlank();
    }

    /**
     * 对已终态 FILLED 订单补扫缺失 Trade，或重放 existing Trade 以确保 Ledger 收敛。
     * <p>
     * Why:
     * 这里刻意不再走 `getOrder -> alignOrderStatus(...)`，因为订单已经是终态；
     * 本批的最小修复目标只是补齐 `fills -> trades -> ledger` 或 `existing trade -> ledger`，
     * 避免为了等 fills 再次改动终态推进时机。
     */
    private int reconcileFilledOrder(OrderRecord order, int limit) {
        int newTrades = reconcileFills(order, limit);
        auditLogRepository.append(
                "RECONCILE",
                "OKX_FILLED_ORDER_FILL_BACKFILL_COMPLETED",
                order.orderId(),
                order.traceId(),
                java.util.Map.of(
                        "order_id", order.orderId(),
                        "status", order.status().name(),
                        "external_order_id", String.valueOf(order.externalOrderId()),
                        "new_trades", newTrades
                )
        );
        return newTrades;
    }

    /**
     * 撤单终态可以与部分成交共存；全部 durable 成交足量时才使用专用入口纠正终态。
     * 缺少稳定外部身份时仅记录未收敛观测，禁止猜测身份查询或重新发出交易命令。
     */
    private int reconcileCancelledOrder(OrderRecord order, int limit) {
        if (order.externalOrderId() == null || order.externalOrderId().isBlank()) {
            observation.record(OKX_RECONCILE, UNRESOLVED, 1);
            return 0;
        }
        int newTrades = reconcileFills(order, limit);
        OrderRecord corrected = orderLifecycleService.reconcileCancelledExecution(order.orderId(), order.traceId());
        auditLogRepository.append(
                "RECONCILE",
                "OKX_CANCELLED_ORDER_FILL_BACKFILL_COMPLETED",
                order.orderId(),
                order.traceId(),
                java.util.Map.of(
                        "order_id", order.orderId(),
                        "status", corrected.status().name(),
                        "external_order_id", order.externalOrderId(),
                        "new_trades", newTrades
                )
        );
        return newTrades;
    }

    private int reconcileSingleOrder(OrderRecord currentOrder, int limit) {
        AdapterOrderSnapshot snapshot = okxExchangeAdapter.getOrder(new com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery(
                currentOrder.accountId(),
                currentOrder.venue(),
                currentOrder.symbol(),
                currentOrder.clientOrderId(),
                currentOrder.externalOrderId(),
                currentOrder.traceId()
        ));
        if (snapshot.resultCategory() == AdapterResultCategory.NOT_FOUND) {
            observation.record(OKX_RECONCILE, UNRESOLVED, 1);
            auditLogRepository.append(
                    "RECONCILE",
                    "OKX_RECONCILE_ORDER_NOT_FOUND",
                    currentOrder.orderId(),
                    currentOrder.traceId(),
                    java.util.Map.of(
                            "order_id", currentOrder.orderId(),
                            "client_order_id", currentOrder.clientOrderId(),
                            "exchange_order_id", String.valueOf(currentOrder.externalOrderId())
                    )
            );
            return 0;
        }
        if (snapshot.resultCategory() != AdapterResultCategory.SUCCESS) {
            throw new IllegalStateException(
                    "okx getOrder failed, category=" + snapshot.resultCategory()
                            + ", code=" + (snapshot.error() == null ? "UNKNOWN" : snapshot.error().code())
            );
        }
        OrderRecord updatedOrder = currentOrder;
        if (updatedOrder.externalOrderId() == null && snapshot.exchangeOrderId() != null && !snapshot.exchangeOrderId().isBlank()) {
            updatedOrder = orderCommandService.linkExternalOrderId(
                    updatedOrder.orderId(),
                    snapshot.exchangeOrderId(),
                    updatedOrder.traceId()
            );
        }
        OrderRecord statusCandidate = updatedOrder;
        int newTrades = reconcileFills(updatedOrder, limit,
                () -> alignOrderStatus(statusCandidate, snapshot.externalStatus(), statusCandidate.traceId()));
        updatedOrder = orderCommandService.findByOrderId(updatedOrder.orderId()).orElse(updatedOrder);
        // 查询和记账期间 CANCEL ACK 也可能先提交；不能只依据扫描时的旧状态选择纠正入口。
        if (updatedOrder.status() == OrderStatus.CANCELLED) {
            updatedOrder = orderLifecycleService.reconcileCancelledExecution(updatedOrder.orderId(), updatedOrder.traceId());
        }
        auditLogRepository.append(
                "RECONCILE",
                "OKX_RECONCILE_COMPLETED",
                updatedOrder.orderId(),
                updatedOrder.traceId(),
                java.util.Map.of(
                        "order_id", updatedOrder.orderId(),
                        "status", updatedOrder.status().name(),
                        "external_order_id", String.valueOf(updatedOrder.externalOrderId()),
                        "new_trades", newTrades
                )
        );
        return newTrades;
    }

    private void alignOrderStatus(OrderRecord order, String targetStatusName, String traceId) {
        // Why:
        // reconcile 与 cancel 命令可能并发触发，同一个 order 在本方法执行前后已被其它链路推进到终态。
        // 这里先读最新状态，避免用过期快照做二次迁移导致非法状态跳转（例如 CANCELLED -> CANCEL_REQUESTED）。
        OrderStatus currentStatus = orderCommandService.findByOrderId(order.orderId())
                .map(OrderRecord::status)
                .orElse(order.status());
        OrderStatus targetStatus = OrderStatus.valueOf(targetStatusName);
        if (currentStatus == targetStatus) {
            return;
        }
        if (isTerminalStatus(currentStatus)) {
            return;
        }
        // Why:
        // 旧链路里 cancel reject 可能把订单停留在 CANCEL_REQUESTED。
        // 当交易所事实回报为“仍存活/已成交/已拒绝”时，先显式落到 CANCEL_REJECTED，再继续对齐，
        // 避免直接 CANCEL_REQUESTED -> ACCEPTED/PARTIALLY_FILLED 的非法迁移。
        if (currentStatus == OrderStatus.CANCEL_REQUESTED && targetStatus != OrderStatus.CANCELLED) {
            try {
                orderLifecycleService.rejectCancel(order.orderId(), "RECONCILE_CANCEL_REJECTED", traceId);
            } catch (IllegalStateException ex) {
                OrderStatus latestStatus = orderCommandService.findByOrderId(order.orderId())
                        .map(OrderRecord::status)
                        .orElse(currentStatus);
                if (latestStatus == targetStatus || isTerminalStatus(latestStatus)) {
                    return;
                }
                throw ex;
            }
            if (targetStatus != OrderStatus.CANCEL_REJECTED) {
                orderLifecycleService.applyExternalStatus(order.orderId(), targetStatus, "RECONCILE_STATUS_ALIGN", traceId);
            }
            return;
        }
        if (targetStatus == OrderStatus.PARTIALLY_FILLED && currentStatus == OrderStatus.SENT) {
            orderLifecycleService.acknowledge(order.orderId(), "RECONCILE_CONFIRM_ACCEPTED", traceId);
            orderLifecycleService.markPartiallyFilled(order.orderId(), "RECONCILE_PARTIAL_FILL", traceId);
            return;
        }
        if (targetStatus == OrderStatus.CANCELLED && currentStatus != OrderStatus.CANCEL_REQUESTED) {
            try {
                orderLifecycleService.requestCancel(order.orderId(), "RECONCILE_CANCEL_REQUESTED", traceId);
            } catch (IllegalStateException ex) {
                OrderStatus latestStatus = orderCommandService.findByOrderId(order.orderId())
                        .map(OrderRecord::status)
                        .orElse(currentStatus);
                if (latestStatus == OrderStatus.CANCELLED || isTerminalStatus(latestStatus)) {
                    return;
                }
                throw ex;
            }
            orderLifecycleService.cancel(order.orderId(), "RECONCILE_CANCELLED", traceId);
            return;
        }
        orderLifecycleService.applyExternalStatus(order.orderId(), targetStatus, "RECONCILE_STATUS_ALIGN", traceId);
    }

    private boolean isTerminalStatus(OrderStatus status) {
        return status == OrderStatus.FILLED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED;
    }

    private int reconcileFills(OrderRecord order, int limit) {
        return reconcileFills(order, limit, () -> { });
    }

    private int reconcileFills(OrderRecord order, int limit, Runnable alignValidatedStatus) {
        if (order.externalOrderId() == null || order.externalOrderId().isBlank()) {
            return 0;
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<AdapterTradeReport> tradeReports = List.copyOf(
                okxExchangeAdapter.listTradeReports(order.symbol(), order.externalOrderId(), order.traceId())
        );
        if (tradeReports.size() > limit) {
            auditRecoveryBoundaryFailure(order, "VENUE_REPORT_LIMIT_EXCEEDED");
            throw new IllegalStateException("per-order venue report recovery limit exceeded");
        }
        List<PaperTradeRecord> durableTrades;
        try {
            durableTrades = tradeRepository.findAllByOrderId(order.orderId(), limit);
        } catch (IllegalStateException ex) {
            auditRecoveryBoundaryFailure(order, "TRADE_ORDER_ENVIRONMENT_MISMATCH".equals(ex.getMessage())
                    ? "TRADE_ORDER_ENVIRONMENT_MISMATCH" : "DURABLE_TRADE_LIMIT_EXCEEDED");
            throw ex;
        }
        // 先验证完整 durable 集合与本次报告的并集；不能把分页响应当成累计量，也不能先写入 overfill。
        var uniqueReports = new java.util.LinkedHashMap<String, AdapterTradeReport>();
        var quantities = new java.util.LinkedHashMap<String, java.math.BigDecimal>();
        var durableByFill = new java.util.HashMap<String, PaperTradeRecord>();
        for (PaperTradeRecord trade : durableTrades) {
            validateTradeOrderIdentity(order, trade);
            if (trade.qty() == null || trade.qty().signum() <= 0
                    || durableByFill.putIfAbsent(trade.exchangeTradeId(), trade) != null) {
                failRecoveryIdentityMismatch(order, trade, "INVALID_DURABLE_FILL_QUANTITY_OR_IDENTITY");
            }
            quantities.put(trade.exchangeTradeId(), trade.qty());
        }
        for (AdapterTradeReport report : tradeReports) {
            validateVenueReportOrderIdentity(order, report);
            if (report.quantity().signum() <= 0) failRecoveryIdentityMismatch(order, null, "INVALID_FILL_QUANTITY");
            AdapterTradeReport previous = uniqueReports.putIfAbsent(report.exchangeTradeId(), report);
            if (previous != null && (previous.price().compareTo(report.price()) != 0
                    || previous.quantity().compareTo(report.quantity()) != 0
                    || normalizedFee(previous).compareTo(normalizedFee(report)) != 0
                    || !Objects.equals(previous.feeAsset(), report.feeAsset())
                    || !Objects.equals(previous.tradeTs(), report.tradeTs()))) {
                failRecoveryIdentityMismatch(order, null, "CONFLICTING_DUPLICATE_VENUE_FILL");
            }
            PaperTradeRecord durable = durableByFill.get(report.exchangeTradeId());
            if (durable != null) validateTradeVenueReportIdentity(order, durable, report);
            quantities.putIfAbsent(report.exchangeTradeId(), report.quantity());
        }
        java.math.BigDecimal executed = quantities.values().stream()
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (order.qty() == null || order.qty().signum() <= 0 || executed.compareTo(order.qty()) > 0) {
            auditRecoveryBoundaryFailure(order, "RECONCILIATION_OVERFILL");
            throw new IllegalStateException("RECONCILIATION_OVERFILL: " + order.orderId());
        }
        // 先拒绝非法数量，再保持原有 venue 状态推进早于 Trade/Ledger 的恢复契约。
        alignValidatedStatus.run();
        int newTrades = 0;
        Set<String> reportTradeIds = new HashSet<>();
        for (AdapterTradeReport tradeReport : uniqueReports.values()) {
            validateVenueReportOrderIdentity(order, tradeReport);
            if (!reportTradeIds.add(tradeReport.exchangeTradeId())) {
                failRecoveryIdentityMismatch(order, null, "DUPLICATE_VENUE_EXCHANGE_TRADE_ID");
            }
            var existingTrade = tradeRepository.findByExchangeAndExchangeTradeId("OKX", tradeReport.exchangeTradeId());
            if (existingTrade.isPresent()) {
                ensureLedgerConvergence(order, existingTrade.orElseThrow(), tradeReport, true);
                auditLogRepository.append(
                        "RECONCILE",
                        "OKX_FILL_DEDUP_HIT",
                        order.orderId(),
                        order.traceId(),
                        java.util.Map.of("exchange_trade_id", tradeReport.exchangeTradeId(), "order_id", order.orderId())
                );
                continue;
            }
            // Why: OKX fills 的 fee 常以负数表示“扣减”，而账本入参要求传入非负费用金额。
            // 这里统一转成绝对值，保留“费用大小”语义，避免 reconcile 在成交已落库后因参数校验中断。
            java.math.BigDecimal normalizedFee = tradeReport.fee() == null ? java.math.BigDecimal.ZERO : tradeReport.fee().abs();
            PaperTradeRecord trade = new PaperTradeRecord(
                    "trd-" + UUID.randomUUID(),
                    order.orderId(),
                    order.accountId(),
                    tradeReport.symbol(),
                    "OKX",
                    tradeReport.exchangeOrderId(),
                    tradeReport.exchangeTradeId(),
                    tradeReport.price(),
                    tradeReport.quantity(),
                    normalizedFee,
                    tradeReport.feeAsset(),
                    order.traceId(),
                    tradeReport.tradeTs()
            );
            tradeRepository.insertWithRequiredEvent(trade);
            ensureLedgerConvergence(order, trade, tradeReport, false);
            newTrades++;
        }
        for (PaperTradeRecord durableTrade : durableTrades) {
            if (reportTradeIds.contains(durableTrade.exchangeTradeId())) {
                continue;
            }
            ensureLedgerConvergence(order, durableTrade, null, true);
        }
        return newTrades;
    }

    /**
     * Replays an existing durable Trade through the canonical idempotent ledger path.
     *
     * <p>Why: Trade persistence and ledger posting have separate transaction boundaries. A previous
     * ledger failure must remain recoverable without inserting a replacement Trade or maintaining a
     * second projection implementation.</p>
     */
    private LedgerPostingResult ensureLedgerConvergence(
            OrderRecord order,
            PaperTradeRecord trade,
            AdapterTradeReport tradeReport,
            boolean recovery
    ) {
        if (!recovery) return replayLedgerFacts(order, trade, tradeReport, false);
        observation.record(LEDGER_RECOVERY, ATTEMPT, 1);
        try {
            LedgerPostingResult result = replayLedgerFacts(order, trade, tradeReport, true);
            observation.record(LEDGER_RECOVERY, result.posted() ? SUCCESS : FAILURE, 1);
            if (!result.posted()) observation.record(LEDGER_RECOVERY, UNRESOLVED, 1);
            return result;
        } catch (RuntimeException ex) {
            observation.record(LEDGER_RECOVERY, FAILURE, 1);
            throw ex;
        }
    }

    private LedgerPostingResult replayLedgerFacts(
            OrderRecord order, PaperTradeRecord trade, AdapterTradeReport tradeReport, boolean recovery
    ) {
        validateTradeOrderIdentity(order, trade);
        if (tradeReport != null) {
            validateTradeVenueReportIdentity(order, trade, tradeReport);
        }
        tradeRepository.ensureRequiredEvent(trade.tradeId());
        return postLedger(order, trade, recovery);
    }

    private void validateTradeOrderIdentity(OrderRecord order, PaperTradeRecord trade) {
        if (!Objects.equals(trade.orderId(), order.orderId())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_ORDER_ID_MISMATCH");
        }
        if (!Objects.equals(trade.accountId(), order.accountId())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_ACCOUNT_ID_MISMATCH");
        }
        if (!Objects.equals(trade.symbol(), order.symbol())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_SYMBOL_MISMATCH");
        }
        if (!Objects.equals(trade.exchange(), order.venue())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_EXCHANGE_MISMATCH");
        }
        if (!Objects.equals(trade.externalOrderId(), order.externalOrderId())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_EXTERNAL_ORDER_ID_MISMATCH");
        }
        if (trade.exchangeTradeId() == null || trade.exchangeTradeId().isBlank()) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_EXCHANGE_TRADE_ID_MISSING");
        }
    }

    private void validateVenueReportOrderIdentity(OrderRecord order, AdapterTradeReport report) {
        if (!Objects.equals(report.exchangeCode(), order.venue())) {
            failRecoveryIdentityMismatch(order, null, "REPORT_EXCHANGE_MISMATCH");
        }
        if (!Objects.equals(report.symbol(), order.symbol())) {
            failRecoveryIdentityMismatch(order, null, "REPORT_SYMBOL_MISMATCH");
        }
        if (!Objects.equals(report.exchangeOrderId(), order.externalOrderId())) {
            failRecoveryIdentityMismatch(order, null, "REPORT_EXTERNAL_ORDER_ID_MISMATCH");
        }
        if (!Objects.equals(report.side(), order.side())) {
            failRecoveryIdentityMismatch(order, null, "REPORT_SIDE_MISMATCH");
        }
        if (report.accountId() != null && !Objects.equals(report.accountId(), order.accountId())) {
            failRecoveryIdentityMismatch(order, null, "REPORT_ACCOUNT_ID_MISMATCH");
        }
        if (report.clientOrderId() != null && !Objects.equals(report.clientOrderId(), order.clientOrderId())) {
            failRecoveryIdentityMismatch(order, null, "REPORT_CLIENT_ORDER_ID_MISMATCH");
        }
        if (report.exchangeTradeId() == null || report.exchangeTradeId().isBlank()) {
            failRecoveryIdentityMismatch(order, null, "REPORT_EXCHANGE_TRADE_ID_MISSING");
        }
        if (report.price() == null || report.quantity() == null) {
            failRecoveryIdentityMismatch(order, null, "REPORT_PRICE_OR_QUANTITY_MISSING");
        }
    }

    private void validateTradeVenueReportIdentity(
            OrderRecord order,
            PaperTradeRecord trade,
            AdapterTradeReport report
    ) {
        validateVenueReportOrderIdentity(order, report);
        if (!Objects.equals(trade.exchange(), report.exchangeCode())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_REPORT_EXCHANGE_MISMATCH");
        }
        if (!Objects.equals(trade.exchangeTradeId(), report.exchangeTradeId())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_REPORT_ID_MISMATCH");
        }
        if (!Objects.equals(trade.externalOrderId(), report.exchangeOrderId())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_REPORT_EXTERNAL_ORDER_ID_MISMATCH");
        }
        if (!Objects.equals(trade.symbol(), report.symbol())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_REPORT_SYMBOL_MISMATCH");
        }
        if (trade.price().compareTo(report.price()) != 0) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_REPORT_PRICE_MISMATCH");
        }
        if (trade.qty().compareTo(report.quantity()) != 0) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_REPORT_QUANTITY_MISMATCH");
        }
        if (trade.fee() == null || trade.fee().compareTo(normalizedFee(report)) != 0
                || !Objects.equals(trade.feeCurrency(), report.feeAsset())) {
            failRecoveryIdentityMismatch(order, trade, "TRADE_REPORT_FEE_MISMATCH");
        }
    }

    private java.math.BigDecimal normalizedFee(AdapterTradeReport report) {
        return report.fee() == null ? java.math.BigDecimal.ZERO : report.fee().abs();
    }

    private void failRecoveryIdentityMismatch(OrderRecord order, PaperTradeRecord trade, String reason) {
        auditLogRepository.append(
                "RECONCILE",
                "OKX_LEDGER_RECOVERY_IDENTITY_MISMATCH",
                order.orderId(),
                order.traceId(),
                java.util.Map.of(
                        "order_id", order.orderId(),
                        "trade_id", trade == null ? "UNKNOWN" : String.valueOf(trade.tradeId()),
                        "reason", reason
                )
        );
        throw new IllegalStateException("ledger recovery identity mismatch: " + reason);
    }

    private void auditRecoveryBoundaryFailure(OrderRecord order, String reason) {
        auditLogRepository.append(
                "RECONCILE",
                "OKX_LEDGER_RECOVERY_INCOMPLETE",
                order.orderId(),
                order.traceId(),
                java.util.Map.of("order_id", order.orderId(), "reason", reason)
        );
    }

    private LedgerPostingResult postLedger(OrderRecord order, PaperTradeRecord trade, boolean recovery) {
        LedgerPostingResult postingResult;
        try {
            postingResult = tradeLedgerGateway.postTrade(new TradeLedgerRequest(
                    trade.tradeId(),
                    trade.orderId(),
                    trade.accountId(),
                    trade.symbol(),
                    OrderSide.valueOf(order.side()),
                    trade.price(),
                    trade.qty(),
                    trade.fee(),
                    trade.feeCurrency(),
                    trade.traceId(),
                    trade.ts()
            ));
        } catch (RuntimeException ex) {
            auditLedgerFailure(order, trade.tradeId(), ex.getClass().getSimpleName());
            throw ex;
        }
        if (!postingResult.posted()) {
            observation.record(OKX_RECONCILE, UNRESOLVED, 1);
            auditLedgerFailure(order, trade.tradeId(), postingResult.reason());
        } else if (recovery && !postingResult.idempotentHit()) {
            auditLogRepository.append(
                    "RECONCILE",
                    "OKX_LEDGER_RECOVERY_COMPLETED",
                    order.orderId(),
                    order.traceId(),
                    java.util.Map.of("trade_id", trade.tradeId(), "order_id", order.orderId())
            );
        }
        return postingResult;
    }

    private void auditLedgerFailure(OrderRecord order, String tradeId, String reason) {
        auditLogRepository.append(
                "RECONCILE",
                "OKX_LEDGER_POST_FAILED",
                order.orderId(),
                order.traceId(),
                java.util.Map.of("trade_id", tradeId, "reason", reason)
        );
    }

}
