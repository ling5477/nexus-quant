import {Alert, Button, Space} from 'antd';
import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {authApi} from '@/api/auth';
import {useAuthStore} from '@/store/auth-store';
import {readStoredSession} from '@/utils/token-storage';
import {sessionWindow, shouldRenewSession} from './session-lifecycle';

let renewal: Promise<void> | null = null;

/** 单标签页合并请求；支持Web Locks时跨标签串行，晚到响应不能复活已退出或另一登录的会话。 */
async function renewActiveSession() {
    if (renewal) return renewal;
    const sessionId = useAuthStore.getState().sessionId;
    const execute = async () => {
        if (!sessionId || useAuthStore.getState().sessionId !== sessionId) return;
        const stored = readStoredSession();
        if (stored?.sessionId === sessionId) useAuthStore.getState().renewSession({...stored, expiresIn: 0});
        const payload = await authApi.renew();
        if (useAuthStore.getState().sessionId === sessionId) useAuthStore.getState().renewSession(payload);
    };
    renewal = Promise.resolve(navigator.locks
        ? navigator.locks.request('nq-auth-renew', {signal: AbortSignal.timeout(16_000)}, execute)
        : execute()).then(() => {}).finally(() => { renewal = null; });
    return renewal;
}

export function SessionLifecycle() {
    const {t} = useTranslation();
    const state = useAuthStore();
    const [now, setNow] = useState(Date.now);
    const [busy, setBusy] = useState(false);
    const [failed, setFailed] = useState(false);
    const active = useRef(false);
    const attempts = useRef({at: Date.now(), failures: 0});
    const timing = sessionWindow(state, now);

    async function renew() {
        if (busy || !useAuthStore.getState().sessionId) return;
        setBusy(true);
        attempts.current.at = Date.now();
        // 请求在途的新操作留到下一轮消费，不被本次完成覆盖。
        active.current = false;
        try {
            await renewActiveSession();
            attempts.current.failures = 0;
            setFailed(false);
        } catch {
            attempts.current.failures += 1;
            active.current = true;
            setFailed(true);
        } finally { setBusy(false); setNow(Date.now()); }
    }

    useEffect(() => {
        active.current = false;
        attempts.current = {at: Date.now(), failures: 0};
        if (!state.accessToken) return;
        const tick = () => setNow(Date.now());
        const activity = (event: Event) => {
            if (!event.isTrusted) return;
            active.current = true;
            attempts.current.failures = 0;
            tick();
        };
        const events = ['pointerdown', 'keydown', 'touchstart', 'wheel'] as const;
        events.forEach(name => document.addEventListener(name, activity, {passive: true}));
        document.addEventListener('visibilitychange', tick);
        const timer = globalThis.window.setInterval(tick, 1000);
        return () => {
            events.forEach(name => document.removeEventListener(name, activity));
            document.removeEventListener('visibilitychange', tick);
            globalThis.window.clearInterval(timer);
        };
    }, [state.sessionId, Boolean(state.accessToken)]);

    useEffect(() => {
        if (!state.accessToken) return;
        if (timing.remaining <= 0) { useAuthStore.getState().clearAuth('expired'); return; }
        if (state.sessionId && !state.currentUser?.mustChangePassword && !busy &&
            shouldRenewSession(active.current, document.visibilityState === 'visible', timing.remaining,
                now - attempts.current.at, timing.interval, attempts.current.failures)) void renew();
    }, [now, state.accessToken, state.sessionId, state.currentUser?.mustChangePassword, timing.remaining, timing.interval, busy]);

    if (!state.accessToken || (timing.remaining > 120_000 && !failed)) return null;
    const absolute = timing.absoluteRemaining <= 120_000;
    return <Alert data-testid="session-lifecycle-warning" type="warning" showIcon
        message={t(absolute ? 'sessionLifetime.maximum' : failed ? 'sessionLifetime.failed' : 'sessionLifetime.warning',
            {minutes: Math.max(1, Math.ceil(timing.remaining / 60_000))})}
        description={absolute ? t('sessionLifetime.signInAgain') : undefined}
        action={<Space wrap>{state.sessionId && !absolute && !state.currentUser?.mustChangePassword &&
            <Button size="small" loading={busy} onClick={() => {attempts.current.failures = 0; void renew();}}>
                {t('sessionLifetime.continue')}
            </Button>}</Space>} style={{marginBottom: 12}} />;
}
