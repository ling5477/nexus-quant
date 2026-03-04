package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.OrderCommandService;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private final OrderCommandService orderCommandService;
    private final OkxExchangeAdapter okxExchangeAdapter;
    private final OkxRestReconcileService okxRestReconcileService;
    private final com.guidinglight.nexusquant.core.service.port.AuditLogRepository auditLogRepository;
    private final Clock clock;

    /**
     * @param orderCommandService     订单编排服务
     * @param okxExchangeAdapter      OKX adapter
     * @param okxRestReconcileService REST reconcile 服务
     * @param auditLogRepository      审计仓储
     */
    public OkxRecoveryService(
            OrderCommandService orderCommandService,
            OkxExchangeAdapter okxExchangeAdapter,
            OkxRestReconcileService okxRestReconcileService,
            com.guidinglight.nexusquant.core.service.port.AuditLogRepository auditLogRepository
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.okxExchangeAdapter = Objects.requireNonNull(okxExchangeAdapter, "okxExchangeAdapter must not be null");
        this.okxRestReconcileService = Objects.requireNonNull(
                okxRestReconcileService,
                "okxRestReconcileService must not be null"
        );
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
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
                List.of(OrderStatus.NEW, OrderStatus.RISK_PASSED, OrderStatus.SENT, OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED),
                DEFAULT_LIMIT
        ).stream().filter(order -> "OKX".equals(order.venue())).toList();
        long linkedCount = hydrateExternalOrderIds(candidates, traceId);
        int newTrades = okxRestReconcileService.reconcileOnce(DEFAULT_LIMIT);
        Instant finishedAt = Instant.now(clock);
        auditLogRepository.append(
                "RECOVERY",
                "OKX_RECOVERY_COMPLETED",
                traceId,
                traceId,
                Map.of(
                        "candidate_orders", candidates.size(),
                        "linked_external_order_ids", linkedCount,
                        "new_trades", newTrades,
                        "started_at", startedAt.toString(),
                        "finished_at", finishedAt.toString()
                )
        );
        return new RecoveryReport(startedAt, finishedAt, candidates.size(), newTrades, linkedCount, 0L, traceId);
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
}
