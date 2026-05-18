package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersion;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyVersionResponse 描述 GateI-1 策略版本响应。
 */
@Schema(name = "StrategyVersionResponse", description = "策略版本响应")
public record StrategyVersionResponse(
        @Schema(description = "策略版本 ID")
        String strategyVersionId,
        @Schema(description = "策略编码")
        String strategyCode,
        @Schema(description = "版本号")
        int version,
        @Schema(description = "版本展示名称")
        String versionName,
        @Schema(description = "版本状态")
        String status,
        @Schema(description = "参数快照 JSON")
        String paramSnapshotJson,
        @Schema(description = "配置快照 JSON")
        String configSnapshotJson,
        @Schema(description = "来源快照 JSON")
        String sourceSnapshotJson,
        @Schema(description = "快照 checksum")
        String checksum,
        @Schema(description = "创建人")
        String createdBy,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "更新时间")
        Instant updatedAt
) {
    public static StrategyVersionResponse from(StrategyVersion version) {
        return new StrategyVersionResponse(
                version.strategyVersionId(),
                version.strategyCode(),
                version.version(),
                version.versionName(),
                version.status().name(),
                version.paramSnapshotJson(),
                version.configSnapshotJson(),
                version.sourceSnapshotJson(),
                version.checksum(),
                version.createdBy(),
                version.createdAt(),
                version.updatedAt()
        );
    }
}
