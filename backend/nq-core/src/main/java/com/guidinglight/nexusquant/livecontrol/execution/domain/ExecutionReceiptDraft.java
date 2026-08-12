package com.guidinglight.nexusquant.livecontrol.execution.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 只包含 V39 allowlist 字段的脱敏回执；raw provider payload 永不进入该模型。 */
public record ExecutionReceiptDraft(
        UUID receiptId,
        UUID intentId,
        ExecutionReceiptOutcome outcome,
        String exchangeRequestId,
        String exchangeOrderId,
        String errorCategory,
        String errorCode,
        Instant receivedAt,
        String payloadDigest
) {
    public static final String DIGEST_SCHEMA = "execution-receipt-envelope.v1";

    public ExecutionReceiptDraft {
        Objects.requireNonNull(receiptId, "receiptId must not be null");
        Objects.requireNonNull(intentId, "intentId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        if (payloadDigest == null || !payloadDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadDigest must be lowercase SHA-256");
        }
        validateNormalized(exchangeRequestId, 128, "exchangeRequestId");
        validateNormalized(exchangeOrderId, 128, "exchangeOrderId");
        validateNormalized(errorCategory, 64, "errorCategory");
        validateNormalized(errorCode, 128, "errorCode");
    }

    private static void validateNormalized(String value, int maxLength, String name) {
        if (value != null && (value.isBlank() || value.length() > maxLength
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)) {
            throw new IllegalArgumentException(name + " must be a bounded single-line normalized value");
        }
    }

    @Override
    public String toString() {
        return "ExecutionReceiptDraft[receiptId=" + receiptId + ", intentId=" + intentId
                + ", outcome=" + outcome + ", normalizedEnvelope=REDACTED, receivedAt=" + receivedAt
                + ", payloadDigest=" + payloadDigest + "]";
    }
}
