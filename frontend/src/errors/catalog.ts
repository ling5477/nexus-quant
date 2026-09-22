export type ErrorPresentation = 'GLOBAL_NOTIFICATION' | 'INLINE_FORM_ERROR' | 'PAGE_STATE_ERROR'
    | 'CONFLICT_ACTION_REQUIRED' | 'AUTH_REDIRECT_OR_PROMPT';

export interface ErrorCatalogEntry {
    errorKey: string;
    errorId: string | null;
    httpStatus: readonly number[];
    severity: 'error' | 'warning';
    presentation: ErrorPresentation;
    titleKey: string;
    messageKey: string;
    actionKey: string;
    retryPolicy: {automaticMutation: false; userAction: 'refresh' | 'correct' | 'signIn' | 'contact' | 'checkConnection'};
    traceIdVisibility: 'visible';
}

function entry(errorKey: string, httpStatus: number[], textKey: string,
               presentation: ErrorPresentation, userAction: ErrorCatalogEntry['retryPolicy']['userAction'],
               errorId: string | null = null): ErrorCatalogEntry {
    return {
        errorKey, errorId, httpStatus, presentation,
        severity: presentation === 'CONFLICT_ACTION_REQUIRED' ? 'warning' : 'error',
        titleKey: `errors:${textKey}.title`, messageKey: `errors:${textKey}.message`,
        actionKey: `errors:actions.${userAction}`,
        retryPolicy: {automaticMutation: false, userAction}, traceIdVisibility: 'visible',
    };
}

// 错误身份只有此处负责映射；HTTP 回退不把未知业务错误冒认成订单冲突。
export const errorCatalog: Readonly<Record<string, ErrorCatalogEntry>> = {
    ORDER_VERSION_CONFLICT: entry('ORDER_VERSION_CONFLICT', [409], 'orderVersionConflict', 'CONFLICT_ACTION_REQUIRED', 'refresh', 'NQ-TRD-1001'),
    STATE_CONFLICT: entry('STATE_CONFLICT', [409], 'conflict', 'CONFLICT_ACTION_REQUIRED', 'refresh'),
    VALIDATION_ERROR: entry('VALIDATION_ERROR', [400, 422], 'validation', 'INLINE_FORM_ERROR', 'correct'),
    MALFORMED_REQUEST: entry('MALFORMED_REQUEST', [400], 'badRequest', 'INLINE_FORM_ERROR', 'correct'),
    MISSING_PARAMETER: entry('MISSING_PARAMETER', [400], 'validation', 'INLINE_FORM_ERROR', 'correct'),
    BAD_REQUEST: entry('BAD_REQUEST', [400], 'badRequest', 'INLINE_FORM_ERROR', 'correct'),
    UNAUTHORIZED: entry('UNAUTHORIZED', [401], 'unauthorized', 'AUTH_REDIRECT_OR_PROMPT', 'signIn'),
    FORBIDDEN: entry('FORBIDDEN', [403], 'forbidden', 'GLOBAL_NOTIFICATION', 'contact'),
    RESOURCE_NOT_FOUND: entry('RESOURCE_NOT_FOUND', [404], 'notFound', 'PAGE_STATE_ERROR', 'refresh'),
    BUSINESS_REJECTED: entry('BUSINESS_REJECTED', [422], 'rejected', 'PAGE_STATE_ERROR', 'correct'),
    INTERNAL_ERROR: entry('INTERNAL_ERROR', [500, 502, 503, 504], 'internal', 'GLOBAL_NOTIFICATION', 'contact'),
    ADMIN_NOT_INITIALIZED: entry('ADMIN_NOT_INITIALIZED', [409], 'adminNotInitialized', 'AUTH_REDIRECT_OR_PROMPT', 'contact'),
    ADMISSION_BLOCKED: entry('ADMISSION_BLOCKED', [422], 'admissionBlocked', 'PAGE_STATE_ERROR', 'correct'),
    ADMISSION_STALE: entry('ADMISSION_STALE', [409], 'admissionStale', 'CONFLICT_ACTION_REQUIRED', 'refresh'),
    ADMISSION_GUARD_UNINITIALIZED: entry('ADMISSION_GUARD_UNINITIALIZED', [409], 'admissionGuard', 'PAGE_STATE_ERROR', 'contact'),
    SHADOW_MATERIALIZATION_FORBIDDEN: entry('SHADOW_MATERIALIZATION_FORBIDDEN', [403], 'forbidden', 'GLOBAL_NOTIFICATION', 'contact'),
    REVIEW_CASE_VERSION_CONFLICT: entry('REVIEW_CASE_VERSION_CONFLICT', [409], 'reviewConflict', 'CONFLICT_ACTION_REQUIRED', 'refresh'),
    REVIEW_CASE_NOT_FOUND: entry('REVIEW_CASE_NOT_FOUND', [404], 'notFound', 'PAGE_STATE_ERROR', 'refresh'),
    REVIEW_ACTION_FORBIDDEN: entry('REVIEW_ACTION_FORBIDDEN', [403], 'forbidden', 'GLOBAL_NOTIFICATION', 'contact'),
    IDEMPOTENCY_KEY_REUSED: entry('IDEMPOTENCY_KEY_REUSED', [409], 'idempotencyConflict', 'CONFLICT_ACTION_REQUIRED', 'refresh'),
    NETWORK_ERROR: entry('NETWORK_ERROR', [0], 'network', 'GLOBAL_NOTIFICATION', 'checkConnection'),
    UNKNOWN_ERROR: entry('UNKNOWN_ERROR', [], 'unknown', 'PAGE_STATE_ERROR', 'contact'),
};

export function resolveErrorCatalog(error: {code?: string; errorKey?: string; errorId?: string; status?: number}): ErrorCatalogEntry {
    const byId = error.errorId && Object.values(errorCatalog).find((item) => item.errorId === error.errorId);
    if (byId) return byId;
    if (error.errorKey && Object.hasOwn(errorCatalog, error.errorKey) && error.errorKey !== 'UNKNOWN_ERROR') return errorCatalog[error.errorKey];
    if (error.code && Object.hasOwn(errorCatalog, error.code) && error.code !== 'UNKNOWN_ERROR') return errorCatalog[error.code];
    switch (error.status) {
        case 0: return errorCatalog.NETWORK_ERROR;
        case 400: return errorCatalog.BAD_REQUEST;
        case 401: return errorCatalog.UNAUTHORIZED;
        case 403: return errorCatalog.FORBIDDEN;
        case 404: return errorCatalog.RESOURCE_NOT_FOUND;
        case 409: return errorCatalog.STATE_CONFLICT;
        case 422: return errorCatalog.BUSINESS_REJECTED;
        default: return error.status && error.status >= 500 ? errorCatalog.INTERNAL_ERROR : errorCatalog.UNKNOWN_ERROR;
    }
}
