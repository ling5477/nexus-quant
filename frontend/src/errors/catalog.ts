export type ErrorPresentation =
    | 'GLOBAL_NOTIFICATION'
    | 'INLINE_FORM_ERROR'
    | 'PAGE_STATE_ERROR'
    | 'CONFLICT_ACTION_REQUIRED'
    | 'AUTH_REDIRECT_OR_PROMPT';

interface ErrorCatalogEntry {
    errorKey: string;
    errorId: string | null;
    httpStatus: readonly number[];
    severity: 'error' | 'warning';
    presentation: ErrorPresentation;
    titleKey: string;
    messageKey: string;
    actionKey: string;
    retryPolicy: {
        automaticMutation: false;
        userAction: 'refresh' | 'correct' | 'signIn' | 'contact' | 'checkConnection';
    };
    traceIdVisibility: 'visible';
}

function entry(
    errorKey: string,
    httpStatus: number[],
    textKey: string,
    presentation: ErrorPresentation,
    userAction: ErrorCatalogEntry['retryPolicy']['userAction'],
    errorId: string | null = null,
): ErrorCatalogEntry {
    return {
        errorKey,
        errorId,
        httpStatus,
        presentation,
        severity: presentation === 'CONFLICT_ACTION_REQUIRED' ? 'warning' : 'error',
        titleKey: `errors:${textKey}.title`,
        messageKey: `errors:${textKey}.message`,
        actionKey: `errors:actions.${userAction}`,
        retryPolicy: {automaticMutation: false, userAction},
        traceIdVisibility: 'visible',
    };
}

