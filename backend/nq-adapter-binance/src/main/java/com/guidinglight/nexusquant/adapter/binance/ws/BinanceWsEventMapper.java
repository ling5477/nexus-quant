package com.guidinglight.nexusquant.adapter.binance.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.CancelAck;
import com.guidinglight.nexusquant.contracts.event.CancelReject;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.OrderAck;
import com.guidinglight.nexusquant.contracts.event.OrderReject;
import com.guidinglight.nexusquant.contracts.event.TopicNames;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * BinanceWsEventMapper 负责把 Binance 用户数据流原始消息映射为标准事件包。
 * <p>
 * Why:
 * PR-BW2 只允许做“原始消息 -> EventEnvelope -> event_store”证据链，不允许推进状态机或写业务表。
 * 因此该 mapper 只产出 topic + envelope，不包含任何持久化或业务编排动作。
 */
public class BinanceWsEventMapper {

    private static final String SOURCE = "BINANCE_WS";
    private static final String VENUE = "BINANCE";
    private static final String UNKNOWN_ACCOUNT_KEY = "BINANCE|UNKNOWN_ACCOUNT";

    private final Clock clock;

    /**
     * 默认构造器使用 UTC 时钟。
     */
    public BinanceWsEventMapper() {
        this(Clock.systemUTC());
    }

