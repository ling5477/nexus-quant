import {create} from 'zustand';

import type {CurrentUser, LoginResponse} from '@/types/auth';
import {clearStoredSession, readStoredSession, toCurrentUser, writeStoredSession} from '@/utils/token-storage';

type BootstrapStatus = 'idle' | 'loading' | 'ready';
type LogoutReason = 'manual' | 'expired' | 'unauthorized';

interface AuthState {
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
 * GateG-1 需要登录态在刷新后恢复，但服务端主数据仍交给 TanStack Query；
 * 因此这里只保存路由守卫和 header 必需的最小上下文，不把业务列表塞进全局状态。
 */
export const useAuthStore = create<AuthState>((set) => ({
    accessToken: initialSession?.accessToken ?? null,
    tokenType: initialSession?.tokenType ?? null,
    expiresAt: initialSession?.expiresAt ?? null,
    currentUser: initialSession ? toCurrentUser(initialSession) : null,
    bootstrapStatus: initialSession ? 'loading' : 'ready',
    lastLogoutReason: null,
    setSession: (payload) => {
        writeStoredSession(payload);
        set({
            accessToken: payload.accessToken,
            tokenType: payload.tokenType,
            expiresAt: payload.expiresAt,
            currentUser: {
                userId: 0,
                username: payload.username,
                roles: payload.roles,
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
    updateCurrentUser: (currentUser) => {
        set((state) => {
            if (state.accessToken && state.tokenType && state.expiresAt && currentUser) {
                writeStoredSession({
                    accessToken: state.accessToken,
                    tokenType: state.tokenType,
                    expiresAt: state.expiresAt,
                    username: currentUser.username,
                    roles: currentUser.roles,
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
