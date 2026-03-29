package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterTradeReport;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.event.TradeExecuted;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.OrderLifecycleService;
import com.guidinglight.nexusquant.trading.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.ledger.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BinanceRestReconcileService 负责 GateC-2 的 Binance REST-only 同步器。
 * <p>
 * Why:
 * Binance 在 GateC-2 仍然坚持 REST-first：订单终态、成交同步、账本触发都以 REST query + myTrades 为事实来源。
 * 该服务复用 core 状态机与 ledger 幂等底座，但把 Binance 特有的 `myTrades/orderId/clientOrderId` 方言限制在
 * adapter-binance 与本服务内，避免 core/ledger/risk 出现 venue 分支。
 */
@Component
public class BinanceRestReconcileService {

    private static final Logger log = LoggerFactory.getLogger(BinanceRestReconcileService.class);
    private static final String SOURCE = "nq-scheduler.binance-rest-reconcile";
    private static final String EXCHANGE = "BINANCE";
    private static final int DEFAULT_LIMIT = 100;

    private final OrderCommandService orderCommandService;
    private final OrderLifecycleService orderLifecycleService;
    private final BinanceExchangeAdapter binanceExchangeAdapter;
    private final TradeRepository tradeRepository;
    private final TradeLedgerGateway tradeLedgerGateway;
    private final EventPublisherPort eventPublisherPort;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /**
     * @param orderCommandService    订单查询与 external_order_id 绑定服务
     * @param orderLifecycleService  订单生命周期入口
     * @param binanceExchangeAdapter Binance adapter
     * @param tradeRepository        trades 仓储
     * @param tradeLedgerGateway     ledger 网关
     * @param eventStoreAppender     event_store 写入器
     * @param auditLogRepository     审计仓储
     */
    public BinanceRestReconcileService(
            OrderCommandService orderCommandService,
            OrderLifecycleService orderLifecycleService,
            BinanceExchangeAdapter binanceExchangeAdapter,
            TradeRepository tradeRepository,
            TradeLedgerGateway tradeLedgerGateway,
            EventPublisherPort eventPublisherPort,
            AuditLogRepository auditLogRepository
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.orderLifecycleService = Objects.requireNonNull(
                orderLifecycleService,
                "orderLifecycleService must not be null"
        );
        this.binanceExchangeAdapter = Objects.requireNonNull(
                binanceExchangeAdapter,
                "binanceExchangeAdapter must not be null"
        );
        this.tradeRepository = Objects.requireNonNull(tradeRepository, "tradeRepository must not be null");
        this.tradeLedgerGateway = Objects.requireNonNull(tradeLedgerGateway, "tradeLedgerGateway must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 定时执行 Binance REST reconcile。
     */
    @Scheduled(
            fixedDelayString = "${nq.binance.reconcile.fixed-delay-ms:5000}",
            initialDelayString = "${nq.binance.reconcile.initial-delay-ms:5000}"
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
                        OrderStatus.CANCEL_REJECTED
                ),
                limit
        )) {
            if (!EXCHANGE.equals(order.venue())) {
                continue;
            }
            try {
                newTrades += reconcileSingleOrder(order);
            } catch (Exception ex) {
                log.warn(
                        "binance_reconcile_order_failed trace_id={} account_id={} order_id={} client_order_id={} "
                                + "external_order_id={} reason={}",
                        order.traceId(),
                        order.accountId(),
                        order.orderId(),
                        order.clientOrderId(),
                        order.externalOrderId(),
                        ex.getMessage()
                );
                appendAuditEvidence(
                        order,
                        "BINANCE_RECONCILE_ORDER_FAILED",
                        "FAIL",
                        Map.of("reason", ex.getMessage())
                );
            }
        }
        return newTrades;
    }

    private int reconcileSingleOrder(OrderRecord currentOrder) {
        AdapterOrderSnapshot snapshot = binanceExchangeAdapter.getOrder(new AdapterOrderQuery(
                currentOrder.accountId(),
                currentOrder.venue(),
                currentOrder.symbol(),
                currentOrder.clientOrderId(),
                currentOrder.externalOrderId(),
                currentOrder.traceId()
        ));
        if (snapshot.deferred()) {
            log.debug(
                    "binance_reconcile_remote_order_deferred trace_id={} account_id={} order_id={} client_order_id={} "
                            + "exchange_order_id={} category={} error_code={}",
                    currentOrder.traceId(),
                    currentOrder.accountId(),
                    currentOrder.orderId(),
                    currentOrder.clientOrderId(),
                    currentOrder.externalOrderId(),
                    snapshot.resultCategory(),
                    snapshot.error() == null ? null : snapshot.error().code()
            );
            return 0;
        }
        if (snapshot.resultCategory() != AdapterResultCategory.SUCCESS) {
            throw new IllegalStateException(
                    "binance getOrder failed, category=" + snapshot.resultCategory()
                            + ", code=" + (snapshot.error() == null ? "UNKNOWN" : snapshot.error().code())
                            + ", reason=" + (snapshot.error() == null ? "unknown" : snapshot.error().message())
            );
        }
        OrderRecord updatedOrder = currentOrder;
        if (updatedOrder.externalOrderId() == null
                && snapshot.exchangeOrderId() != null
                && !snapshot.exchangeOrderId().isBlank()) {
            updatedOrder = orderCommandService.linkExternalOrderId(
                    updatedOrder.orderId(),
                    snapshot.exchangeOrderId(),
                    updatedOrder.traceId()
            );
        }
        alignOrderStatus(updatedOrder, snapshot.externalStatus(), updatedOrder.traceId());
        OrderRecord latestOrder = orderCommandService.findByOrderId(updatedOrder.orderId()).orElse(updatedOrder);
        return reconcileFills(latestOrder);
    }

    private void alignOrderStatus(OrderRecord order, String adapterStatus, String traceId) {
        OrderStatus currentStatus = order.status();
        OrderStatus targetStatus = toTargetStatus(adapterStatus);
        if (currentStatus == targetStatus || isTerminalStatus(currentStatus) || targetStatus == OrderStatus.SENT) {
            return;
        }
        if ((targetStatus == OrderStatus.ACCEPTED || targetStatus == OrderStatus.PARTIALLY_FILLED)
                && currentStatus == OrderStatus.CANCEL_REQUESTED) {
            orderLifecycleService.rejectCancel(order.orderId(), "RECONCILE_CANCEL_REJECTED", traceId);
            if (targetStatus == OrderStatus.ACCEPTED) {
                orderLifecycleService.acknowledge(order.orderId(), "RECONCILE_STATUS_ALIGN", traceId);
                return;
            }
            orderLifecycleService.acknowledge(order.orderId(), "RECONCILE_CONFIRM_ACCEPTED", traceId);
            orderLifecycleService.markPartiallyFilled(order.orderId(), "RECONCILE_PARTIAL_FILL", traceId);
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

    private int reconcileFills(OrderRecord order) {
        if (order.externalOrderId() == null || order.externalOrderId().isBlank()) {
            return 0;
        }
        int newTrades = 0;
        for (AdapterTradeReport tradeReport : binanceExchangeAdapter.listTradeReports(order.symbol(), order.externalOrderId(), order.traceId())) {
            if (tradeRepository.findByExchangeAndExchangeTradeId(EXCHANGE, tradeReport.exchangeTradeId()).isPresent()) {
                continue;
            }
            PaperTradeRecord trade = new PaperTradeRecord(
                    "trd-" + UUID.randomUUID(),
                    order.orderId(),
                    order.accountId(),
                    tradeReport.symbol(),
                    EXCHANGE,
                    tradeReport.exchangeOrderId(),
                    tradeReport.exchangeTradeId(),
                    tradeReport.price(),
                    tradeReport.quantity(),
                    tradeReport.fee(),
                    tradeReport.feeAsset(),
                    order.traceId(),
                    tradeReport.tradeTs()
            );
            try {
                tradeRepository.insert(trade);
            } catch (DuplicateKeyException duplicateKeyException) {
                appendAuditEvidence(
                        order,
                        "BINANCE_FILL_DEDUP_RACE",
                        "SKIP",
                        Map.of("exchange_trade_id", tradeReport.exchangeTradeId(), "reason", duplicateKeyException.getMessage())
                );
                continue;
            }
            publishTradeEvent(order, tradeReport, trade.tradeId());
            LedgerPostingResult postingResult = tradeLedgerGateway.postTrade(new TradeLedgerRequest(
                    trade.tradeId(),
                    order.orderId(),
                    order.accountId(),
                    tradeReport.symbol(),
                    OrderSide.valueOf(tradeReport.side()),
                    tradeReport.price(),
                    tradeReport.quantity(),
                    tradeReport.fee(),
                    tradeReport.feeAsset(),
                    order.traceId(),
                    tradeReport.tradeTs()
            ));
            if (!postingResult.posted()) {
                log.warn(
                        "binance_reconcile_ledger_post_failed trace_id={} account_id={} order_id={} trade_id={} "
                                + "client_order_id={} external_order_id={} reason={}",
                        order.traceId(),
                        order.accountId(),
                        order.orderId(),
                        trade.tradeId(),
                        order.clientOrderId(),
                        order.externalOrderId(),
                        postingResult.reason()
                );
                appendAuditEvidence(
                        order,
                        "BINANCE_LEDGER_POST_FAILED",
                        "FAIL",
                        Map.of("trade_id", trade.tradeId(), "reason", postingResult.reason())
                );
            }
            newTrades++;
        }
        return newTrades;
    }

    private void publishTradeEvent(OrderRecord order, AdapterTradeReport tradeReport, String tradeId) {
        TradeExecuted payload = new TradeExecuted(
                tradeId,
                order.orderId(),
                order.clientOrderId(),
                order.accountId(),
                tradeReport.symbol(),
                order.venue(),
                EXCHANGE,
                tradeReport.exchangeOrderId(),
                tradeReport.exchangeTradeId(),
                tradeReport.price(),
                tradeReport.quantity(),
                tradeReport.fee(),
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

    private void appendAuditEvidence(OrderRecord order, String action, String outcome, Map<String, Object> detail) {
        auditLogRepository.append("RECONCILE", action, order.orderId(), order.traceId(), detail);
        AuditRecorded payload = new AuditRecorded(
                "RECONCILE",
                action,
                order.orderId(),
                outcome,
                detail.toString(),
                Instant.now(clock)
        );
        EventEnvelope<AuditRecorded> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                payload.getClass().getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                order.traceId(),
                order.clientOrderId(),
                payload
        );
        eventPublisherPort.append(TopicNames.AUDIT_EVENT_V1, envelope);
    }

    private OrderStatus toTargetStatus(String status) {
        return switch (status == null ? "" : status.trim().toUpperCase()) {
            case "ACCEPTED" -> OrderStatus.ACCEPTED;
            case "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrderStatus.FILLED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            default -> OrderStatus.SENT;
        };
    }

    private boolean isTerminalStatus(OrderStatus status) {
        return status == OrderStatus.FILLED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED;
    }
}

