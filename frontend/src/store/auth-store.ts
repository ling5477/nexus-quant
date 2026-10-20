import {create} from 'zustand';

import type {CurrentUser, LoginResponse} from '@/types/auth';
import {
    clearStoredSession,
    readStoredSession,
    toCurrentUser,
    writeStoredSession,
    sessionClockOffset,
} from '@/utils/token-storage';

type BootstrapStatus = 'idle' | 'loading' | 'ready';
type LogoutReason = 'manual' | 'expired' | 'unauthorized';

export interface AuthState {
    sessionId: string | null;
    idleExpiresAt: string | null;
    sessionExpiresAt: string | null;
    serverTime: string | null;
    serverTimeOffsetMs: number;
    renewSession: (payload: LoginResponse) => void;
    accessToken: string | null;
    tokenType: string | null;
    expiresAt: string | null;
    currentUser: CurrentUser | null;
    bootstrapStatus: BootstrapStatus;
    lastLogoutReason: LogoutReason | null;
    setSession: (payload: LoginResponse) => void;
    updateCurrentUser: (currentUser: CurrentUser | null) => void;
    setBootstrapStatus: (status: BootstrapStatus) => void;
    clearAuth: (reason?: LogoutReason) => void;
}

const initialSession = readStoredSession();

/**
 * 鉴权 store 只保存 token 与当前用户最小信息。
 * Why:
 * 控制台需要登录态在刷新后恢复，但服务端主数据仍交给 TanStack Query；
 * 因此这里只保存路由守卫和 header 必需的最小上下文，不把业务列表塞进全局状态。
 */
export const useAuthStore = create<AuthState>((set) => ({
    sessionId: initialSession?.sessionId ?? null,
    idleExpiresAt: initialSession?.idleExpiresAt ?? null,
    sessionExpiresAt: initialSession?.sessionExpiresAt ?? null,
    serverTime: initialSession?.serverTime ?? null,
    serverTimeOffsetMs: initialSession?.serverTimeOffsetMs ?? 0,
    accessToken: initialSession?.accessToken ?? null,
    tokenType: initialSession?.tokenType ?? null,
    expiresAt: initialSession?.expiresAt ?? null,
    currentUser: initialSession ? toCurrentUser(initialSession) : null,
    bootstrapStatus: initialSession ? 'loading' : 'ready',
    lastLogoutReason: null,
    setSession: (payload) => {
        // 只有正式登录开始新一轮声明提示；刷新和认证资料重读不清除此标记。
        try {
            sessionStorage.removeItem('nq:login-disclosures:' + payload.username);
        } catch {
            /* 浏览器禁用会话存储时由声明组件的当前挂载状态处理。 */
        }
        const offset = sessionClockOffset(payload);
        writeStoredSession({...payload, serverTimeOffsetMs: offset});
        set({
            sessionId: payload.sessionId ?? null, idleExpiresAt: payload.idleExpiresAt ?? null,
            sessionExpiresAt: payload.sessionExpiresAt ?? null, serverTime: payload.serverTime ?? null,
            serverTimeOffsetMs: offset,
            accessToken: payload.accessToken,
            tokenType: payload.tokenType,
            expiresAt: payload.expiresAt,
            currentUser: {
                userId: 0,
                username: payload.username,
                roles: payload.roles,
                mustChangePassword: payload.mustChangePassword,
                authenticated: true,
                defaultExchangeAccountId: null,
                defaultExchangeCode: null,
                defaultTradeEnv: null,
                defaultAccountAlias: null,
            },
            bootstrapStatus: 'ready',
            lastLogoutReason: null,
        });
    },
    renewSession: (payload) => set((state) => {
        if (!state.sessionId || state.sessionId !== payload.sessionId || state.currentUser?.username !== payload.username
            || (state.serverTime && payload.serverTime && Date.parse(payload.serverTime) < Date.parse(state.serverTime))) return {};
        const offset = sessionClockOffset(payload);
        writeStoredSession({...payload, serverTimeOffsetMs: offset});
        return {accessToken: payload.accessToken, tokenType: payload.tokenType, expiresAt: payload.expiresAt,
            idleExpiresAt: payload.idleExpiresAt ?? null, sessionExpiresAt: payload.sessionExpiresAt ?? null,
            serverTime: payload.serverTime ?? null,
            serverTimeOffsetMs: offset,
            currentUser: {...state.currentUser, roles: payload.roles, mustChangePassword: payload.mustChangePassword}};
    }),
    updateCurrentUser: (currentUser) => {
        set((state) => {
            if (state.accessToken && state.tokenType && state.expiresAt && currentUser) {
                writeStoredSession({
                    sessionId: state.sessionId ?? undefined, idleExpiresAt: state.idleExpiresAt ?? undefined,
                    sessionExpiresAt: state.sessionExpiresAt ?? undefined, serverTime: state.serverTime ?? undefined,
                    serverTimeOffsetMs: state.serverTimeOffsetMs,
                    accessToken: state.accessToken,
                    tokenType: state.tokenType,
                    expiresAt: state.expiresAt,
                    username: currentUser.username,
                    roles: currentUser.roles,
                    mustChangePassword: currentUser.mustChangePassword,
                });
            }

            return {
                currentUser,
            };
        });
    },
    setBootstrapStatus: (bootstrapStatus) => set({bootstrapStatus}),
    clearAuth: (reason = 'manual') => {
        clearStoredSession();
        set({
            sessionId: null, idleExpiresAt: null, sessionExpiresAt: null, serverTime: null, serverTimeOffsetMs: 0,
            accessToken: null,
            tokenType: null,
            expiresAt: null,
            currentUser: null,
            bootstrapStatus: 'ready',
            lastLogoutReason: reason,
        });
    },
}));

export function selectIsAuthenticated(state: AuthState): boolean {
    return Boolean(state.accessToken && state.currentUser?.authenticated);
}

/** 续期属于同一次登录；真实身份切换仍隔离全部在途回执与缓存。 */
export function selectSessionIdentity(state: Pick<AuthState, 'sessionId' | 'accessToken'>): string | null {
    return state.sessionId ?? state.accessToken;
}
