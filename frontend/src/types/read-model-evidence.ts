/**
 * GateU 统一 read-model evidence metadata。
 *
 * Why:
 * 该类型只描述后端 GET-only 诊断响应中的来源、可用性与时间语义。metadata 缺失、UNKNOWN 或
 * UNAVAILABLE 时前端必须 fail-closed，不得补造时间、FRESH 状态或交易授权结论。
 */
export type ReadModelEvidenceAvailability = 'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE' | 'UNKNOWN' | string;

export type ReadModelEvidenceFreshnessStatus = 'FRESH' | 'STALE' | 'UNKNOWN' | string;

export interface ReadModelEvidenceMetadata {
    source: string;
    availability: ReadModelEvidenceAvailability;
    lastCalculatedAt: string | null;
    freshnessStatus: ReadModelEvidenceFreshnessStatus;
    ageSeconds: number | null;
    staleAfterSeconds: number | null;
    staleReason: string | null;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
}
