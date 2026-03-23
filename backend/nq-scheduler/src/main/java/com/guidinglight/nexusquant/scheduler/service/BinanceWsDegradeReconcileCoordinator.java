package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsConnectionListener;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BinanceWsDegradeReconcileCoordinator 负责 GateC-2.1 的 WS 断线降级策略。
 * <p>
 * Why:
 * Binance WS 只能加速不能替代事实源；一旦断线、listenKey 失效或重连持续失败，
 * 必须触发一次受限范围的 REST reconcile，并留下完整审计证据链。
 */
@Component
@ConditionalOnProperty(name = "nq.binance.ws.enabled", havingValue = "true")
public class BinanceWsDegradeReconcileCoordinator implements BinanceWsConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(BinanceWsDegradeReconcileCoordinator.class);
    private static final String SOURCE = "nq-scheduler.binance-ws-degrade";
    private static final String SUBJECT = "BINANCE_WS";

    private final BinanceRestReconcileService binanceRestReconcileService;
    private final AuditLogRepository auditLogRepository;
    private final EventStoreAppender eventStoreAppender;
    private final Clock clock;
    private final int reconcileLimit;
    private final long cooldownMs;
    private final int reconnectFailThreshold;
    private final AtomicLong nextAllowedEpochMs;

    /**
     * @param binanceWsClient             WS 客户端
     * @param binanceRestReconcileService REST reconcile 服务
     * @param auditLogRepository          审计仓储
     * @param eventStoreAppender          event_store 写入器
     * @param reconcileLimit              每次降级触发的非终态扫描上限
     * @param cooldownMs                  降级触发去抖窗口
     * @param reconnectFailThreshold      连续 connect_failed 触发 reconcile 的阈值
     */
    public BinanceWsDegradeReconcileCoordinator(
            BinanceWsClient binanceWsClient,
            BinanceRestReconcileService binanceRestReconcileService,
            AuditLogRepository auditLogRepository,
            EventStoreAppender eventStoreAppender,
            @Value("${nq.binance.ws.degrade.reconcile-limit:100}") int reconcileLimit,
            @Value("${nq.binance.ws.degrade.cooldown-ms:15000}") long cooldownMs,
            @Value("${nq.binance.ws.degrade.reconnect-fail-threshold:2}") int reconnectFailThreshold
    ) {
        Objects.requireNonNull(binanceWsClient, "binanceWsClient must not be null");
        this.binanceRestReconcileService = Objects.requireNonNull(
                binanceRestReconcileService,
                "binanceRestReconcileService must not be null"
        );
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.eventStoreAppender = Objects.requireNonNull(eventStoreAppender, "eventStoreAppender must not be null");
        this.clock = Clock.systemUTC();
        this.reconcileLimit = Math.max(1, reconcileLimit);
        this.cooldownMs = Math.max(0L, cooldownMs);
        this.reconnectFailThreshold = Math.max(1, reconnectFailThreshold);
        this.nextAllowedEpochMs = new AtomicLong(0L);
        binanceWsClient.addConnectionListener(this);
    }

    @Override
    public void onConnected(String traceId) {
        // Why:
        // 当前 Binance BW3 只需要在降级入口留痕与节流；连接恢复后的“重置状态”由 client 自身重连计数承担。
    }

    @Override
    public void onReconnected(String reason, String traceId) {
        log.debug("binance_ws_reconnected trace_id={} reason={}", traceId, reason);
    }

    @Override
    public void onDisconnected(String reason, int attempt, long delayMs, String traceId) {
        if (isReconnectFailure(reason) && attempt < reconnectFailThreshold) {
            log.debug(
                    "binance_ws_reconnect_failed_observed trace_id={} reason={} attempt={} delay_ms={} threshold={}",
                    traceId,
                    reason,
                    attempt,
                    delayMs,
                    reconnectFailThreshold
            );
            return;
        }
        String action = isReconnectFailure(reason)
                ? "BINANCE_WS_RECONNECT_FAILED_THRESHOLD"
                : "BINANCE_WS_DISCONNECTED";
        triggerReconcile(action, traceId, Map.of(
                "reason", reason,
                "attempt", attempt,
                "delay_ms", delayMs,
                "threshold", reconnectFailThreshold
        ));
    }

    @Override
    public void onListenKeyExpired(String errorCode, String reason, String traceId) {
        triggerReconcile("BINANCE_WS_LISTENKEY_EXPIRED", traceId, Map.of(
                "error_code", String.valueOf(errorCode),
                "reason", String.valueOf(reason)
        ));
    }

    private void triggerReconcile(String action, String traceId, Map<String, Object> detail) {
        long now = Instant.now(clock).toEpochMilli();
        long nextAllowed = nextAllowedEpochMs.get();
        if (now < nextAllowed) {
            log.debug(
                    "binance_ws_reconcile_degrade_skipped_cooldown trace_id={} action={} now={} cooldown_until={} detail={}",
                    traceId,
                    action,
                    now,
                    nextAllowed,
                    detail
            );
            return;
        }
        nextAllowedEpochMs.set(now + cooldownMs);
        MDC.put("trace_id", traceId);
        try {
            log.info(
                    "binance_ws_reconcile_degrade_triggered trace_id={} action={} reconcile_limit={} cooldown_ms={} detail={}",
                    traceId,
                    action,
                    reconcileLimit,
                    cooldownMs,
                    detail
            );
            appendAudit(traceId, action, "SUCCESS", Map.of(
                    "detail", detail.toString(),
                    "reconcile_limit", reconcileLimit,
                    "cooldown_ms", cooldownMs
            ));
            int newTrades = binanceRestReconcileService.reconcileOnce(reconcileLimit);
            log.info(
                    "binance_ws_reconcile_degrade_completed trace_id={} action={} reconcile_limit={} new_trades={}",
                    traceId,
                    action,
                    reconcileLimit,
                    newTrades
            );
            appendAudit(traceId, "BINANCE_WS_RECONCILE_DEGRADE_COMPLETED", "SUCCESS", Map.of(
                    "trigger_action", action,
                    "reconcile_limit", reconcileLimit,
                    "new_trades", newTrades
            ));
        } catch (Exception ex) {
            log.warn(
                    "binance_ws_reconcile_degrade_failed trace_id={} action={} reconcile_limit={} reason={}",
                    traceId,
                    action,
                    reconcileLimit,
                    ex.getMessage()
            );
            appendAudit(traceId, "BINANCE_WS_RECONCILE_DEGRADE_FAILED", "FAIL", Map.of(
                    "trigger_action", action,
                    "reconcile_limit", reconcileLimit,
                    "error", ex.getMessage()
            ));
        } finally {
            MDC.remove("trace_id");
        }
    }

    private boolean isReconnectFailure(String reason) {
        return "connect_failed".equals(reason) || "listenkey_create_failed".equals(reason);
    }

    private void appendAudit(String traceId, String action, String outcome, Map<String, Object> detail) {
        auditLogRepository.append("WS", action, SUBJECT, traceId, detail);
        AuditRecorded payload = new AuditRecorded(
                "WS",
                action,
                SUBJECT,
                outcome,
                detail.toString(),
                Instant.now(clock)
        );
        EventEnvelope<AuditRecorded> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                AuditRecorded.class.getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                traceId,
                SUBJECT,
                payload
        );
        eventStoreAppender.append(TopicNames.AUDIT_EVENT_V1, envelope);
    }
}
