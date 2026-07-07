/**
 * GateR-7 Shadow Run read-only frontend types.
 *
 * 这些类型只描述 GateR-6 已有 GET API 的响应结构。前端不得在这里扩展
 * create / start / stop / execute / approve / trade 等写侧能力，也不得暴露 credential
 * material、private payload、real account/order 或交易授权字段。
 */
export type JsonPrimitive = string | number | boolean | null;

export type JsonValue = JsonPrimitive | JsonObject | JsonValue[];

export interface JsonObject {
    [key: string]: JsonValue;
}

export interface ShadowRunSideEffectFlags {
    noOrderSubmission: boolean;
    noCredentialAccess: boolean;
    noPrivateEndpoint: boolean;
    noLedgerMutation: boolean;
    noAccountMutation: boolean;
    noExternalPrivateIo: boolean;
}

export interface ShadowRunListRequest {
    status?: string | null;
    strategyVersionId?: string | null;
    datasetId?: string | null;
    paperRunId?: string | null;
    limit?: number;
    offset?: number;
}

export interface ShadowRunListItemResponse {
    id: string;
    status: string;
    strategyVersionId: string;
    datasetId: string;
    paperRunId: string | null;
    authorizationBoundary: string;
    traceId: string;
    createdAt: string;
    updatedAt: string;
    startedAt: string | null;
    completedAt: string | null;
    blockersCount: number;
    warningsCount: number;
    nextStepsCount: number;
    noOrderSubmission: boolean;
    noCredentialAccess: boolean;
    noPrivateEndpoint: boolean;
    noLedgerMutation: boolean;
    noAccountMutation: boolean;
}

export interface ShadowRunListResponse {
    items: ShadowRunListItemResponse[];
    limit: number;
    offset: number;
    total: number;
}

export interface ShadowRunDetailResponse {
    id: string;
    strategyVersionId: string;
    datasetId: string;
    evaluationId: string;
    publishId: string;
    paperRunId: string;
    status: string;
    windowStart: string;
    windowEnd: string;
    authorizationBoundary: string;
    sideEffectFlags: ShadowRunSideEffectFlags;
    blockers: JsonValue;
    warnings: JsonValue;
    nextSteps: JsonValue;
    requestId: string;
    traceId: string;
    createdAt: string;
    updatedAt: string;
    startedAt: string | null;
    stoppedAt: string | null;
    completedAt: string | null;
}

export interface ShadowRunEventResponse {
    eventType: string;
    fromStatus: string | null;
    toStatus: string | null;
    reasonCode: string | null;
    message: string | null;
    metadata: JsonValue;
    requestId: string | null;
    traceId: string | null;
    createdAt: string;
}

export interface ShadowRunSnapshotResponse {
    snapshotType: string;
    sequenceNo: number;
    source: string;
    schemaVersion: string;
    checksum: string;
    payload: JsonValue;
    capturedAt: string;
    traceId: string | null;
}

export interface ShadowConsistencyReportResponse {
    id: string;
    shadowRunId: string;
    paperRunId: string;
    comparisonStatus: string;
    metricDelta: JsonValue;
    divergenceReasons: JsonValue;
    limitations: JsonValue;
    generatedAt: string;
    traceId: string | null;
}