    /**
     * 可测试构造器。
     */
    public BinanceWsEventMapper(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 把一条 Binance 原始消息映射为待入链事件。
     *
     * @param message Binance 原始消息
     * @param traceId 本条消息的新 trace_id
     * @return 待写入 event_store 的事件列表
     */
    public List<MappedEvent> map(BinanceWsRawMessage message, String traceId) {
        Objects.requireNonNull(message, "message must not be null");
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (message.payload() == null) {
            throw new IllegalArgumentException("message.payload must not be null");
        }
        String eventType = normalize(message.eventType());
        return switch (eventType) {
            case "executionreport" -> mapExecutionReport(message, traceId);
            case "outboundaccountposition" -> mapOutboundAccountPosition(message, traceId);
            case "balanceupdate" -> mapBalanceUpdate(message, traceId);
            default -> List.of();
        };
    }

    private List<MappedEvent> mapExecutionReport(BinanceWsRawMessage message, String traceId) {
        JsonNode payload = message.payload();
        String clientOrderId = nullable(text(payload, "c"));
        String originalClientOrderId = nullable(text(payload, "C"));
        String externalOrderId = nullable(text(payload, "i"));
        String key = resolveOrderKey(clientOrderId, externalOrderId);
        String executionType = normalize(text(payload, "x"));
        String orderStatus = normalize(text(payload, "X"));
        String symbol = nullable(text(payload, "s"));
        Instant ts = parseTimestamp(payload, message.receivedAt(), "E", "T", "O");
        String rejectCode = resolveRejectCode(payload);
        String rejectReason = resolveRejectReason(payload);
        Long accountId = parseLongOrNull(text(payload, "u", "U", "accountId"));
        boolean cancelContext = isCancelContext(clientOrderId, originalClientOrderId, executionType);

        List<MappedEvent> events = new ArrayList<>();
        if ("new".equals(executionType) && !"rejected".equals(orderStatus)) {
            events.add(new MappedEvent(
                    TopicNames.ORDER_EVENT_V1,
                    buildEnvelope(
                            traceId,
                            key,
                            ts,
                            new OrderAck(accountId, VENUE, clientOrderId, externalOrderId, "ACCEPTED", ts)
                    )
            ));
            return events;
        }
        if ("canceled".equals(executionType) || "canceled".equals(orderStatus) || "cancelled".equals(orderStatus)) {
            events.add(new MappedEvent(
                    TopicNames.ORDER_EVENT_V1,
                    buildEnvelope(
                            traceId,
                            key,
                            ts,
                            new CancelAck(accountId, VENUE, clientOrderId, externalOrderId, "CANCELLED", ts)
                    )
            ));
            return events;
        }
        if ("rejected".equals(executionType) || isReject(rejectCode, rejectReason, orderStatus)) {
            if (cancelContext) {
                events.add(new MappedEvent(
                        TopicNames.ORDER_EVENT_V1,
                        buildEnvelope(
                                traceId,
                                key,
                                ts,
                                new CancelReject(
                                        accountId,
                                        VENUE,
                                        clientOrderId,
                                        externalOrderId,
                                        coalesce(rejectCode, "BINANCE_WS_CANCEL_REJECT"),
                                        coalesce(rejectReason, "cancel rejected by Binance WS"),
                                        ts
                                )
                        )
                ));
            } else {
                events.add(new MappedEvent(
                        TopicNames.ORDER_EVENT_V1,
                        buildEnvelope(
                                traceId,
                                key,
                                ts,
                                new OrderReject(
                                        accountId,
                                        VENUE,
                                        clientOrderId,
                                        coalesce(rejectCode, "BINANCE_WS_ORDER_REJECT"),
                                        coalesce(rejectReason, "order rejected by Binance WS"),
                                        ts
                                )
                        )
                ));
            }
            return events;
        }
        if ("trade".equals(executionType) && "partially_filled".equals(orderStatus)) {
            events.add(new MappedEvent(
                    TopicNames.ORDER_EVENT_V1,
                    buildEnvelope(
                            traceId,
                            key,
                            ts,
                            new OrderStateEvidence(
                                    "OrderPartiallyFilled",
                                    accountId,
                                    VENUE,
                                    symbol,
                                    clientOrderId,
                                    externalOrderId,
                                    orderStatus,
                                    SOURCE,
                                    ts
                            ),
                            "OrderPartiallyFilled"
                    )
            ));
        } else if ("trade".equals(executionType) && "filled".equals(orderStatus)) {
            events.add(new MappedEvent(
                    TopicNames.ORDER_EVENT_V1,
                    buildEnvelope(
                            traceId,
                            key,
                            ts,
                            new OrderStateEvidence(
                                    "OrderFilled",
                                    accountId,
                                    VENUE,
                                    symbol,
                                    clientOrderId,
                                    externalOrderId,
                                    orderStatus,
                                    SOURCE,
                                    ts
                            ),
                            "OrderFilled"
                    )
            ));
        }
        return events;
    }

    private List<MappedEvent> mapOutboundAccountPosition(BinanceWsRawMessage message, String traceId) {
        JsonNode payload = message.payload();
        Instant ts = parseTimestamp(payload, message.receivedAt(), "u", "E");
        JsonNode balances = payload.path("B");
        if (!balances.isArray() || balances.isEmpty()) {
            return List.of();
        }
        List<MappedEvent> events = new ArrayList<>();
        String accountKey = UNKNOWN_ACCOUNT_KEY;
        for (JsonNode balance : balances) {
            events.add(new MappedEvent(
                    TopicNames.POSITION_EVENT_V1,
                    buildEnvelope(
                            traceId,
                            accountKey,
                            ts,
                            new AccountBalanceSnapshotEvidence(
                                    accountKey,
                                    VENUE,
                                    nullable(text(balance, "a")),
                                    parseDecimalOrNull(text(balance, "f")),
                                    parseDecimalOrNull(text(balance, "l")),
                                    SOURCE,
                                    ts
                            )
                    )
            ));
        }
        return events;
    }

    private List<MappedEvent> mapBalanceUpdate(BinanceWsRawMessage message, String traceId) {
        JsonNode payload = message.payload();
        Instant ts = parseTimestamp(payload, message.receivedAt(), "T", "E");
        String asset = nullable(text(payload, "a"));
        String accountKey = UNKNOWN_ACCOUNT_KEY;
        AuditRecorded auditRecorded = new AuditRecorded(
                "ACCOUNT",
                "BINANCE_WS_BALANCE_UPDATE",
                accountKey,
                "SUCCESS",
                "source=BINANCE_WS,asset=" + asset + ",delta=" + coalesce(nullable(text(payload, "d")), "0"),
                ts
        );
        return List.of(new MappedEvent(
                TopicNames.AUDIT_EVENT_V1,
                buildEnvelope(traceId, accountKey, ts, auditRecorded)
        ));
    }

    private EventEnvelope<?> buildEnvelope(String traceId, String key, Instant ts, Object payload) {
        return buildEnvelope(traceId, key, ts, payload, payload.getClass().getSimpleName());
    }

    private EventEnvelope<?> buildEnvelope(String traceId, String key, Instant ts, Object payload, String type) {
        return new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                type,
                1,
                ts,
                SOURCE,
                traceId,
                key,
                payload
        );
    }

