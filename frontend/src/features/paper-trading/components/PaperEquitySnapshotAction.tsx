import {useState} from 'react';
import {Alert, Button, Space, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import {useAuthStore} from '@/store/auth-store';
import type {EquityCurveSnapshotItem} from '../types/paper-trading';
import type {SimPerformance} from '../api/analysis';

interface EventIdentity {
    decisionId: string;
    snapshotTime: string;
}
const instant = (value: string) => value.replace(/\.0+Z$/, 'Z').replace(/(\.\d*?[1-9])0+Z$/, '$1Z');
const amounts = new Set([
    'totalEquity',
    'cashBalance',
    'positionValue',
    'unrealizedPnl',
    'realizedPnl',
    'drawdown',
]);

/** 持久保留原事件；网络未知时只确认或重发同一幂等事件，不改用最新估值身份。 */
export function PaperEquitySnapshotAction({
    paperRunId,
    sample,
}: {
    paperRunId: string;
    sample: SimPerformance['samples'][number] | null;
}) {
    const {t} = useTranslation('pages');
    const user = useAuthStore((s) => s.currentUser);
    const key = `nq:paper-equity:${user?.userId}:${paperRunId}`;
    const [stored] = useState(() => {
        try {
            const raw = localStorage.getItem(key);
            const pending = raw ? (JSON.parse(raw) as EventIdentity) : null;
            if (
                pending &&
                (typeof pending.decisionId !== 'string' ||
                    !pending.decisionId ||
                    typeof pending.snapshotTime !== 'string' ||
                    !pending.snapshotTime)
            )
                throw Error();
            return {pending, broken: false};
        } catch {
            return {pending: null, broken: true};
        }
    });
    const [pending, setPending] = useState<EventIdentity | null>(stored.pending);
    const [busy, setBusy] = useState(false);
    const [problem, setProblem] = useState<string | null>(null);
    const [result, setResult] = useState<EquityCurveSnapshotItem | null>(null);
    const writable = Boolean(user?.roles.some((r) => ['ADMIN', 'OPERATOR'].includes(r)));
    const submit = async () => {
        if (busy || !user || stored.broken || (!sample && !pending) || !writable) return;
        const owner = user.userId;
        const original = pending ?? {decisionId: sample!.decisionId, snapshotTime: sample!.eventAt};
        setBusy(true);
        setProblem(null);
        setResult(null);
        try {
            const current = localStorage.getItem(key);
            if (current && current !== JSON.stringify(original))
                throw Error('original equity event changed in another tab');
            localStorage.setItem(key, JSON.stringify(original));
            setPending(original);
            const {data} = await apiClient.post<{
                status: string;
                reason: string | null;
                snapshot: EquityCurveSnapshotItem | null;
            }>(`/paper-trading/runs/${encodeURIComponent(paperRunId)}/equity-snapshots`, original, {
                transformResponse: [(text: string) => parseExactJson(text, amounts)],
            });
            if (
                useAuthStore.getState().currentUser?.userId !== owner ||
                localStorage.getItem(key) !== JSON.stringify(original)
            )
                return;
            if (data.status === 'UNVALUABLE' && data.snapshot === null) {
                localStorage.removeItem(key);
                setPending(null);
                setProblem(t('paperEquity.unvaluable', {reason: data.reason}));
                return;
            }
            const value = data.snapshot;
            if (
                data.status !== 'VERIFIED' ||
                !value ||
                value.paperRunId !== paperRunId ||
                value.valuationStatus !== 'VERIFIED' ||
                value.valuationCurrency !== 'USDT' ||
                instant(value.snapshotTime) !== instant(original.snapshotTime) ||
                JSON.parse(value.valuationDetails ?? '{}').decisionId !== original.decisionId
            )
                throw Error('unexpected equity identity');
            localStorage.removeItem(key);
            setPending(null);
            setResult(value);
        } catch {
            if (useAuthStore.getState().currentUser?.userId === owner)
                setProblem(t('paperEquity.unknown'));
        } finally {
            setBusy(false);
        }
    };
    return (
        <Space direction="vertical" style={{width: '100%'}}>
            <Button
                onClick={() => void submit()}
                loading={busy}
                disabled={stored.broken || !writable || (!pending && !sample)}
            >
                {t(pending ? 'paperEquity.recover' : 'paperEquity.generate')}
            </Button>
            {stored.broken && <Alert type="error" message={t('paperEquity.storage')} />}
            {problem && <Alert type="warning" message={problem} />}
            {result && (
                <Alert
                    type="success"
                    message={t('paperEquity.saved')}
                    description={
                        <Typography.Text copyable>{result.equitySnapshotId}</Typography.Text>
                    }
                />
            )}
        </Space>
    );
}
