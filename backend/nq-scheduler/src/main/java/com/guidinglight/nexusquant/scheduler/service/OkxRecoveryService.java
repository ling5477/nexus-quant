package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxRuntimeConfig;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.application.RecoveryReport;
import com.guidinglight.nexusquant.trading.application.RecoveryService;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.guidinglight.nexusquant.trading.domain.TradingVenue;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

/**
 * OkxRecoveryService 负责 GateC-1 的 REST-only 恢复入口。
 * <p>
 * Why:
 * docs/gates/gate-c/RECOVERY_RUNBOOK.md 要求启动与定时恢复都先扫描本地非终态订单，
 * 再用 `orders-pending + getOrder + fills` 做 query-confirm，且绝不重复下单。
 * 这里复用 `OkxRestReconcileService`，把恢复行为收敛成同一条幂等路径。
 */
@Component
@Profile("!scoped-okx-private-readonly")
@ConditionalOnProperty(
        prefix = "nq.runtime.trading-components",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
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
    private final OkxExchangeAdapter okxExchangeAdapter;
    private final OkxRestReconcileService okxRestReconcileService;
    private final AuditLogRepository auditLogRepository;
    private final EventPublisherPort eventPublisherPort;
    private final boolean recoveryEnabled;
    private final Clock clock;

    /**
     * @param orderCommandService     订单编排服务
     * @param okxExchangeAdapter      OKX adapter
     * @param okxRestReconcileService REST reconcile 服务
     * @param auditLogRepository      审计仓储
     * @param eventPublisherPort      event_store 发布器
     * @param recoveryEnabled         恢复链开关；local 默认关闭，formal 环境默认开启
     */
    public OkxRecoveryService(
            OrderCommandService orderCommandService,
            OkxExchangeAdapter okxExchangeAdapter,
            OkxRestReconcileService okxRestReconcileService,
            AuditLogRepository auditLogRepository,
            EventPublisherPort eventPublisherPort,
            @Value("${nq.okx.recovery.enabled:true}") boolean recoveryEnabled
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.okxExchangeAdapter = Objects.requireNonNull(okxExchangeAdapter, "okxExchangeAdapter must not be null");
        this.okxRestReconcileService = Objects.requireNonNull(
                okxRestReconcileService,
                "okxRestReconcileService must not be null"
        );
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.recoveryEnabled = recoveryEnabled;
        this.clock = Clock.systemUTC();
    }

    /**
     * 上下文刷新后触发一次启动恢复。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        if (!recoveryEnabled) {
            // Why: local 验收首先要保证 nq-app 能启动到登录阶段；
            // 本地未显式开启恢复链时，跳过启动恢复比在 ContextRefreshed 阶段直接拖死应用更可审计也更安全。
            OkxRuntimeConfig runtimeConfig = OkxRuntimeConfig.fromSystemEnv();
            LoggerFactory.getLogger(OkxRecoveryService.class).warn(
                    "okx_recovery_startup_skipped reason=recovery_disabled configured_okx_env={} mapped_trade_env={}",
                    runtimeConfig.envName(),
                    runtimeConfig.simulatedTrading() ? "SIM" : "LIVE"
            );
            return;
        }
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
        if (!recoveryEnabled) {
            return;
        }
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
        ).stream().filter(order -> order.canonicalVenue() == TradingVenue.OKX).toList();
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
                            new AdapterOpenOrdersQuery(
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
     * 只有发送前撤销与终态能原子提交时才允许负面终态；可能已发送的订单保留未决事实。
     */
    private long resolveOrderNotFoundDuringQueryConfirm(List<OrderRecord> candidates, String traceId) {
        long resolvedCount = 0L;
        for (OrderRecord order : candidates) {
            if (!QUERY_CONFIRM_STATUSES.contains(order.status())) {
                continue;
            }
            AdapterOrderSnapshot snapshot = okxExchangeAdapter.getOrder(new AdapterOrderQuery(
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
                if (orderCommandService.finalizeOrdinaryNoOrder(order.orderId(), traceId)) {
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
        eventPublisherPort.append(TopicNames.AUDIT_EVENT_V1, envelope);
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