// 错误身份只有此处负责映射；HTTP 回退不把未知业务错误冒认成订单冲突。
export const errorCatalog: Readonly<Record<string, ErrorCatalogEntry>> = {
    RESEARCH_CREATION_SCOPE_CHANGED: entry(
        'RESEARCH_CREATION_SCOPE_CHANGED',
        [0],
        'researchCreationScopeChanged',
        'PAGE_STATE_ERROR',
        'refresh',
    ),
    RESEARCH_CREATION_UNKNOWN: entry(
        'RESEARCH_CREATION_UNKNOWN',
        [0],
        'researchCreationUnknown',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    RESEARCH_CREATION_STORAGE: entry(
        'RESEARCH_CREATION_STORAGE',
        [0],
        'researchCreationStorage',
        'PAGE_STATE_ERROR',
        'contact',
    ),
    FEE_SYNC_PENDING: entry(
        'FEE_SYNC_PENDING',
        [0],
        'feeSyncUnknown',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    FEE_SYNC_STORAGE_UNAVAILABLE: entry(
        'FEE_SYNC_STORAGE_UNAVAILABLE',
        [0],
        'feeSyncStorage',
        'PAGE_STATE_ERROR',
        'contact',
    ),
    FEE_SYNC_RESPONSE_INVALID: entry(
        'FEE_SYNC_RESPONSE_INVALID',
        [0],
        'feeSyncUnknown',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    FEE_SYNC_SCOPE_CHANGED: entry(
        'FEE_SYNC_SCOPE_CHANGED',
        [0],
        'accountCommandScope',
        'AUTH_REDIRECT_OR_PROMPT',
        'signIn',
    ),
    FEE_SYNC_TARGET_INVALID: entry(
        'FEE_SYNC_TARGET_INVALID',
        [0],
        'validation',
        'INLINE_FORM_ERROR',
        'correct',
    ),
    CREDENTIAL_OPERATION_PENDING: entry(
        'CREDENTIAL_OPERATION_PENDING',
        [0],
        'credentialOperationUnknown',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    CREDENTIAL_OPERATION_STORAGE_UNAVAILABLE: entry(
        'CREDENTIAL_OPERATION_STORAGE_UNAVAILABLE',
        [0],
        'accountCommandStorage',
        'PAGE_STATE_ERROR',
        'contact',
    ),
    CREDENTIAL_OPERATION_RESPONSE_INVALID: entry(
        'CREDENTIAL_OPERATION_RESPONSE_INVALID',
        [0],
        'credentialOperationUnknown',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    CREDENTIAL_OPERATION_SCOPE_CHANGED: entry(
        'CREDENTIAL_OPERATION_SCOPE_CHANGED',
        [0],
        'accountCommandScope',
        'AUTH_REDIRECT_OR_PROMPT',
        'signIn',
    ),
    CREDENTIAL_OPERATION_IDENTITY_CONFLICT: entry(
        'CREDENTIAL_OPERATION_IDENTITY_CONFLICT',
        [409],
        'credentialOperationIdentity',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    SCHEDULER_VERSION_CONFLICT: entry(
        'SCHEDULER_VERSION_CONFLICT',
        [409],
        'schedulerOperationVersion',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    SCHEDULER_CAPABILITY_UNAVAILABLE: entry(
        'SCHEDULER_CAPABILITY_UNAVAILABLE',
        [409],
        'schedulerOperationCapability',
        'PAGE_STATE_ERROR',
        'refresh',
    ),
    SCHEDULER_OPERATION_ID_CONFLICT: entry(
        'SCHEDULER_OPERATION_ID_CONFLICT',
        [409],
        'schedulerOperationIdentity',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    SCHEDULER_OPERATION_PENDING: entry(
        'SCHEDULER_OPERATION_PENDING',
        [0, 409],
        'schedulerOperationPending',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    SCHEDULER_OPERATION_STORAGE_UNAVAILABLE: entry(
        'SCHEDULER_OPERATION_STORAGE_UNAVAILABLE',
        [0],
        'schedulerOperationStorage',
        'PAGE_STATE_ERROR',
        'contact',
    ),
    SCHEDULER_OPERATION_RESPONSE_INVALID: entry(
        'SCHEDULER_OPERATION_RESPONSE_INVALID',
        [0],
        'schedulerOperationUnknown',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    SCHEDULER_OPERATION_SCOPE_CHANGED: entry(
        'SCHEDULER_OPERATION_SCOPE_CHANGED',
        [0],
        'schedulerOperationScope',
        'AUTH_REDIRECT_OR_PROMPT',
        'signIn',
    ),
    ACCOUNT_COMMAND_PENDING: entry(
        'ACCOUNT_COMMAND_PENDING',
        [0],
        'accountCommandPending',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    ACCOUNT_COMMAND_STORAGE_UNAVAILABLE: entry(
        'ACCOUNT_COMMAND_STORAGE_UNAVAILABLE',
        [0],
        'accountCommandStorage',
        'PAGE_STATE_ERROR',
        'contact',
    ),
    ACCOUNT_COMMAND_RESPONSE_INVALID: entry(
        'ACCOUNT_COMMAND_RESPONSE_INVALID',
        [0],
        'accountCommandUnknown',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    ACCOUNT_COMMAND_SCOPE_CHANGED: entry(
        'ACCOUNT_COMMAND_SCOPE_CHANGED',
        [0],
        'accountCommandScope',
        'AUTH_REDIRECT_OR_PROMPT',
        'signIn',
    ),
    ACCOUNT_COMMAND_IDENTITY_CONFLICT: entry(
        'ACCOUNT_COMMAND_IDENTITY_CONFLICT',
        [409],
        'idempotencyConflict',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    ORDER_VERSION_CONFLICT: entry(
        'ORDER_VERSION_CONFLICT',
        [409],
        'orderVersionConflict',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
        'NQ-TRD-1001',
    ),
    STATE_CONFLICT: entry(
        'STATE_CONFLICT',
        [409],
        'conflict',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    VALIDATION_ERROR: entry(
        'VALIDATION_ERROR',
        [400, 422],
        'validation',
        'INLINE_FORM_ERROR',
        'correct',
    ),
    MALFORMED_REQUEST: entry(
        'MALFORMED_REQUEST',
        [400],
        'badRequest',
        'INLINE_FORM_ERROR',
        'correct',
    ),
    MISSING_PARAMETER: entry(
        'MISSING_PARAMETER',
        [400],
        'validation',
        'INLINE_FORM_ERROR',
        'correct',
    ),
    BAD_REQUEST: entry('BAD_REQUEST', [400], 'badRequest', 'INLINE_FORM_ERROR', 'correct'),
    UNAUTHORIZED: entry('UNAUTHORIZED', [401], 'unauthorized', 'AUTH_REDIRECT_OR_PROMPT', 'signIn'),
    FORBIDDEN: entry('FORBIDDEN', [403], 'forbidden', 'GLOBAL_NOTIFICATION', 'contact'),
    RESOURCE_NOT_FOUND: entry(
        'RESOURCE_NOT_FOUND',
        [404],
        'notFound',
        'PAGE_STATE_ERROR',
        'refresh',
    ),
    BUSINESS_REJECTED: entry('BUSINESS_REJECTED', [422], 'rejected', 'PAGE_STATE_ERROR', 'correct'),
    INTERNAL_ERROR: entry(
        'INTERNAL_ERROR',
        [500, 502, 503, 504],
        'internal',
        'GLOBAL_NOTIFICATION',
        'contact',
    ),
    ADMIN_NOT_INITIALIZED: entry(
        'ADMIN_NOT_INITIALIZED',
        [409],
        'adminNotInitialized',
        'AUTH_REDIRECT_OR_PROMPT',
        'contact',
    ),
    ADMISSION_BLOCKED: entry(
        'ADMISSION_BLOCKED',
        [422],
        'admissionBlocked',
        'PAGE_STATE_ERROR',
        'correct',
    ),
    ADMISSION_STALE: entry(
        'ADMISSION_STALE',
        [409],
        'admissionStale',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    ADMISSION_GUARD_UNINITIALIZED: entry(
        'ADMISSION_GUARD_UNINITIALIZED',
        [409],
        'admissionGuard',
        'PAGE_STATE_ERROR',
        'contact',
    ),
    SHADOW_MATERIALIZATION_FORBIDDEN: entry(
        'SHADOW_MATERIALIZATION_FORBIDDEN',
        [403],
        'forbidden',
        'GLOBAL_NOTIFICATION',
        'contact',
    ),
    REVIEW_CASE_VERSION_CONFLICT: entry(
        'REVIEW_CASE_VERSION_CONFLICT',
        [409],
        'reviewConflict',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    REVIEW_CASE_NOT_FOUND: entry(
        'REVIEW_CASE_NOT_FOUND',
        [404],
        'notFound',
        'PAGE_STATE_ERROR',
        'refresh',
    ),
    REVIEW_ACTION_FORBIDDEN: entry(
        'REVIEW_ACTION_FORBIDDEN',
        [403],
        'forbidden',
        'GLOBAL_NOTIFICATION',
        'contact',
    ),
    IDEMPOTENCY_KEY_REUSED: entry(
        'IDEMPOTENCY_KEY_REUSED',
        [409],
        'idempotencyConflict',
        'CONFLICT_ACTION_REQUIRED',
        'refresh',
    ),
    NETWORK_ERROR: entry('NETWORK_ERROR', [0], 'network', 'GLOBAL_NOTIFICATION', 'checkConnection'),
    UNKNOWN_ERROR: entry('UNKNOWN_ERROR', [], 'unknown', 'PAGE_STATE_ERROR', 'contact'),
};

export function resolveErrorCatalog(error: {
    code?: string;
    errorKey?: string;
    errorId?: string;
    status?: number;
}): ErrorCatalogEntry {
    const byId =
        error.errorId && Object.values(errorCatalog).find((item) => item.errorId === error.errorId);
    if (byId) return byId;
    if (
        error.errorKey &&
        Object.hasOwn(errorCatalog, error.errorKey) &&
        error.errorKey !== 'UNKNOWN_ERROR'
    )
        return errorCatalog[error.errorKey];
    if (error.code && Object.hasOwn(errorCatalog, error.code) && error.code !== 'UNKNOWN_ERROR')
        return errorCatalog[error.code];
    switch (error.status) {
        case 0:
            return errorCatalog.NETWORK_ERROR;
        case 400:
            return errorCatalog.BAD_REQUEST;
        case 401:
            return errorCatalog.UNAUTHORIZED;
        case 403:
            return errorCatalog.FORBIDDEN;
        case 404:
            return errorCatalog.RESOURCE_NOT_FOUND;
        case 409:
            return errorCatalog.STATE_CONFLICT;
        case 422:
            return errorCatalog.BUSINESS_REJECTED;
        default:
            return error.status && error.status >= 500
                ? errorCatalog.INTERNAL_ERROR
                : errorCatalog.UNKNOWN_ERROR;
    }
}
