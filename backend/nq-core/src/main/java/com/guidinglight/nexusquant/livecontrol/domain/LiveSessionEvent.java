package com.guidinglight.nexusquant.livecontrol.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** LiveSession append-only 有序事件。sequence 由数据库锁定 session row 后分配。 */
public record LiveSessionEvent(
        UUID id,
        UUID sessionId,
        long sequence,
        LiveSessionState fromState,
        LiveSessionState toState,
        String command,
        Long actorId,
        String requestId,
        String traceId,
        String reasonCode,
        String idempotencyKey,
        String commandPayloadHash,
        String metadataJson,
        Instant createdAt
) {
    public LiveSessionEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(toState, "toState must not be null");
        require(sequence > 0, "sequence must be positive");
        requireText(command, "command");
        requireText(requestId, "requestId");
        requireText(traceId, "traceId");
        requireText(reasonCode, "reasonCode");
        requireText(idempotencyKey, "idempotencyKey");
        require(commandPayloadHash != null && commandPayloadHash.matches("[0-9a-f]{64}"),
                "commandPayloadHash must be lowercase SHA-256");
        require(metadataJson != null && metadataJson.length() <= 8192, "metadataJson is invalid");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public LiveSessionEvent withSequence(long allocatedSequence) {
        return new LiveSessionEvent(
                id, sessionId, allocatedSequence, fromState, toState, command, actorId,
                requestId, traceId, reasonCode, idempotencyKey, commandPayloadHash, metadataJson, createdAt
        );
    }

    private static void requireText(String value, String name) {
        require(value != null && !value.isBlank() && value.length() <= 128, name + " is invalid");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
