import type {AuthState} from '@/store/auth-store';

type SessionTimes = Pick<AuthState, 'expiresAt' | 'idleExpiresAt' | 'sessionExpiresAt' | 'serverTimeOffsetMs' | 'serverTime'>;

/** 使用服务端时钟差；后台读取不会改变任何期限。 */
export function sessionWindow(state: SessionTimes, now: number) {
    const serverNow = now + state.serverTimeOffsetMs;
    const deadline = (value: string | null) => value ? Date.parse(value) : Infinity;
    const remaining = Math.min(deadline(state.expiresAt), deadline(state.idleExpiresAt), deadline(state.sessionExpiresAt)) - serverNow;
    const absoluteRemaining = deadline(state.sessionExpiresAt) - serverNow;
    const issued = deadline(state.serverTime);
    const interval = Math.max(1000, Math.min(60_000,
        (deadline(state.idleExpiresAt) - issued) / 3, (deadline(state.expiresAt) - issued) / 3));
    return {remaining, absoluteRemaining, interval, serverNow};
}

/** 只有尚未消费的用户操作才允许续期；空闲计时和普通轮询不算活跃。 */
export function shouldRenewSession(active: boolean, visible: boolean, remaining: number,
    sinceLastAttempt: number, interval: number, failures: number): boolean {
    return active && visible && remaining > 0 && failures < 3 && sinceLastAttempt >= Math.max(interval, failures * 15_000);
}
