import type {ReadModelEvidenceMetadata} from '@/types/read-model-evidence';

/**
 * Validation Operations Runtime Evidence 的只读聚合类型。
 *
 * Why: 该类型只表达五个既有诊断来源的 metadata 汇总；不包含交易授权、执行命令、凭证、账户、订单或真实 adapter 状态。
 */
export interface ValidationOperationsRuntimeEvidenceSource {
    sourceKey: string;
    displayName: string;
    evidenceMetadata: ReadModelEvidenceMetadata;
}

export interface ValidationOperationsRuntimeEvidenceOverviewResponse {
    generatedAt: string;
    evidenceMetadata: ReadModelEvidenceMetadata;
    sourceCount: number;
    availableCount: number;
    partialCount: number;
    unavailableCount: number;
    unknownAvailabilityCount: number;
    freshCount: number;
    staleCount: number;
    unknownFreshnessCount: number;
    sources: ValidationOperationsRuntimeEvidenceSource[];
    traceId: string;
}
