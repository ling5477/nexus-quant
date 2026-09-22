export interface ApiFieldError {
    field?: string;
    message?: string;
    reason?: string;
    rejectedValue?: unknown;
}

export interface ApiErrorResponse {
    errorId?: string;
    errorKey?: string;
    timestamp?: string;
    status?: number;
    error?: string;
    code?: string;
    message?: string;
    path?: string;
    traceId?: string;
    fieldErrors?: ApiFieldError[];
}

export interface AppApiError extends Error {
    errorId?: string;
    errorKey?: string;
    status: number;
    code: string;
    traceId?: string;
    path?: string;
    fieldErrors: ApiFieldError[];
    raw?: unknown;
}
