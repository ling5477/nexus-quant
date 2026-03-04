package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.model.OkxFillRecord;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.event.TradeExecuted;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
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
    private final OkxExchangeAdapter okxExchangeAdapter;
    private final TradeRepository tradeRepository;
    private final TradeLedgerGateway tradeLedgerGateway;
    private final EventStoreAppender eventStoreAppender;
    private final com.guidinglight.nexusquant.core.service.port.AuditLogRepository auditLogRepository;
    private final Clock clock;

    /**
     * @param orderCommandService 订单编排服务
     * @param okxExchangeAdapter  OKX adapter
     * @param tradeRepository     trades 仓储
     * @param tradeLedgerGateway  ledger 网关
     * @param eventStoreAppender  event_store 写入器
     * @param auditLogRepository  审计仓储
     */
    public OkxRestReconcileService(
            OrderCommandService orderCommandService,
            OkxExchangeAdapter okxExchangeAdapter,
            TradeRepository tradeRepository,
            TradeLedgerGateway tradeLedgerGateway,
            EventStoreAppender eventStoreAppender,
            com.guidinglight.nexusquant.core.service.port.AuditLogRepository auditLogRepository
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.okxExchangeAdapter = Objects.requireNonNull(okxExchangeAdapter, "okxExchangeAdapter must not be null");
        this.tradeRepository = Objects.requireNonNull(tradeRepository, "tradeRepository must not be null");
        this.tradeLedgerGateway = Objects.requireNonNull(tradeLedgerGateway, "tradeLedgerGateway must not be null");
        this.eventStoreAppender = Objects.requireNonNull(eventStoreAppender, "eventStoreAppender must not be null");
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
                List.of(OrderStatus.SENT, OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED, OrderStatus.CANCEL_REQUESTED),
                limit
        )) {
            if (!"OKX".equals(order.venue())) {
                continue;
            }
            newTrades += reconcileSingleOrder(order);
        }
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
        OrderRecord updatedOrder = currentOrder;
        if (updatedOrder.externalOrderId() == null && snapshot.externalOrderId() != null && !snapshot.externalOrderId().isBlank()) {
            updatedOrder = orderCommandService.linkExternalOrderId(
                    updatedOrder.orderId(),
                    snapshot.externalOrderId(),
                    updatedOrder.traceId()
            );
        }
        alignOrderStatus(updatedOrder, snapshot.status(), updatedOrder.traceId());
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
        OrderStatus currentStatus = order.status();
        OrderStatus targetStatus = OrderStatus.valueOf(targetStatusName);
        if (currentStatus == targetStatus) {
            return;
        }
        if (targetStatus == OrderStatus.PARTIALLY_FILLED && currentStatus == OrderStatus.SENT) {
            orderCommandService.transitionOrder(order.orderId(), OrderStatus.ACCEPTED, "RECONCILE_CONFIRM_ACCEPTED", traceId);
            orderCommandService.transitionOrder(order.orderId(), OrderStatus.PARTIALLY_FILLED, "RECONCILE_PARTIAL_FILL", traceId);
            return;
        }
        if (targetStatus == OrderStatus.CANCELLED && currentStatus != OrderStatus.CANCEL_REQUESTED) {
            orderCommandService.transitionOrder(order.orderId(), OrderStatus.CANCEL_REQUESTED, "RECONCILE_CANCEL_REQUESTED", traceId);
            orderCommandService.transitionOrder(order.orderId(), OrderStatus.CANCELLED, "RECONCILE_CANCELLED", traceId);
            return;
        }
        orderCommandService.transitionOrder(order.orderId(), targetStatus, "RECONCILE_STATUS_ALIGN", traceId);
    }

    private int reconcileFills(OrderRecord order) {
        if (order.externalOrderId() == null || order.externalOrderId().isBlank()) {
            return 0;
        }
        int newTrades = 0;
        for (OkxFillRecord fill : okxExchangeAdapter.listFills(order.symbol(), order.externalOrderId(), order.traceId())) {
            if (tradeRepository.findByExchangeAndExchangeTradeId("OKX", fill.exchangeTradeId()).isPresent()) {
                auditLogRepository.append(
                        "RECONCILE",
                        "OKX_FILL_DEDUP_HIT",
                        order.orderId(),
                        order.traceId(),
                        java.util.Map.of("exchange_trade_id", fill.exchangeTradeId(), "order_id", order.orderId())
                );
                continue;
            }
            // Why: OKX fills 的 fee 常以负数表示“扣减”，而账本入参要求传入非负费用金额。
            // 这里统一转成绝对值，保留“费用大小”语义，避免 reconcile 在成交已落库后因参数校验中断。
            java.math.BigDecimal normalizedFee = fill.fee() == null ? java.math.BigDecimal.ZERO : fill.fee().abs();
            PaperTradeRecord trade = new PaperTradeRecord(
                    "trd-" + UUID.randomUUID(),
                    order.orderId(),
                    order.accountId(),
                    fill.symbol(),
                    "OKX",
                    fill.exchangeTradeId(),
                    fill.price(),
                    fill.qty(),
                    normalizedFee,
                    fill.feeCurrency(),
                    order.traceId(),
                    fill.ts()
            );
            tradeRepository.insert(trade);
            publishTradeEvent(order, fill, normalizedFee, trade.tradeId());
            LedgerPostingResult postingResult = tradeLedgerGateway.postTrade(new TradeLedgerRequest(
                    trade.tradeId(),
                    order.orderId(),
                    order.accountId(),
                    fill.symbol(),
                    OrderSide.valueOf(fill.side()),
                    fill.price(),
                    fill.qty(),
                    normalizedFee,
                    fill.feeCurrency(),
                    order.traceId(),
                    fill.ts()
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

    private void publishTradeEvent(OrderRecord order, OkxFillRecord fill, java.math.BigDecimal normalizedFee, String tradeId) {
        TradeExecuted payload = new TradeExecuted(
                tradeId,
                order.orderId(),
                order.clientOrderId(),
                order.accountId(),
                fill.symbol(),
                order.venue(),
                "OKX",
                fill.externalOrderId(),
                fill.exchangeTradeId(),
                fill.price(),
                fill.qty(),
                normalizedFee,
                fill.feeCurrency(),
                fill.ts()
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
        eventStoreAppender.append(TopicNames.TRADE_EVENT_V1, envelope);
    }
}
