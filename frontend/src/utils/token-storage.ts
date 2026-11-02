import type {CurrentUser, LoginResponse} from '@/types/auth';

const STORAGE_KEY = 'nexus-quant.console.auth';

export interface StoredAuthSession {
    sessionId?: string;
    idleExpiresAt?: string;
    sessionExpiresAt?: string;
    serverTime?: string;
    serverTimeOffsetMs?: number;
    accessToken: string;
    tokenType: string;
    expiresAt: string;
    username: string;
    roles: string[];
    mustChangePassword: boolean;
}

function isBrowser(): boolean {
    return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

/** 旧存储携带的是上次接收时的时钟差；不能将旧serverTime误当成刚收到的服务器时间。 */
export function sessionClockOffset(payload: Pick<LoginResponse, 'serverTime' | 'serverTimeOffsetMs'>, receivedAt = Date.now()): number {
    if (typeof payload.serverTimeOffsetMs === 'number' && Number.isFinite(payload.serverTimeOffsetMs)) return payload.serverTimeOffsetMs;
    const server = payload.serverTime ? Date.parse(payload.serverTime) : NaN;
    return Number.isFinite(server) ? server - receivedAt : 0;
}

function toStoredSession(payload: LoginResponse | StoredAuthSession): StoredAuthSession {
    return {
        sessionId: payload.sessionId, idleExpiresAt: payload.idleExpiresAt,
        sessionExpiresAt: payload.sessionExpiresAt, serverTime: payload.serverTime,
        serverTimeOffsetMs: sessionClockOffset(payload),
        accessToken: payload.accessToken,
        tokenType: payload.tokenType,
        expiresAt: payload.expiresAt,
        username: payload.username,
        roles: payload.roles,
        mustChangePassword: payload.mustChangePassword,
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
            sessionId: parsed.sessionId, idleExpiresAt: parsed.idleExpiresAt,
            sessionExpiresAt: parsed.sessionExpiresAt, serverTime: parsed.serverTime,
            serverTimeOffsetMs: parsed.serverTimeOffsetMs,
            accessToken: parsed.accessToken,
            tokenType: parsed.tokenType,
            expiresAt: parsed.expiresAt,
            username: parsed.username,
            roles: parsed.roles,
            mustChangePassword: parsed.mustChangePassword === true,
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
        mustChangePassword: session.mustChangePassword,
        authenticated: true,
        defaultExchangeAccountId: null,
        defaultExchangeCode: null,
        defaultTradeEnv: null,
        defaultAccountAlias: null,
    };
}
