package com.guidinglight.nexusquant.livecontrol.execution.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Receipt digest 仅覆盖归一化 allowlist envelope。 */
public final class ExecutionReceiptCanonicalEncoder {

    private static final DateTimeFormatter INSTANT_FORMAT = new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
            .appendLiteral('Z')
            .toFormatter(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private ExecutionReceiptCanonicalEncoder() {
    }

    public static ExecutionReceiptDraft draft(
            UUID receiptId,
            UUID intentId,
            ExecutionReceiptOutcome outcome,
            String exchangeRequestId,
            String exchangeOrderId,
            String errorCategory,
            String errorCode,
            Instant receivedAt
    ) {
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        if (receivedAt.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("receivedAt must have at most microsecond precision");
        }
        String canonical = String.join("\n",
                ExecutionReceiptDraft.DIGEST_SCHEMA,
                "receiptId=" + receiptId,
                "intentId=" + intentId,
                "outcome=" + outcome,
                "exchangeRequestId=" + nullable(exchangeRequestId),
                "exchangeOrderId=" + nullable(exchangeOrderId),
                "errorCategory=" + nullable(errorCategory),
                "errorCode=" + nullable(errorCode),
                "receivedAt=" + INSTANT_FORMAT.format(receivedAt)
        );
        return new ExecutionReceiptDraft(receiptId, intentId, outcome, exchangeRequestId,
                exchangeOrderId, errorCategory, errorCode, receivedAt,
                ExecutionIntentCanonicalEncoder.sha256(canonical));
    }

    private static String nullable(String value) {
        return value == null ? "null" : "base64:" + Base64.getEncoder()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
