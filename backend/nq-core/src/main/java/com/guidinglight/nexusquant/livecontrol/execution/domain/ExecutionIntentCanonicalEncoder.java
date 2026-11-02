package com.guidinglight.nexusquant.livecontrol.execution.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** 固定字段顺序的 v1 canonical contract；不依赖 Map 或普通 JSON serializer。 */
public final class ExecutionIntentCanonicalEncoder {

    public static final String CLIENT_ORDER_ID_SCHEMA = "execution-client-order-id.v1";

    private ExecutionIntentCanonicalEncoder() {
    }

    public static ExecutionIntentDraft place(
            UUID intentId,
            UUID sessionId,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal limitPrice,
            String localOrderId
    ) {
        String normalizedSymbol = normalizeUpper(symbol, "symbol");
        String normalizedSide = normalizeUpper(side, "side");
        String clientOrderId = stableClientOrderId(intentId);
        String canonical = String.join("\n",
                ExecutionIntentDraft.PAYLOAD_SCHEMA,
                "intentId=" + intentId,
                "sessionId=" + sessionId,
                "action=PLACE",
                "symbol=" + normalizedSymbol,
                "side=" + normalizedSide,
                "orderType=LIMIT",
                "quantity=" + decimal(quantity, "quantity"),
                "limitPrice=" + decimal(limitPrice, "limitPrice"),
                "localOrderId=" + requireText(localOrderId, "localOrderId"),
                "clientOrderId=" + clientOrderId
        );
        return new ExecutionIntentDraft(intentId, sessionId, ExecutionIntentAction.PLACE, normalizedSymbol,
                normalizedSide, "LIMIT", scaled(quantity, "quantity"), scaled(limitPrice, "limitPrice"),
                localOrderId, clientOrderId, sha256(canonical));
    }

    public static ExecutionIntentDraft cancel(
            UUID intentId,
            UUID sessionId,
            String symbol,
            String localOrderId,
            String originalClientOrderId
    ) {
        String normalizedSymbol = normalizeUpper(symbol, "symbol");
        String stableId = requireText(originalClientOrderId, "originalClientOrderId");
        String canonical = String.join("\n",
                ExecutionIntentDraft.PAYLOAD_SCHEMA,
                "intentId=" + intentId,
                "sessionId=" + sessionId,
                "action=CANCEL",
                "symbol=" + normalizedSymbol,
                "localOrderId=" + requireText(localOrderId, "localOrderId"),
                "clientOrderId=" + stableId
        );
        return new ExecutionIntentDraft(intentId, sessionId, ExecutionIntentAction.CANCEL, normalizedSymbol,
                null, null, null, null, localOrderId, stableId, sha256(canonical));
    }

    public static String stableClientOrderId(UUID intentId) {
        String digest = sha256(CLIENT_ORDER_ID_SCHEMA + "\nintentId=" + intentId);
        return "nq1-" + digest.substring(0, 40);
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String decimal(BigDecimal value, String name) {
        return scaled(value, name).toPlainString();
    }

    private static BigDecimal scaled(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        try {
            return value.setScale(8, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + " must have at most 8 decimals", ex);
        }
    }

    private static String normalizeUpper(String value, String name) {
        return requireText(value, name).toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must not contain canonical delimiters");
        }
        return value;
    }
}
