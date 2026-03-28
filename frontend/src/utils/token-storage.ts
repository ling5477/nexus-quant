import type {CurrentUser, LoginResponse} from '@/types/auth';

const STORAGE_KEY = 'nexus-quant.console.auth';

interface StoredAuthSession {
    accessToken: string;
    tokenType: string;
    expiresAt: string;
    username: string;
    roles: string[];
}

function isBrowser(): boolean {
    return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

function toStoredSession(payload: LoginResponse | StoredAuthSession): StoredAuthSession {
    return {
        accessToken: payload.accessToken,
        tokenType: payload.tokenType,
        expiresAt: payload.expiresAt,
        username: payload.username,
        roles: payload.roles,
    };
}

export function readStoredSession(): StoredAuthSession | null {
    if (!isBrowser()) {
        return null;
    }

    const raw = window.localStorage.getItem(STORAGE_KEY);

    if (!raw) {
        return null;
    }

    try {
        const parsed = JSON.parse(raw) as Partial<StoredAuthSession>;

        if (!parsed.accessToken || !parsed.tokenType || !parsed.expiresAt || !parsed.username || !Array.isArray(parsed.roles)) {
            window.localStorage.removeItem(STORAGE_KEY);
            return null;
        }

        return {
            accessToken: parsed.accessToken,
            tokenType: parsed.tokenType,
            expiresAt: parsed.expiresAt,
            username: parsed.username,
            roles: parsed.roles,
        };
    } catch {
        window.localStorage.removeItem(STORAGE_KEY);
        return null;
    }
}

export function writeStoredSession(payload: LoginResponse | StoredAuthSession): void {
    if (!isBrowser()) {
        return;
    }

    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(toStoredSession(payload)));
}

export function clearStoredSession(): void {
    if (!isBrowser()) {
        return;
    }

    window.localStorage.removeItem(STORAGE_KEY);
}

export function toCurrentUser(session: StoredAuthSession): CurrentUser {
    return {
        userId: 0,
        username: session.username,
        roles: session.roles,
        authenticated: true,
        defaultExchangeAccountId: null,
        defaultExchangeCode: null,
        defaultTradeEnv: null,
        defaultAccountAlias: null,
    };
}
