package com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * ValidationOperationsRuntimeEvidenceOverviewReadModel 表示五个既有诊断 read model 的只读 metadata 聚合。
 *
 * <p>Why: 该模型只汇总来源已经给出的 evidenceMetadata，帮助人工识别证据是否完整；它不重新解释底层交易
 * 事实、不产生运行记录，也不表示策略、Paper、Shadow 或 LIVE 的执行就绪与交易授权。
 */
public record ValidationOperationsRuntimeEvidenceOverviewReadModel(
        Instant generatedAt,
        ReadModelEvidenceMetadata evidenceMetadata,
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

    public ValidationOperationsRuntimeEvidenceOverviewReadModel {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        evidenceMetadata = Objects.requireNonNull(evidenceMetadata, "evidenceMetadata must not be null");
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (sourceCount != sources.size()) {
            throw new IllegalArgumentException("sourceCount must equal sources size");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /**
     * RuntimeEvidenceSource 保留来源身份和其原始 metadata，不复制或重算任何来源业务结论。
     */
    public record RuntimeEvidenceSource(
            String sourceKey,
            String displayName,
            ReadModelEvidenceMetadata evidenceMetadata
    ) {
        public RuntimeEvidenceSource {
            if (sourceKey == null || sourceKey.isBlank()) {
                throw new IllegalArgumentException("sourceKey must not be blank");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
            evidenceMetadata = Objects.requireNonNull(evidenceMetadata, "evidenceMetadata must not be null");
        }
    }
}
