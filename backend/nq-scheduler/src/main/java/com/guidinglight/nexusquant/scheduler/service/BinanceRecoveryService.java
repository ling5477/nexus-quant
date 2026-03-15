package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * BinanceRecoveryService 提供 GateD 当前阶段的 Binance 手动 recovery 入口。
 * <p>
 * Why:
 * UC-D10 需要单独观察 Binance `recovery -> reconcile` 的最小闭环，但当前共享 `RecoveryService`
 * 仍保留历史 OKX 语义；若直接复用共享入口，会把 OKX 主链一并拉进本批验收。
 * 因此这里提供一个只面向 Binance 的手动 recovery 服务：
 * 1) 先扫描 Binance 非终态订单；
 * 2) 用 open orders 回填缺失的 external_order_id；
 * 3) 再复用既有 `BinanceRestReconcileService` 收敛状态与 fills。
 * <p>
 * 当前明确只支持手动 runOnce，不引入新的启动/定时副作用。
 */
@Component
public class BinanceRecoveryService {

    private static final int DEFAULT_LIMIT = 500;
    private static final String VENUE = "BINANCE";
    private static final String SOURCE = "nq-scheduler.binance-recovery";

    private final OrderCommandService orderCommandService;
    private final BinanceExchangeAdapter binanceExchangeAdapter;
    private final BinanceRestReconcileService binanceRestReconcileService;
    private final AuditLogRepository auditLogRepository;
    private final EventStoreAppender eventStoreAppender;
    private final Clock clock;

    /**
     * @param orderCommandService        订单查询与 external_order_id 回填入口
     * @param binanceExchangeAdapter     Binance adapter
     * @param binanceRestReconcileService Binance REST reconcile 服务
     * @param auditLogRepository         审计仓储
     * @param eventStoreAppender         event_store 写入器
     */
    public BinanceRecoveryService(
            OrderCommandService orderCommandService,
            BinanceExchangeAdapter binanceExchangeAdapter,
            BinanceRestReconcileService binanceRestReconcileService,
            AuditLogRepository auditLogRepository,
            EventStoreAppender eventStoreAppender
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.binanceExchangeAdapter = Objects.requireNonNull(
                binanceExchangeAdapter,
                "binanceExchangeAdapter must not be null"
        );
        this.binanceRestReconcileService = Objects.requireNonNull(
                binanceRestReconcileService,
                "binanceRestReconcileService must not be null"
        );
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.eventStoreAppender = Objects.requireNonNull(eventStoreAppender, "eventStoreAppender must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 执行一次 Binance recovery。
     * <p>
     * Why:
     * Binance 当前阶段的恢复目标不是“重新下单”，而是把本地已存在的非终态订单重新对齐到外部事实：
     * 缺 external_order_id 就先回填，随后统一交给 reconcile 做状态与成交同步。
     *
     * @param traceId 本次恢复任务 traceId
     * @return 恢复报告
     */
    public RecoveryReport rebuild(String traceId) {
        Instant startedAt = Instant.now(clock);
        List<OrderRecord> candidates = orderCommandService.findOrdersByStatuses(
                List.of(
                        OrderStatus.SENT,
                        OrderStatus.ACCEPTED,
                        OrderStatus.PARTIALLY_FILLED,
                        OrderStatus.CANCEL_REQUESTED,
                        OrderStatus.CANCEL_REJECTED
                ),
                DEFAULT_LIMIT
        ).stream().filter(order -> VENUE.equals(order.venue())).toList();
        long linkedCount = hydrateExternalOrderIds(candidates, traceId);
        int newTrades = binanceRestReconcileService.reconcileOnce(DEFAULT_LIMIT);
        Instant finishedAt = Instant.now(clock);
        Map<String, Object> detail = Map.of(
                "venue", VENUE,
                "candidate_orders", candidates.size(),
                "linked_external_order_ids", linkedCount,
                "new_trades", newTrades,
                "started_at", startedAt.toString(),
                "finished_at", finishedAt.toString()
        );
        appendCompletionAudit(traceId, detail);
        return new RecoveryReport(startedAt, finishedAt, candidates.size(), newTrades, linkedCount, 0L, traceId);
    }

    private long hydrateExternalOrderIds(List<OrderRecord> candidates, String traceId) {
        long linkedCount = 0L;
        Map<String, List<AdapterOrderSnapshot>> openOrdersBySymbol = new LinkedHashMap<>();
        for (OrderRecord order : candidates) {
            if (order.externalOrderId() != null && !order.externalOrderId().isBlank()) {
                continue;
            }
            List<AdapterOrderSnapshot> openOrders = openOrdersBySymbol.computeIfAbsent(
                    order.symbol(),
                    symbol -> binanceExchangeAdapter.listOpenOrders(new AdapterOpenOrdersQuery(
                            order.accountId(),
                            order.venue(),
                            symbol,
                            traceId
                    ))
            );
            for (AdapterOrderSnapshot snapshot : openOrders) {
                if (order.clientOrderId().equals(snapshot.clientOrderId())
                        && snapshot.externalOrderId() != null
                        && !snapshot.externalOrderId().isBlank()) {
                    orderCommandService.linkExternalOrderId(order.orderId(), snapshot.externalOrderId(), traceId);
                    linkedCount++;
                    break;
                }
            }
        }
        return linkedCount;
    }

    private void appendCompletionAudit(String traceId, Map<String, Object> detail) {
        auditLogRepository.append("RECOVERY", "BINANCE_RECOVERY_COMPLETED", traceId, traceId, detail);
        AuditRecorded payload = new AuditRecorded(
                "RECOVERY",
                "BINANCE_RECOVERY_COMPLETED",
                traceId,
                "SUCCESS",
                detail.toString(),
                Instant.now(clock)
        );
        EventEnvelope<AuditRecorded> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                payload.getClass().getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                traceId,
                traceId,
                payload
        );
        eventStoreAppender.append(TopicNames.AUDIT_EVENT_V1, envelope);
    }
}
