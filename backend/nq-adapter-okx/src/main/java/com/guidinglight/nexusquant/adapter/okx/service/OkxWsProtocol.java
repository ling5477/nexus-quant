package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OkxWsProtocol 负责 WS 报文构造与最小消息解析。
 * <p>
 * Why:
 * PR-W1/W2 需要把“连接治理”和“消息映射”解耦：
 * 这里负责协议细节，业务映射交给 OkxWsEventMapper。
 */
final class OkxWsProtocol {

    private static final String WS_LOGIN_SIGN_PATH = "/users/self/verify";

    private OkxWsProtocol() {
    }

    static String buildSubscribeMessage(ObjectMapper mapper, List<OkxWsSubscription> subscriptions) {
        return buildOpMessage(mapper, "subscribe", subscriptions);
    }

    static String buildUnsubscribeMessage(ObjectMapper mapper, List<OkxWsSubscription> subscriptions) {
        return buildOpMessage(mapper, "unsubscribe", subscriptions);
    }

    static String buildLoginMessage(
            ObjectMapper mapper,
            OkxApiCredentials credentials,
            OkxRequestSigner signer,
            Clock clock
    ) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        Objects.requireNonNull(signer, "signer must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (!credentials.isConfigured()) {
            throw new IllegalArgumentException("OKX credentials are not fully configured");
        }
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        Map<String, String> signHeaders = signer.signHeaders(
                credentials,
                "GET",
                WS_LOGIN_SIGN_PATH,
                "",
                timestamp
        );
        Map<String, Object> message = Map.of(
                "op", "login",
                "args", List.of(Map.of(
                        "apiKey", credentials.apiKey(),
                        "passphrase", credentials.passphrase(),
                        "timestamp", timestamp,
                        "sign", signHeaders.get("OK-ACCESS-SIGN")
                ))
        );
        return toJson(mapper, message);
    }

    static ParsedMessage parseInboundMessage(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedMessage(MessageKind.UNKNOWN, "", "", "", "", false);
        }
        String normalized = raw.trim();
        if ("pong".equalsIgnoreCase(normalized)) {
            return new ParsedMessage(MessageKind.PONG, "", "", "", "", true);
        }
        try {
            JsonNode root = mapper.readTree(normalized);
            String event = root.path("event").asText("");
            String code = root.path("code").asText("");
            String msg = root.path("msg").asText("");
            String channel = root.path("arg").path("channel").asText("");
            if ("login".equals(event) && "0".equals(code)) {
                return new ParsedMessage(MessageKind.LOGIN_SUCCESS, event, code, msg, channel, false);
            }
            if ("login".equals(event) && !code.isBlank() && !"0".equals(code)) {
                return new ParsedMessage(MessageKind.LOGIN_FAILED, event, code, msg, channel, false);
            }
            if ("subscribe".equals(event) && "0".equals(code)) {
                return new ParsedMessage(MessageKind.SUBSCRIBE_SUCCESS, event, code, msg, channel, false);
            }
            if ("subscribe".equals(event) && !code.isBlank() && !"0".equals(code)) {
                return new ParsedMessage(MessageKind.SUBSCRIBE_FAILED, event, code, msg, channel, false);
            }
            return new ParsedMessage(MessageKind.BUSINESS_MESSAGE, event, code, msg, channel, false);
        } catch (Exception ex) {
            return new ParsedMessage(MessageKind.UNKNOWN, "", "", ex.getMessage(), "", false);
        }
    }

    /**
     * 从原始 WS 文本中提取可消费的业务消息。
     * <p>
     * Why:
     * W2 需要按 data 数组逐条入 event_store，不能把整包文本直接塞给上层做脆弱字符串解析。
     */
    static List<OkxWsBusinessMessage> extractBusinessMessages(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(raw);
            String channel = root.path("arg").path("channel").asText("");
            if (channel.isBlank()) {
                return List.of();
            }
            String event = root.path("event").asText("");
            String code = root.path("code").asText("");
            String msg = root.path("msg").asText("");
            JsonNode dataNode = root.path("data");
            List<JsonNode> dataItems = new ArrayList<>();
            if (dataNode.isArray()) {
                dataNode.forEach(dataItems::add);
            } else if (!dataNode.isMissingNode() && !dataNode.isNull()) {
                dataItems.add(dataNode);
            }
            return List.of(new OkxWsBusinessMessage(channel, event, code, msg, dataItems, raw));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static long reconnectDelayMs(int attempt, long baseDelayMs, long maxDelayMs) {
        if (attempt <= 0) {
            return baseDelayMs;
        }
        long candidate;
        if (attempt >= 31) {
            candidate = maxDelayMs;
        } else {
            candidate = baseDelayMs * (1L << (attempt - 1));
        }
        return Math.min(candidate, maxDelayMs);
    }

    private static String buildOpMessage(ObjectMapper mapper, String op, List<OkxWsSubscription> subscriptions) {
        List<Map<String, String>> args = subscriptions.stream().map(OkxWsSubscription::toArgMap).toList();
        return toJson(mapper, Map.of("op", op, "args", args));
    }

    private static String toJson(ObjectMapper mapper, Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize ws message", ex);
        }
    }

    enum MessageKind {
        PONG,
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        SUBSCRIBE_SUCCESS,
        SUBSCRIBE_FAILED,
        BUSINESS_MESSAGE,
        UNKNOWN
    }

    record ParsedMessage(
            MessageKind kind,
            String event,
            String code,
            String msg,
            String channel,
            boolean pong
    ) {
    }
}
