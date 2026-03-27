export interface ApiFieldError {
    field?: string;
    message?: string;
}

export interface ApiErrorResponse {
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
    status: number;
    code: string;
    traceId?: string;
    path?: string;
    fieldErrors: ApiFieldError[];
    raw?: unknown;
}
