package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.OrderStatusChangedPayload;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.OrderLifecycleService;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OkxRecoveryService 负责 GateC-1 的 REST-only 恢复入口。
 * <p>
 * Why:
 * docs/gates/gate-c/RECOVERY_RUNBOOK.md 要求启动与定时恢复都先扫描本地非终态订单，
 * 再用 `orders-pending + getOrder + fills` 做 query-confirm，且绝不重复下单。
 * 这里复用 `OkxRestReconcileService`，把恢复行为收敛成同一条幂等路径。
 */
@Component
public class OkxRecoveryService implements RecoveryService {

    private static final int DEFAULT_LIMIT = 500;
    private static final String SOURCE = "nq-scheduler.okx-recovery";
    private static final String ORDER_NOT_FOUND_REASON = "ORDER_NOT_FOUND/OKX_51603";
    private static final Set<OrderStatus> QUERY_CONFIRM_STATUSES = Set.of(
            OrderStatus.SENT,
            OrderStatus.ACCEPTED,
            OrderStatus.PARTIALLY_FILLED,
            OrderStatus.CANCEL_REQUESTED,
            OrderStatus.CANCEL_REJECTED
    );

    private final OrderCommandService orderCommandService;
    private final OrderLifecycleService orderLifecycleService;
    private final OkxExchangeAdapter okxExchangeAdapter;
    private final OkxRestReconcileService okxRestReconcileService;
    private final com.guidinglight.nexusquant.core.service.port.AuditLogRepository auditLogRepository;
    private final EventStoreAppender eventStoreAppender;
    private final Clock clock;

