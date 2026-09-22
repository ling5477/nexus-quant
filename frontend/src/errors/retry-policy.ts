import type {AppApiError} from '@/types/api';
import {resolveErrorCatalog} from './catalog';

export function shouldRetryQuery(failureCount: number, error: Error): boolean {
    const apiError = error as Partial<AppApiError>;
    const entry = resolveErrorCatalog(apiError);
    return failureCount < 1 && apiError.status !== 401 && apiError.status !== 403
        && entry.presentation !== 'CONFLICT_ACTION_REQUIRED';
}

// 写请求不自动重放；冲突后的刷新与重新确认必须由用户发起。
export const mutationRetry = false;