    private String resolveOrderKey(String clientOrderId, String externalOrderId) {
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            return clientOrderId;
        }
        if (externalOrderId != null && !externalOrderId.isBlank()) {
            return externalOrderId;
        }
        return "BINANCE_WS_ORDER_UNKNOWN";
    }

    private boolean isCancelContext(String clientOrderId, String originalClientOrderId, String executionType) {
        if (originalClientOrderId != null && !originalClientOrderId.isBlank() && !"null".equalsIgnoreCase(originalClientOrderId)) {
            return true;
        }
        return executionType.contains("cancel") && clientOrderId != null;
    }

    private boolean isReject(String rejectCode, String rejectReason, String orderStatus) {
        if (rejectCode != null && !rejectCode.isBlank() && !"none".equalsIgnoreCase(rejectCode)) {
            return true;
        }
        if (rejectReason != null && !rejectReason.isBlank()) {
            return true;
        }
        return "rejected".equals(orderStatus);
    }

    private String resolveRejectCode(JsonNode payload) {
        String code = nullable(text(payload, "r"));
        if (code == null || "NONE".equalsIgnoreCase(code)) {
            return null;
        }
        return code;
    }

    private String resolveRejectReason(JsonNode payload) {
        String reason = nullable(text(payload, "r"));
        if (reason == null || "NONE".equalsIgnoreCase(reason)) {
            return null;
        }
        return reason;
    }

    private String text(JsonNode item, String... fields) {
        for (String field : fields) {
            JsonNode child = item.path(field);
            if (!child.isMissingNode() && !child.isNull()) {
                String value = child.asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String coalesce(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal parseDecimalOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Instant parseTimestamp(JsonNode item, Instant receivedAt, String... fields) {
        for (String field : fields) {
            String raw = text(item, field);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                return Instant.ofEpochMilli(Long.parseLong(raw));
            } catch (NumberFormatException ignored) {
                // ignore and try next
            }
        }
        return receivedAt == null ? Instant.now(clock) : receivedAt;
    }

    /**
     * MappedEvent 表示 mapper 输出的一条“待入链事件”。
     */
    public record MappedEvent(String topic, EventEnvelope<?> envelope) {

        public MappedEvent {
            Objects.requireNonNull(envelope, "envelope must not be null");
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic must not be blank");
            }
        }
    }

    /**
     * OrderStateEvidence 用于记录 Binance WS 的订单状态证据。
     * <p>
     * Why:
     * BW2 只允许保留“订单已部分成交/已成交”的外部证据，不允许落 trades 或推进状态机，因此必须用独立证据类型承载。
     */
    public record OrderStateEvidence(
            String evidenceType,
            Long accountId,
            String venue,
            String symbol,
            String clientOrderId,
            String externalOrderId,
            String state,
            String source,
            Instant ts
    ) {
    }

    /**
     * AccountBalanceSnapshotEvidence 用于记录账户余额快照证据。
     * <p>
     * Why:
     * outboundAccountPosition 是“账户余额快照”，不是本地 positions 事实；这里仅把外部快照写入 `position.event.v1` 供后续审查。
     */
    public record AccountBalanceSnapshotEvidence(
            String accountKey,
            String venue,
            String asset,
            BigDecimal free,
            BigDecimal locked,
            String source,
            Instant ts
    ) {
    }
}
