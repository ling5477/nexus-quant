package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsConnectionListener;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OkxWsDegradeReconcileCoordinator 负责 PR-W3 的 WS 断线降级策略。
 * <p>
 * Why:
 * WS 只能加速不能替代事实源；一旦连接异常，需要触发一次受限范围的 REST reconcile，
 * 并把降级原因写入 audit_logs + event_store，避免“静默自愈”不可审计。
 */
@Component
@ConditionalOnProperty(name = "nq.okx.ws.enabled", havingValue = "true")
public class OkxWsDegradeReconcileCoordinator implements OkxWsConnectionListener {

    private static final String SOURCE = "nq-scheduler.okx-ws-degrade";

    private final OkxRestReconcileService okxRestReconcileService;
    private final AuditLogRepository auditLogRepository;
    private final EventPublisherPort eventPublisherPort;
    private final Clock clock;
    private final int reconcileLimit;
    private final long cooldownMs;
    private final int subscribeFailThreshold;
    private final AtomicLong nextAllowedEpochMs;
    private final AtomicInteger subscribeFailureStreak;

    /**
     * @param okxWsClient             WS 客户端
     * @param okxRestReconcileService REST reconcile 服务
     * @param auditLogRepository      审计仓储
     * @param eventPublisherPort      事件事实链追加端口
     * @param reconcileLimit          每次降级触发的非终态扫描上限
     * @param cooldownMs              降级触发去抖窗口
     * @param subscribeFailThreshold  连续订阅失败阈值
     */
    public OkxWsDegradeReconcileCoordinator(
            OkxWsClient okxWsClient,
            OkxRestReconcileService okxRestReconcileService,
            AuditLogRepository auditLogRepository,
            EventPublisherPort eventPublisherPort,
            @Value("${nq.okx.ws.degrade.reconcile-limit:100}") int reconcileLimit,
            @Value("${nq.okx.ws.degrade.cooldown-ms:15000}") long cooldownMs,
            @Value("${nq.okx.ws.degrade.subscribe-fail-threshold:3}") int subscribeFailThreshold
    ) {
        Objects.requireNonNull(okxWsClient, "okxWsClient must not be null");
        this.okxRestReconcileService = Objects.requireNonNull(
                okxRestReconcileService,
                "okxRestReconcileService must not be null"
        );
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.clock = Clock.systemUTC();
        this.reconcileLimit = Math.max(1, reconcileLimit);
        this.cooldownMs = Math.max(0L, cooldownMs);
        this.subscribeFailThreshold = Math.max(1, subscribeFailThreshold);
        this.nextAllowedEpochMs = new AtomicLong(0L);
        this.subscribeFailureStreak = new AtomicInteger(0);
        okxWsClient.addConnectionListener(this);
    }

    @Override
    public void onConnected(String traceId) {
        subscribeFailureStreak.set(0);
    }

    @Override
    public void onReconnectScheduled(String reason, int attempt, long delayMs, String traceId) {
        triggerReconcile("WS_RECONNECT_SCHEDULED", traceId, Map.of(
                "reason", String.valueOf(reason),
                "attempt", attempt,
                "delay_ms", delayMs
        ));
    }

    @Override
    public void onSubscribeFailed(String channel, String code, String message, String traceId) {
        int streak = subscribeFailureStreak.incrementAndGet();
        if (streak < subscribeFailThreshold) {
            appendAudit(traceId, "WS_SUBSCRIBE_FAILED_OBSERVED", "SUCCESS", Map.of(
                    "channel", String.valueOf(channel),
                    "code", String.valueOf(code),
                    "message", String.valueOf(message),
                    "streak", streak,
                    "threshold", subscribeFailThreshold
            ));
            return;
        }
        subscribeFailureStreak.set(0);
        triggerReconcile("WS_SUBSCRIBE_FAILED_THRESHOLD", traceId, Map.of(
                "channel", String.valueOf(channel),
                "code", String.valueOf(code),
                "message", String.valueOf(message),
                "threshold", subscribeFailThreshold
        ));
    }

    private void triggerReconcile(String action, String traceId, Map<String, Object> detail) {
        long now = Instant.now(clock).toEpochMilli();
        long nextAllowed = nextAllowedEpochMs.get();
        if (now < nextAllowed) {
            appendAudit(traceId, "WS_RECONCILE_DEGRADE_SKIPPED_COOLDOWN", "SUCCESS", Map.of(
                    "action", action,
                    "cooldown_until", nextAllowed,
                    "now", now,
                    "detail", detail.toString()
            ));
            return;
        }
        nextAllowedEpochMs.set(now + cooldownMs);
        MDC.put("trace_id", traceId);
        try {
            appendAudit(traceId, action, "SUCCESS", Map.of(
                    "detail", detail.toString(),
                    "reconcile_limit", reconcileLimit,
                    "cooldown_ms", cooldownMs
            ));
            int newTrades = okxRestReconcileService.reconcileOnce(reconcileLimit);
            appendAudit(traceId, "WS_RECONCILE_DEGRADE_COMPLETED", "SUCCESS", Map.of(
                    "trigger_action", action,
                    "reconcile_limit", reconcileLimit,
                    "new_trades", newTrades
            ));
        } catch (Exception ex) {
            appendAudit(traceId, "WS_RECONCILE_DEGRADE_FAILED", "FAIL", Map.of(
                    "trigger_action", action,
                    "reconcile_limit", reconcileLimit,
                    "error", ex.getMessage()
            ));
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void appendAudit(String traceId, String action, String outcome, Map<String, Object> detail) {
        String subject = "OKX_WS";
        auditLogRepository.append("WS", action, subject, traceId, detail);
        AuditRecorded payload = new AuditRecorded(
                "WS",
                action,
                subject,
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
                subject,
                payload
        );
        eventPublisherPort.append(TopicNames.AUDIT_EVENT_V1, envelope);
    }
}
