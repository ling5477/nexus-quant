import {describe, expect, it} from 'vitest';
import {sessionWindow, shouldRenewSession} from '../../src/features/session/session-lifecycle';
import {useAuthStore, selectSessionIdentity} from '../../src/store/auth-store';
import {responseBelongsToSession} from '../../src/api/session-scope';
import {sessionClockOffset} from '../../src/utils/token-storage';

const at = Date.parse('2026-10-19T10:00:00Z');
const iso = (offset: number) => new Date(at + offset).toISOString();
const times = {expiresAt: iso(3600_000), idleExpiresAt: iso(1800_000), sessionExpiresAt: iso(8 * 3600_000), serverTime: iso(0), serverTimeOffsetMs: 0};

describe('活跃会话生命周期', () => {
    it('刷新复用已保存时钟差；旧存储重读及续期失败不能移动原截止时间', () => {
        const offset = sessionClockOffset({serverTime: iso(0)}, at + 3600_000);
        expect(offset).toBe(-3600_000);
        const restored = {serverTime: iso(0), serverTimeOffsetMs: offset};
        expect(sessionClockOffset(restored, at + 4200_000)).toBe(offset);
        expect(sessionWindow({...times, serverTimeOffsetMs: offset}, at + 5400_000).remaining).toBe(0);
    });
    it('空闲与最长时限先到者生效，服务端时钟差参与计算', () => {
        expect(sessionWindow(times, at + 1800_000).remaining).toBe(0);
        expect(sessionWindow({...times, idleExpiresAt: iso(9 * 3600_000), expiresAt: iso(9 * 3600_000)}, at + 8 * 3600_000).remaining).toBe(0);
        expect(sessionWindow({...times, serverTimeOffsetMs: 30_000}, at).remaining).toBe(1770_000);
    });
    it('后台轮询、隐藏标签和过期会话不能触发自动续期', () => {
        expect(shouldRenewSession(false, true, 60_000, 60_000, 1000, 0)).toBe(false);
        expect(shouldRenewSession(true, false, 60_000, 60_000, 1000, 0)).toBe(false);
        expect(shouldRenewSession(true, true, 0, 60_000, 1000, 0)).toBe(false);
        expect(shouldRenewSession(true, true, 60_000, 60_000, 1000, 0)).toBe(true);
    });
    it('网络失败有退避及三次上限', () => {
        expect(shouldRenewSession(true, true, 60_000, 1000, 1000, 1)).toBe(false);
        expect(shouldRenewSession(true, true, 60_000, 30_000, 1000, 2)).toBe(true);
        expect(shouldRenewSession(true, true, 60_000, 60_000, 1000, 3)).toBe(false);
    });
    it('短令牌在到期前提前续期，最长时限仍不变', () => {
        expect(sessionWindow({...times, expiresAt: iso(9000)}, at).interval).toBe(3000);
    });
    it('同登录更新令牌不改变上下文身份，不接受其他登录或退出后的晚到续期', () => {
        const initial = {accessToken: 'synthetic-a', tokenType: 'Bearer', expiresIn: 3600, expiresAt: iso(3600_000),
            username: 'synthetic-user', roles: ['VIEWER'], mustChangePassword: false, sessionId: 'session-a',
            idleExpiresAt: iso(1800_000), sessionExpiresAt: iso(8 * 3600_000), serverTime: iso(0)};
        useAuthStore.getState().setSession(initial);
        useAuthStore.getState().updateCurrentUser({userId: 7, username: initial.username, roles: initial.roles,
            mustChangePassword: false, authenticated: true, defaultExchangeAccountId: 9,
            defaultExchangeCode: 'OKX', defaultTradeEnv: 'SIM', defaultAccountAlias: 'synthetic'});
        const next = {...initial, accessToken: 'synthetic-b', serverTime: iso(1000), idleExpiresAt: iso(1801_000)};
        useAuthStore.getState().renewSession(next);
        expect(selectSessionIdentity(useAuthStore.getState())).toBe('session-a');
        expect(useAuthStore.getState().currentUser?.defaultExchangeAccountId).toBe(9);
        expect(useAuthStore.getState().accessToken).toBe('synthetic-b');
        useAuthStore.getState().renewSession(initial);
        expect(useAuthStore.getState().accessToken).toBe('synthetic-b');
        useAuthStore.getState().renewSession({...next, sessionId: 'session-other'});
        expect(useAuthStore.getState().sessionId).toBe('session-a');
        useAuthStore.getState().clearAuth();
        useAuthStore.getState().renewSession(next);
        expect(useAuthStore.getState().accessToken).toBeNull();
    });
    it('同会话在途响应跨令牌续期有效；换账号和退出仍隔离', () => {
        expect(responseBelongsToSession('Bearer old', 'new', 'Bearer', '/facts', 'one', 'one')).toBe(true);
        expect(responseBelongsToSession('Bearer old', 'new', 'Bearer', '/facts', 'one', 'two')).toBe(false);
        expect(responseBelongsToSession('Bearer old', null, 'Bearer', '/facts', 'one', null)).toBe(false);
    });
});
