package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.model.AdapterTradeReport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.event.TradeExecuted;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.OrderLifecycleService;
import com.guidinglight.nexusquant.ledger.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OkxRestReconcileService 负责 GateC-1 的 REST-only 同步器。
 * <p>
 * Why:
 * WS 还未接入时，非终态订单只能靠 `getOrder + fills` 推进；
 * 但同步器仍必须复用 core 的状态机与 ledger 的幂等能力，不能直接改 orders 或直接写账本表。
 */
@Component
public class OkxRestReconcileService {

    private static final String SOURCE = "nq-scheduler.okx-rest-reconcile";
    private static final int DEFAULT_LIMIT = 100;

    private final OrderCommandService orderCommandService;
    private final OrderLifecycleService orderLifecycleService;
    private final OkxExchangeAdapter okxExchangeAdapter;
    private final TradeRepository tradeRepository;
    private final TradeLedgerGateway tradeLedgerGateway;
    private final EventPublisherPort eventPublisherPort;
    private final com.guidinglight.nexusquant.core.service.port.AuditLogRepository auditLogRepository;
    private final Clock clock;

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
            com.guidinglight.nexusquant.core.service.port.AuditLogRepository auditLogRepository
    ) {
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
     * @param limit 本次扫描上限
     * @return 本次新写入的 trade 数量
     */
    public int reconcileOnce(int limit) {
        int newTrades = 0;
        for (OrderRecord order : orderCommandService.findOrdersByStatuses(
                List.of(
                        OrderStatus.SENT,
                        OrderStatus.ACCEPTED,
                        OrderStatus.PARTIALLY_FILLED,
                        OrderStatus.CANCEL_REQUESTED,
                        OrderStatus.CANCEL_REJECTED,
                        OrderStatus.FILLED
                ),
                limit
        )) {
            if (!"OKX".equals(order.venue())) {
                continue;
            }
            if (order.status() == OrderStatus.FILLED) {
                if (!shouldBackfillFilledOrder(order)) {
                    continue;
                }
                newTrades += reconcileFilledOrder(order);
                continue;
            }
            newTrades += reconcileSingleOrder(order);
        }
        return newTrades;
    }

    /**
     * 仅对“订单已被对齐到终态，但成交事实仍未落库”的 OKX 样本执行补扫。
     * <p>
     * Why:
     * UseCase-B 真实盘样本证明，交易所可能先返回 FILLED，而同一轮 `listFills(...)` 仍拿不到 fill。
     * 若此时不继续补扫，该订单会因已终态而永久退出 reconcile/recovery 的扫描集合，导致 trades/ledger 永远缺失。
     */
    private boolean shouldBackfillFilledOrder(OrderRecord order) {
        if (order.externalOrderId() == null || order.externalOrderId().isBlank()) {
            return false;
        }
        return tradeRepository.findByOrderId(order.orderId()).isEmpty();
    }

    /**
     * 对已终态 FILLED、但仍缺 trade 事实的订单执行 fills 补扫。
     * <p>
     * Why:
     * 这里刻意不再走 `getOrder -> alignOrderStatus(...)`，因为订单已经是终态；
     * 本批的最小修复目标只是补齐 `fills -> trades -> ledger`，避免为了等 fills 再次改动终态推进时机。
     */
    private int reconcileFilledOrder(OrderRecord order) {
        int newTrades = reconcileFills(order);
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

    private int reconcileSingleOrder(OrderRecord currentOrder) {
        AdapterOrderSnapshot snapshot = okxExchangeAdapter.getOrder(new com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery(
                currentOrder.accountId(),
                currentOrder.venue(),
                currentOrder.symbol(),
                currentOrder.clientOrderId(),
                currentOrder.externalOrderId(),
                currentOrder.traceId()
        ));
        if (snapshot.resultCategory() == AdapterResultCategory.NOT_FOUND) {
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
        alignOrderStatus(updatedOrder, snapshot.externalStatus(), updatedOrder.traceId());
        updatedOrder = orderCommandService.findByOrderId(updatedOrder.orderId()).orElse(updatedOrder);
        int newTrades = reconcileFills(updatedOrder);
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

    private int reconcileFills(OrderRecord order) {
        if (order.externalOrderId() == null || order.externalOrderId().isBlank()) {
            return 0;
        }
        int newTrades = 0;
        for (AdapterTradeReport tradeReport : okxExchangeAdapter.listTradeReports(order.symbol(), order.externalOrderId(), order.traceId())) {
            if (tradeRepository.findByExchangeAndExchangeTradeId("OKX", tradeReport.exchangeTradeId()).isPresent()) {
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
            tradeRepository.insert(trade);
            publishTradeEvent(order, tradeReport, normalizedFee, trade.tradeId());
            LedgerPostingResult postingResult = tradeLedgerGateway.postTrade(new TradeLedgerRequest(
                    trade.tradeId(),
                    order.orderId(),
                    order.accountId(),
                    tradeReport.symbol(),
                    OrderSide.valueOf(tradeReport.side()),
                    tradeReport.price(),
                    tradeReport.quantity(),
                    normalizedFee,
                    tradeReport.feeAsset(),
                    order.traceId(),
                    tradeReport.tradeTs()
            ));
            if (!postingResult.posted()) {
                auditLogRepository.append(
                        "RECONCILE",
                        "OKX_LEDGER_POST_FAILED",
                        order.orderId(),
                        order.traceId(),
                        java.util.Map.of("trade_id", trade.tradeId(), "reason", postingResult.reason())
                );
            }
            newTrades++;
        }
        return newTrades;
    }

    private void publishTradeEvent(OrderRecord order, AdapterTradeReport tradeReport, java.math.BigDecimal normalizedFee, String tradeId) {
        TradeExecuted payload = new TradeExecuted(
                tradeId,
                order.orderId(),
                order.clientOrderId(),
                order.accountId(),
                tradeReport.symbol(),
                order.venue(),
                "OKX",
                tradeReport.exchangeOrderId(),
                tradeReport.exchangeTradeId(),
                tradeReport.price(),
                tradeReport.quantity(),
                normalizedFee,
                tradeReport.feeAsset(),
                tradeReport.tradeTs()
        );
        EventEnvelope<TradeExecuted> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                payload.getClass().getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                order.traceId(),
                order.clientOrderId(),
                payload
        );
        eventPublisherPort.append(TopicNames.TRADE_EVENT_V1, envelope);
    }
}
