package com.guidinglight.nexusquant.strategy.api.web.validationoperations.runtimeevidence;

import com.guidinglight.nexusquant.strategy.api.web.ReadModelEvidenceMetadataResponse;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * ValidationOperationsRuntimeEvidenceOverviewResponse 是五个既有诊断来源的 GET-only metadata 聚合 DTO。
 *
 * <p>它只暴露来源证据可用性与新鲜度计数，不包含交易批准、执行就绪、凭证、账户、订单、adapter payload
 * 或任何写侧命令。
 */
@Schema(
        name = "ValidationOperationsRuntimeEvidenceOverviewResponse",
        description = "Read-only aggregate of five Validation Operations evidence metadata sources"
)
public record ValidationOperationsRuntimeEvidenceOverviewResponse(
        Instant generatedAt,
        ReadModelEvidenceMetadataResponse evidenceMetadata,
        long sourceCount,
        long availableCount,
        long partialCount,
        long unavailableCount,
        long unknownAvailabilityCount,
        long freshCount,
        long staleCount,
        long unknownFreshnessCount,
        List<RuntimeEvidenceSource> sources,
        String traceId
) {

    public static ValidationOperationsRuntimeEvidenceOverviewResponse from(
            ValidationOperationsRuntimeEvidenceOverviewReadModel model
    ) {
        return new ValidationOperationsRuntimeEvidenceOverviewResponse(
                model.generatedAt(),
                ReadModelEvidenceMetadataResponse.from(model.evidenceMetadata()),
                model.sourceCount(),
                model.availableCount(),
                model.partialCount(),
                model.unavailableCount(),
                model.unknownAvailabilityCount(),
                model.freshCount(),
                model.staleCount(),
                model.unknownFreshnessCount(),
                model.sources().stream().map(RuntimeEvidenceSource::from).toList(),
                model.traceId()
        );
    }

    /**
     * RuntimeEvidenceSource 是单个来源的原始 metadata 视图，不改变来源业务语义。
     */
    public record RuntimeEvidenceSource(
            String sourceKey,
            String displayName,
            ReadModelEvidenceMetadataResponse evidenceMetadata
    ) {
        private static RuntimeEvidenceSource from(
                ValidationOperationsRuntimeEvidenceOverviewReadModel.RuntimeEvidenceSource source
        ) {
            return new RuntimeEvidenceSource(
                    source.sourceKey(),
                    source.displayName(),
                    ReadModelEvidenceMetadataResponse.from(source.evidenceMetadata())
            );
        }
    }
}
