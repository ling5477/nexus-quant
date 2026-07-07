package com.guidinglight.nexusquant.strategy.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * ShadowRunSnapshotResponse 是 Shadow Run replay snapshot 的只读 DTO。
 *
 * <p>{@code payload} 只允许保存本地 public / preview / diagnostic facts，不允许 credential、private
 * endpoint payload、真实账户余额、真实订单或交易放行字段。
 */
@Schema(name = "ShadowRunSnapshotResponse", description = "GateR-6 read-only Shadow Run snapshot")
public record ShadowRunSnapshotResponse(
        String snapshotType,
        int sequenceNo,
        String source,
        String schemaVersion,
        String checksum,
        JsonNode payload,
        Instant capturedAt,
        String traceId
) {
    public static ShadowRunSnapshotResponse from(ShadowRunSnapshot snapshot) {
        ShadowRunSensitiveDataGuard.validateJson("payload", snapshot.payload());
        return new ShadowRunSnapshotResponse(
                snapshot.snapshotType().name(),
                snapshot.sequenceNo(),
                snapshot.source(),
                snapshot.schemaVersion(),
                snapshot.checksum(),
                snapshot.payload(),
                snapshot.capturedAt(),
                snapshot.traceId()
        );
    }
}
