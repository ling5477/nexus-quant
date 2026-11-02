import type {AppApiError} from '@/types/api';

const APP_ERROR_EVENT_NAME = 'nq:app-error';
const globalErrors = new WeakSet<object>();

export function hasGlobalErrorPresentation(error: object): boolean {
    return globalErrors.has(error);
}

type AppErrorListener = (error: AppApiError) => void;

export function emitAppError(error: AppApiError): void {
    if (typeof window === 'undefined') {
        return;
    }

    globalErrors.add(error);
    window.dispatchEvent(new CustomEvent<AppApiError>(APP_ERROR_EVENT_NAME, {detail: error}));
}

export function subscribeAppError(listener: AppErrorListener): () => void {
    if (typeof window === 'undefined') {
        return () => undefined;
    }

    const wrappedListener = (event: Event) => {
        listener((event as CustomEvent<AppApiError>).detail);
    };

    window.addEventListener(APP_ERROR_EVENT_NAME, wrappedListener);

    return () => {
        window.removeEventListener(APP_ERROR_EVENT_NAME, wrappedListener);
    };
}
