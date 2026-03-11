package com.guidinglight.nexusquant.app.ws;

import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsEventMapper;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsRawMessage;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
import com.guidinglight.nexusquant.scheduler.service.BinanceWsOrderAccelerationService;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BinanceWsEventStoreBridge 负责把 Binance WS 原始消息映射后写入 event_store。
 * <p>
 * Why:
 * PR-BW2 明确要求“只做事件映射与证据链入 event_store”，
 * 因此该桥接器只调用 mapper + EventStoreAppender，不允许碰 orders/trades/ledger/positions 业务表。
 */
@Component
@ConditionalOnProperty(name = "nq.binance.ws.enabled", havingValue = "true")
public class BinanceWsEventStoreBridge {

    private static final Logger log = LoggerFactory.getLogger(BinanceWsEventStoreBridge.class);
    private static final String SOURCE = "BINANCE_WS";

    private final BinanceWsEventMapper mapper;
    private final EventStoreAppender eventStoreAppender;
    private final AuditLogRepository auditLogRepository;
    private final BinanceWsOrderAccelerationService orderAccelerationService;
    private final Clock clock;

    /**
     * 在构造阶段注册 WS 原始消息回调。
     */
    public BinanceWsEventStoreBridge(
            BinanceWsClient binanceWsClient,
            BinanceWsEventMapper mapper,
            EventStoreAppender eventStoreAppender,
            AuditLogRepository auditLogRepository,
            BinanceWsOrderAccelerationService orderAccelerationService
    ) {
        Objects.requireNonNull(binanceWsClient, "binanceWsClient must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.eventStoreAppender = Objects.requireNonNull(eventStoreAppender, "eventStoreAppender must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.orderAccelerationService = Objects.requireNonNull(
                orderAccelerationService,
                "orderAccelerationService must not be null"
        );
        this.clock = Clock.systemUTC();
        binanceWsClient.addRawMessageListener(this::handleRawMessage);
    }

    /**
     * 处理一条 Binance 原始 WS 消息并入链。
     * <p>
     * Why:
     * 解析失败与映射失败都必须同时留下 `audit_logs` 与 `audit.event.v1` 证据，禁止静默吞错。
     */
    void handleRawMessage(BinanceWsRawMessage message) {
        String traceId = "trc-binance-ws-" + UUID.randomUUID();
        MDC.put("trace_id", traceId);
        try {
            if (message.payload() == null) {
                IllegalArgumentException parseFailure = new IllegalArgumentException("binance ws payload parse failed");
                appendFailureAudit("BINANCE_WS_PARSE_FAILED", message, traceId, parseFailure);
                appendFailureAuditEvent("BINANCE_WS_PARSE_FAILED", message, traceId, parseFailure);
                return;
            }
            for (BinanceWsEventMapper.MappedEvent mappedEvent : mapper.map(message, traceId)) {
                // Why:
                // BW3 要求先保留 WS 证据链，再做状态机加速；
                // 这样即使后续迁移被幂等或终态保护拦下，event_store 里仍有完整原始事实。
                eventStoreAppender.append(mappedEvent.topic(), mappedEvent.envelope());
                orderAccelerationService.accelerate(mappedEvent, traceId);
            }
        } catch (Exception ex) {
            appendFailureAudit("BINANCE_WS_EVENT_MAPPING_FAILED", message, traceId, ex);
            appendFailureAuditEvent("BINANCE_WS_EVENT_MAPPING_FAILED", message, traceId, ex);
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void appendFailureAudit(String action, BinanceWsRawMessage message, String traceId, Exception ex) {
        auditLogRepository.append(
                "WS",
                action,
                message.eventType(),
                traceId,
                Map.of(
                        "source", SOURCE,
                        "event_type", String.valueOf(message.eventType()),
                        "raw_payload", String.valueOf(message.rawPayload()),
                        "error", String.valueOf(ex.getMessage())
                )
        );
        log.warn(
                "binance_ws_event_mapping_failed trace_id={} event_type={} reason={}",
                traceId,
                message.eventType(),
                ex.getMessage()
        );
    }

    private void appendFailureAuditEvent(String action, BinanceWsRawMessage message, String traceId, Exception ex) {
        Instant ts = message.receivedAt() == null ? Instant.now(clock) : message.receivedAt();
        String eventType = normalizedEventType(message.eventType());
        AuditRecorded payload = new AuditRecorded(
                "WS",
                action,
                eventType,
                "FAIL",
                "source=BINANCE_WS,event_type=" + message.eventType() + ",error=" + ex.getMessage(),
                ts
        );
        EventEnvelope<AuditRecorded> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                AuditRecorded.class.getSimpleName(),
                1,
                ts,
                SOURCE,
                traceId,
                "UNKNOWN".equals(eventType) ? "BINANCE_WS_UNKNOWN" : eventType,
                payload
        );
        eventStoreAppender.append(TopicNames.AUDIT_EVENT_V1, envelope);
    }

    private String normalizedEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "UNKNOWN" : eventType;
    }
}