    /**
     * @param orderCommandService     订单编排服务
     * @param orderLifecycleService   订单生命周期入口
     * @param okxExchangeAdapter      OKX adapter
     * @param okxRestReconcileService REST reconcile 服务
     * @param auditLogRepository      审计仓储
     * @param eventStoreAppender      event_store 写入器
     */
    public OkxRecoveryService(
            OrderCommandService orderCommandService,
            OrderLifecycleService orderLifecycleService,
            OkxExchangeAdapter okxExchangeAdapter,
            OkxRestReconcileService okxRestReconcileService,
            com.guidinglight.nexusquant.core.service.port.AuditLogRepository auditLogRepository,
            EventStoreAppender eventStoreAppender
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.orderLifecycleService = Objects.requireNonNull(orderLifecycleService, "orderLifecycleService must not be null");
        this.okxExchangeAdapter = Objects.requireNonNull(okxExchangeAdapter, "okxExchangeAdapter must not be null");
        this.okxRestReconcileService = Objects.requireNonNull(
                okxRestReconcileService,
                "okxRestReconcileService must not be null"
        );
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.eventStoreAppender = Objects.requireNonNull(eventStoreAppender, "eventStoreAppender must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 上下文刷新后触发一次启动恢复。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        rebuild("trc-okx-recovery-startup");
    }

    /**
     * 定时执行恢复，覆盖长时间运行中的未知状态窗口。
     */
    @Scheduled(
            fixedDelayString = "${nq.okx.recovery.fixed-delay-ms:15000}",
            initialDelayString = "${nq.okx.recovery.initial-delay-ms:15000}"
    )
    public void scheduledRecovery() {
        rebuild("trc-okx-recovery-scheduled");
    }

    @Override
    public RecoveryReport rebuild(String traceId) {
        Instant startedAt = Instant.now(clock);
        List<OrderRecord> candidates = orderCommandService.findOrdersByStatuses(
                List.of(
                        OrderStatus.NEW,
                        OrderStatus.RISK_PASSED,
                        OrderStatus.SENT,
                        OrderStatus.ACCEPTED,
                        OrderStatus.PARTIALLY_FILLED,
                        OrderStatus.CANCEL_REQUESTED,
                        OrderStatus.CANCEL_REJECTED
                ),
                DEFAULT_LIMIT
        ).stream().filter(order -> "OKX".equals(order.venue())).toList();
        long linkedCount = hydrateExternalOrderIds(candidates, traceId);
        long orderNotFoundResolved = resolveOrderNotFoundDuringQueryConfirm(candidates, traceId);
        int newTrades = safeReconcile(traceId);
        Instant finishedAt = Instant.now(clock);
        auditLogRepository.append(
                "RECOVERY",
                "OKX_RECOVERY_COMPLETED",
                traceId,
                traceId,
                Map.of(
                        "candidate_orders", candidates.size(),
                        "linked_external_order_ids", linkedCount,
                        "order_not_found_resolved", orderNotFoundResolved,
                        "new_trades", newTrades,
                        "started_at", startedAt.toString(),
                        "finished_at", finishedAt.toString()
                )
        );
        return new RecoveryReport(startedAt, finishedAt, candidates.size(), newTrades, linkedCount, orderNotFoundResolved, traceId);
    }

    private long hydrateExternalOrderIds(List<OrderRecord> candidates, String traceId) {
        long linkedCount = 0L;
        Map<String, List<AdapterOrderSnapshot>> openOrdersBySymbol = new LinkedHashMap<>();
        for (OrderRecord order : candidates) {
            List<AdapterOrderSnapshot> openOrders = openOrdersBySymbol.computeIfAbsent(
                    order.symbol(),
                    symbol -> okxExchangeAdapter.listOpenOrders(
                            new com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery(
                                    order.accountId(),
                                    order.venue(),
                                    symbol,
                                    traceId
                            )
                    )
            );
            if (order.externalOrderId() != null && !order.externalOrderId().isBlank()) {
                continue;
            }
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

    /**
     * 在恢复阶段执行 query-confirm，遇到 OKX 51603（订单不存在）时做可审计降级，不阻断启动。
     * <p>
     * Why:
     * 真实盘场景下，本地可能残留“非终态但交易所已不存在”的历史订单。
     * 这类订单不应让恢复 fail-fast；需要保留证据链后把本地状态推进到终态。
     */
    private long resolveOrderNotFoundDuringQueryConfirm(List<OrderRecord> candidates, String traceId) {
        long resolvedCount = 0L;
        for (OrderRecord order : candidates) {
            if (!QUERY_CONFIRM_STATUSES.contains(order.status())) {
                continue;
            }
            AdapterOrderSnapshot snapshot = okxExchangeAdapter.getOrder(new com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery(
                    order.accountId(),
                    order.venue(),
                    order.symbol(),
                    order.clientOrderId(),
                    order.externalOrderId(),
                    traceId
            ));
            if (snapshot.resultCategory() == AdapterResultCategory.NOT_FOUND) {
                appendOrderNotFoundAudit(order, traceId, snapshot.error() == null ? "51603" : snapshot.error().code());
                appendOrderNotFoundAuditEvent(order, traceId, snapshot.error() == null ? "51603" : snapshot.error().code());
                if (transitionToCancelled(order, traceId)) {
                    resolvedCount++;
                }
                continue;
            }
            if (snapshot.resultCategory() != AdapterResultCategory.SUCCESS) {
                throw new IllegalStateException(
                        "okx query-confirm failed, category=" + snapshot.resultCategory()
                                + ", code=" + (snapshot.error() == null ? "UNKNOWN" : snapshot.error().code())
                );
            }
        }
        return resolvedCount;
    }

    private boolean transitionToCancelled(OrderRecord order, String traceId) {
        try {
            OrderRecord snapshot = order;
            if (snapshot.status() != OrderStatus.CANCEL_REQUESTED && snapshot.status() != OrderStatus.CANCELLED) {
                snapshot = orderLifecycleService.requestCancel(
                        snapshot.orderId(),
                        ORDER_NOT_FOUND_REASON,
                        traceId
                );
                appendOrderStatusEvent(snapshot, traceId, ORDER_NOT_FOUND_REASON);
            }
            if (snapshot.status() != OrderStatus.CANCELLED) {
                snapshot = orderLifecycleService.cancel(
                        snapshot.orderId(),
                        ORDER_NOT_FOUND_REASON,
                        traceId
                );
                appendOrderStatusEvent(snapshot, traceId, ORDER_NOT_FOUND_REASON);
            }
            return true;
        } catch (IllegalStateException transitionEx) {
            auditLogRepository.append(
                    "RECOVERY",
                    "RECOVERY_QUERY_ORDER_NOT_FOUND_TRANSITION_FAILED",
                    order.orderId(),
                    traceId,
                    Map.of(
                            "order_id", order.orderId(),
                            "from_status", order.status().name(),
                            "target_status", OrderStatus.CANCELLED.name(),
                            "reason_code", ORDER_NOT_FOUND_REASON,
                            "error", transitionEx.getMessage()
                    )
            );
            return false;
        }
    }

    private int safeReconcile(String traceId) {
        return okxRestReconcileService.reconcileOnce(DEFAULT_LIMIT);
    }

    private void appendOrderNotFoundAudit(OrderRecord order, String traceId, String okxCode) {
        auditLogRepository.append(
                "RECOVERY",
                "RECOVERY_QUERY_ORDER_NOT_FOUND",
                order.orderId(),
                traceId,
                Map.of(
                        "account_id", order.accountId(),
                        "venue", order.venue(),
                        "symbol", order.symbol(),
                        "client_order_id", order.clientOrderId(),
                        "external_order_id", String.valueOf(order.externalOrderId()),
                        "trace_id", traceId,
                        "okx_code", String.valueOf(okxCode),
                        "reason_code", ORDER_NOT_FOUND_REASON
                )
        );
    }

    private void appendOrderNotFoundAuditEvent(OrderRecord order, String traceId, String okxCode) {
        RecoveryOrderNotFoundAuditPayload payload = new RecoveryOrderNotFoundAuditPayload(
                order.accountId(),
                order.venue(),
                order.symbol(),
                order.clientOrderId(),
                order.externalOrderId(),
                traceId,
                String.valueOf(okxCode),
                ORDER_NOT_FOUND_REASON,
                Instant.now(clock)
        );
        EventEnvelope<RecoveryOrderNotFoundAuditPayload> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                payload.getClass().getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                traceId,
                order.clientOrderId(),
                payload
        );
        eventStoreAppender.append(TopicNames.AUDIT_EVENT_V1, envelope);
    }

    private void appendOrderStatusEvent(OrderRecord order, String traceId, String reason) {
        EventEnvelope<OrderStatusChangedPayload> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                OrderStatusChangedPayload.class.getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                traceId,
                order.clientOrderId(),
                new OrderStatusChangedPayload(
                        order.orderId(),
                        order.accountId(),
                        order.clientOrderId(),
                        order.status(),
                        reason,
                        Instant.now(clock)
                )
        );
        eventStoreAppender.append(TopicNames.ORDER_EVENT_V1, envelope);
    }

    private record RecoveryOrderNotFoundAuditPayload(
            Long accountId,
            String venue,
            String symbol,
            String clientOrderId,
            String externalOrderId,
            String traceId,
            String okxCode,
            String reasonCode,
            Instant occurredAt
    ) {
    }
}
