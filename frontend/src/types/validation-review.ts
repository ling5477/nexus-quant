/**
 * GateV-2 durable validation review 的前端 contract。
 *
 * 约束：字段与后端 response/request DTO 一一对应；不声明 evidence anchor、credential、
 * trading authorization 或服务端未公开的 trace/schema/checksum 字段。
 */
export type ValidationReviewState = 'OPEN' | 'ACKNOWLEDGED' | 'ESCALATED' | 'RESOLVED' | 'CLOSED';

export type ValidationReviewSeverity = 'INFO' | 'WARNING' | 'HIGH' | 'CRITICAL';

export type ValidationReviewAction = 'acknowledge' | 'escalate' | 'resolve' | 'close';

export interface ValidationReviewCase {
    id: string;
    ownerId: number;
    evidenceType: string;
    evidenceSource: string;
    severity: ValidationReviewSeverity;
    state: ValidationReviewState;
    title: string;
    summary: string;
    version: number;
    createdAt: string;
    updatedAt: string;
    acknowledgedBy: number | null;
    acknowledgedAt: string | null;
    escalatedBy: number | null;
    escalatedAt: string | null;
    resolvedBy: number | null;
    resolvedAt: string | null;
    closedBy: number | null;
    closedAt: string | null;
    retentionUntil: string;
    diagnosticOnly: true;
    noSideEffect: true;
    notTradingAuthorization: true;
    liveDisabled: true;
}

export interface ValidationReviewEvent {
    id: string;
    caseId: string;
    eventType: string;
    fromState: ValidationReviewState;
    toState: ValidationReviewState;
    caseVersion: number;
    actorId: number;
    createdAt: string;
}

export interface ValidationReviewListRequest {
    state?: ValidationReviewState;
    severity?: ValidationReviewSeverity;
    ownerId?: number;
    limit: number;
    offset: number;
}

export interface ValidationReviewLifecycleRequest {
    expectedVersion: number;
    reason: string;
    metadata?: Record<string, unknown>;
}

export interface ValidationReviewLifecycleCommand {
    caseId: string;
    action: ValidationReviewAction;
    payload: ValidationReviewLifecycleRequest;
    idempotencyKey: string;
}
