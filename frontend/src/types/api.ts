export interface ApiFieldError {
    field?: string;
    message?: string;
    reason?: string;
    rejectedValue?: unknown;
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
