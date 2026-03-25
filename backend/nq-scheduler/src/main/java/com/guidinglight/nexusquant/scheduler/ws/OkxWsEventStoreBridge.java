package com.guidinglight.nexusquant.scheduler.ws;

import com.guidinglight.nexusquant.adapter.okx.service.OkxWsBusinessMessage;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsEventMapper;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.scheduler.service.OkxWsOrderAccelerationService;

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
 * OkxWsEventStoreBridge 负责把 WS 业务消息映射后写入 event_store。
 * <p>
 * Why:
 * PR-W2 明确要求“只做事件映射与证据链入 event_store”，
 * 因此该桥接器只调用 mapper + EventPublisherPort，不允许碰 orders/trades/ledger/positions 业务表。
 */
@Component
@ConditionalOnProperty(name = "nq.okx.ws.enabled", havingValue = "true")
public class OkxWsEventStoreBridge {

    private static final Logger log = LoggerFactory.getLogger(OkxWsEventStoreBridge.class);
    private static final String SOURCE = "OKX_WS";

    private final OkxWsEventMapper mapper;
    private final EventPublisherPort eventPublisherPort;
    private final AuditLogRepository auditLogRepository;
    private final OkxWsOrderAccelerationService orderAccelerationService;
    private final Clock clock;

    /**
     * 在构造阶段注册 WS 业务消息回调。
     */
    public OkxWsEventStoreBridge(
            OkxWsClient okxWsClient,
            OkxWsEventMapper mapper,
            EventPublisherPort eventPublisherPort,
            AuditLogRepository auditLogRepository,
            OkxWsOrderAccelerationService orderAccelerationService
    ) {
        Objects.requireNonNull(okxWsClient, "okxWsClient must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.orderAccelerationService = Objects.requireNonNull(
                orderAccelerationService,
                "orderAccelerationService must not be null"
        );
        this.clock = Clock.systemUTC();
        okxWsClient.addBusinessMessageListener(this::handleBusinessMessage);
    }

    /**
     * 处理一条 WS 业务消息并入链。
     * <p>
     * Why:
     * 任何映射失败都必须留痕（audit_logs + audit.event.v1），禁止吞错无痕。
     */
    void handleBusinessMessage(OkxWsBusinessMessage message, String traceId) {
        MDC.put("trace_id", traceId);
        try {
            for (OkxWsEventMapper.MappedEvent mappedEvent : mapper.map(message, traceId)) {
                // Why:
                // PR-W3 要求先把 WS 证据写入 event_store，再做加速迁移，
                // 这样即使后续状态迁移失败，也能保留完整可回放证据链。
                eventPublisherPort.append(mappedEvent.topic(), mappedEvent.envelope());
                orderAccelerationService.accelerate(mappedEvent, traceId);
            }
        } catch (Exception ex) {
            appendMappingFailureAudit(message, traceId, ex);
            appendMappingFailureAuditEvent(message, traceId, ex);
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void appendMappingFailureAudit(OkxWsBusinessMessage message, String traceId, Exception ex) {
        auditLogRepository.append(
                "WS",
                "OKX_WS_EVENT_MAPPING_FAILED",
                message.channel(),
                traceId,
                Map.of(
                        "source", SOURCE,
                        "channel", String.valueOf(message.channel()),
                        "code", String.valueOf(message.code()),
                        "error", String.valueOf(ex.getMessage())
                )
        );
        log.warn(
                "okx_ws_event_mapping_failed trace_id={} channel={} code={} reason={}",
                traceId,
                message.channel(),
                message.code(),
                ex.getMessage()
        );
    }

    private void appendMappingFailureAuditEvent(OkxWsBusinessMessage message, String traceId, Exception ex) {
        AuditRecorded payload = new AuditRecorded(
                "WS",
                "OKX_WS_EVENT_MAPPING_FAILED",
                String.valueOf(message.channel()),
                "FAIL",
                "source=OKX_WS,channel=" + message.channel() + ",error=" + ex.getMessage(),
                Instant.now(clock)
        );
        EventEnvelope<AuditRecorded> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                AuditRecorded.class.getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                traceId,
                message.channel() == null || message.channel().isBlank() ? "OKX_WS_UNKNOWN" : message.channel(),
                payload
        );
        eventPublisherPort.append(TopicNames.AUDIT_EVENT_V1, envelope);
    }
}
