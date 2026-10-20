package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shadow Run 本地快照事实。
 *
 * <p>快照用于保存 public marketdata input、本地策略决策、risk preflight 和 order intent
 * preview 的脱敏 payload。唯一性由 {@code shadowRunId + snapshotType + sequenceNo}
 * 保证；payload 不允许保存 credential、private endpoint payload 或真实账户/订单状态。
 */
public record ShadowRunSnapshot(
        UUID id,
        UUID shadowRunId,
        ShadowRunSnapshotType snapshotType,
        int sequenceNo,
        String source,
        String schemaVersion,
        String checksum,
        JsonNode payload,
        Instant capturedAt,
        String traceId,
        Instant createdAt
) {

    public ShadowRunSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(shadowRunId, "shadowRunId must not be null");
        Objects.requireNonNull(snapshotType, "snapshotType must not be null");
        if (sequenceNo < 0) {
            throw new IllegalArgumentException("sequenceNo must not be negative");
        }
        ShadowRun.requireText(source, "source");
        ShadowRun.requireText(schemaVersion, "schemaVersion");
        ShadowRun.requireText(checksum, "checksum");
        ShadowRunSensitiveDataGuard.validateJson("payload", payload);
        ShadowRunJsonRules.requireObject(payload, "payload");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        ShadowRun.requireText(traceId, "traceId");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
