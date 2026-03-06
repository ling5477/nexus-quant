package com.guidinglight.nexusquant.adapter.okx.service;

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
 * OkxWsEventMapper 负责把 OKX 私有 WS 消息映射为内部事件包。
 * <p>
 * Why:
 * PR-W2 要求 WS 只做“证据链入 event_store”，不能直接改业务表或推进状态机。
 * 因此映射器只产出 EventEnvelope + topic，不包含任何持久化动作。
 */
public class OkxWsEventMapper {

    private static final String SOURCE = "OKX_WS";
    private static final String VENUE = "OKX";

    private final Clock clock;

    /**
     * 默认构造器使用 UTC 时钟。
     */
    public OkxWsEventMapper() {
        this(Clock.systemUTC());
    }

    /**
     * 可测试构造器。
     */
    public OkxWsEventMapper(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 把 WS 业务消息映射为待入链事件列表。
     *
     * @param message WS 业务消息 DTO
     * @param traceId 本条 WS 消息 trace_id
     * @return 待写入 event_store 的事件列表
     */
    public List<MappedEvent> map(OkxWsBusinessMessage message, String traceId) {
        Objects.requireNonNull(message, "message must not be null");
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        String channel = normalize(message.channel());
        return switch (channel) {
            case "orders" -> mapOrders(message, traceId);
            case "account" -> mapAccount(message, traceId);
            case "positions", "balance_and_position" -> mapPositions(message, traceId, channel);
            default -> List.of();
        };
    }

    private List<MappedEvent> mapOrders(OkxWsBusinessMessage message, String traceId) {
        List<MappedEvent> events = new ArrayList<>();
        for (JsonNode item : message.dataItems()) {
            String state = normalize(text(item, "state"));
            String clientOrderId = nullable(text(item, "clOrdId", "clientOrderId"));
            String externalOrderId = nullable(text(item, "ordId", "orderId"));
            String key = resolveOrderKey(clientOrderId, externalOrderId);
            String symbol = nullable(text(item, "instId", "symbol"));
            Long accountId = parseLongOrNull(text(item, "uid", "acctId", "accountId"));
            Instant ts = parseTimestamp(item, "uTime", "cTime", "ts");
            String rejectCode = resolveRejectCode(item, message);
            String rejectReason = resolveRejectReason(item, message);
            boolean hasReject = isReject(rejectCode, rejectReason, state);
            boolean cancelContext = isCancelContext(item);

            if (("live".equals(state) || "effective".equals(state)) && !hasReject) {
                events.add(new MappedEvent(
                        TopicNames.ORDER_EVENT_V1,
                        buildEnvelope(traceId, key, new OrderAck(accountId, VENUE, clientOrderId, externalOrderId, "ACCEPTED", ts))
                ));
                continue;
            }
            if ("canceled".equals(state) || "cancelled".equals(state)) {
                if (hasReject) {
                    events.add(new MappedEvent(
                            TopicNames.ORDER_EVENT_V1,
                            buildEnvelope(
                                    traceId,
                                    key,
                                    new CancelReject(
                                            accountId,
                                            VENUE,
                                            clientOrderId,
                                            externalOrderId,
                                            coalesce(rejectCode, "OKX_WS_CANCEL_REJECT"),
                                            coalesce(rejectReason, "cancel rejected by OKX WS"),
                                            ts
                                    )
                            )
                    ));
                } else {
                    events.add(new MappedEvent(
                            TopicNames.ORDER_EVENT_V1,
                            buildEnvelope(traceId, key, new CancelAck(accountId, VENUE, clientOrderId, externalOrderId, "CANCELLED", ts))
                    ));
                }
                continue;
            }
            if ("order_failed".equals(state) || "rejected".equals(state) || hasReject) {
                if (cancelContext) {
                    events.add(new MappedEvent(
                            TopicNames.ORDER_EVENT_V1,
                            buildEnvelope(
                                    traceId,
                                    key,
                                    new CancelReject(
                                            accountId,
                                            VENUE,
                                            clientOrderId,
                                            externalOrderId,
                                            coalesce(rejectCode, "OKX_WS_CANCEL_REJECT"),
                                            coalesce(rejectReason, "cancel rejected by OKX WS"),
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
                                    new OrderReject(
                                            accountId,
                                            VENUE,
                                            clientOrderId,
                                            coalesce(rejectCode, "OKX_WS_ORDER_REJECT"),
                                            coalesce(rejectReason, "order rejected by OKX WS"),
                                            ts
                                    )
                            )
                    ));
                }
                continue;
            }
            if ("partially_filled".equals(state) || "filled".equals(state)) {
                String evidenceType = "filled".equals(state) ? "OrderFilled" : "OrderPartiallyFilled";
                events.add(new MappedEvent(
                        TopicNames.ORDER_EVENT_V1,
                        buildEnvelope(
                                traceId,
                                key,
                                new OrderStateEvidence(
                                        evidenceType,
                                        accountId,
                                        VENUE,
                                        symbol,
                                        clientOrderId,
                                        externalOrderId,
                                        state,
                                        SOURCE,
                                        ts
                                ),
                                evidenceType
                        )
                ));
            }
        }
        return events;
    }

    private List<MappedEvent> mapAccount(OkxWsBusinessMessage message, String traceId) {
        List<MappedEvent> events = new ArrayList<>();
        if (message.dataItems().isEmpty()) {
            return events;
        }
        for (JsonNode item : message.dataItems()) {
            String accountKey = resolveAccountKey(item);
            Instant ts = parseTimestamp(item, "uTime", "ts");
            AuditRecorded payload = new AuditRecorded(
                    "ACCOUNT",
                    "OKX_WS_ACCOUNT_SNAPSHOT",
                    accountKey,
                    "SUCCESS",
                    "source=OKX_WS,channel=account",
                    ts
            );
            events.add(new MappedEvent(
                    TopicNames.AUDIT_EVENT_V1,
                    buildEnvelope(traceId, accountKey, payload)
            ));
        }
        return events;
    }

    private List<MappedEvent> mapPositions(OkxWsBusinessMessage message, String traceId, String channel) {
        List<MappedEvent> events = new ArrayList<>();
        for (JsonNode rootItem : message.dataItems()) {
            List<JsonNode> positionItems = extractPositionItems(rootItem, channel);
            if (positionItems.isEmpty()) {
                continue;
            }
            String accountKey = resolveAccountKey(rootItem);
            for (JsonNode positionItem : positionItems) {
                String symbol = nullable(text(positionItem, "instId", "symbol", "ccy"));
                BigDecimal qty = parseDecimalOrNull(text(positionItem, "pos", "qty", "availPos"));
                Instant ts = parseTimestamp(positionItem, "uTime", "ts");
                events.add(new MappedEvent(
                        TopicNames.POSITION_EVENT_V1,
                        buildEnvelope(
                                traceId,
                                accountKey,
                                new PositionSnapshotEvidence(
                                        accountKey,
                                        VENUE,
                                        channel,
                                        symbol,
                                        qty,
                                        SOURCE,
                                        ts
                                )
                        )
                ));
            }
        }
        return events;
    }

    private List<JsonNode> extractPositionItems(JsonNode rootItem, String channel) {
        if ("positions".equals(channel)) {
            return List.of(rootItem);
        }
        JsonNode posData = rootItem.path("posData");
        if (!posData.isArray() || posData.isEmpty()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        posData.forEach(result::add);
        return result;
    }

    private EventEnvelope<?> buildEnvelope(String traceId, String key, Object payload) {
        return buildEnvelope(traceId, key, payload, payload.getClass().getSimpleName());
    }

    private EventEnvelope<?> buildEnvelope(String traceId, String key, Object payload, String type) {
        return new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                type,
                1,
                Instant.now(clock),
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
        return "OKX_WS_ORDER_UNKNOWN";
    }

    private String resolveAccountKey(JsonNode item) {
        String accountId = nullable(text(item, "uid", "acctId", "accountId"));
        if (accountId != null) {
            return accountId;
        }
        return "OKX|UNKNOWN_ACCOUNT";
    }

    private String resolveRejectCode(JsonNode item, OkxWsBusinessMessage message) {
        String itemCode = nullable(text(item, "sCode", "code", "failCode"));
        if (itemCode != null) {
            return itemCode;
        }
        String messageCode = message.code();
        return (messageCode == null || messageCode.isBlank()) ? null : messageCode;
    }

    private String resolveRejectReason(JsonNode item, OkxWsBusinessMessage message) {
        String itemReason = nullable(text(item, "sMsg", "msg", "failReason"));
        if (itemReason != null) {
            return itemReason;
        }
        String messageReason = message.msg();
        return (messageReason == null || messageReason.isBlank()) ? null : messageReason;
    }

    private boolean isReject(String rejectCode, String rejectReason, String state) {
        if (rejectCode != null && !"0".equals(rejectCode.trim())) {
            return true;
        }
        if (rejectReason != null && !rejectReason.isBlank()) {
            return true;
        }
        return "order_failed".equals(state) || "rejected".equals(state);
    }

    private boolean isCancelContext(JsonNode item) {
        if (nullable(text(item, "cancelSource", "cancelReason")) != null) {
            return true;
        }
        String execType = normalize(text(item, "execType", "type", "op"));
        return execType.contains("cancel");
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
        return (value == null || value.isBlank()) ? null : value;
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

    private Instant parseTimestamp(JsonNode item, String... fields) {
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
        return Instant.now(clock);
    }

    /**
     * MappedEvent 表示 mapper 输出的一条“待入链事件”。
     */
    public record MappedEvent(String topic, EventEnvelope<?> envelope) {

        /**
         * 构造时做基础校验，防止入链时出现空 topic 或空 envelope。
         */
        public MappedEvent {
            Objects.requireNonNull(envelope, "envelope must not be null");
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic must not be blank");
            }
        }
    }

    /**
     * OrderStateEvidence 用于记录 WS 的订单状态证据（非状态机驱动）。
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
     * PositionSnapshotEvidence 用于记录 WS 推送的仓位快照证据（非账本事实）。
     */
    public record PositionSnapshotEvidence(
            String accountKey,
            String venue,
            String channel,
            String symbol,
            BigDecimal qty,
            String source,
            Instant ts
    ) {
    }
}
