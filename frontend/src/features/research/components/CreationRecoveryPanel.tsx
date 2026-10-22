import {canWriteResearch} from '@/utils/distribution';
import {useEffect, useState} from 'react';
import {Alert, App, Button, Space, Typography} from 'antd';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {strategiesApi} from '@/features/strategies/api/strategies';
import {researchApi} from '@/features/research/api/research';
import {backtestsApi} from '@/features/backtests/backtestsApi';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import {
    pendingCreation,
    queryCreation,
    retryCreation,
    acknowledgeCreation,
    creationChanged,
    creationStillCurrent,
    creationResultMatches,
    type CreationKind,
    type PendingCreation,
    type CreationReceipt,
} from '../creation-operations';

async function canonical(receipt: CreationReceipt): Promise<string> {
    let id: string;
    let path: string;
    switch (receipt.kind) {
        case 'PAPER_RUN':
        case 'SIM_RUN': {
            const row = await paperTradingApi.detail(receipt.objectId);
            id = row.paperRunId;
            path = `/paper-trading/runs?paperRunId=${encodeURIComponent(id)}`;
            break;
        }
        case 'STRATEGY': {
            const row = await strategiesApi.detail(receipt.lookupKey);
            id = row.strategyId;
            path = `/strategies?strategyCode=${encodeURIComponent(row.strategyCode)}`;
            break;
        }
        case 'VERSION': {
            const row = await strategiesApi.versionDetail(receipt.parentKey, receipt.objectId);
            id = row.strategyVersionId;
            path = `/strategies?strategyCode=${encodeURIComponent(receipt.parentKey)}&versionId=${encodeURIComponent(id)}`;
            break;
        }
        case 'RESEARCH': {
            const row = await researchApi.detail(receipt.objectId);
            id = row.researchConfigId;
            path = `/research?researchConfigId=${encodeURIComponent(id)}`;
            break;
        }
        case 'BACKTEST': {
            const row = await backtestsApi.detail(receipt.objectId);
            id = row.backtestConfigId;
            path = `/backtests?backtestConfigId=${encodeURIComponent(id)}`;
            break;
        }
        case 'BACKTEST_RUN': {
            const row = await backtestsApi.getRun(receipt.objectId);
            id = row.backtestRunId;
            path = `/backtests?backtestConfigId=${encodeURIComponent(row.backtestConfigId)}&backtestRunId=${encodeURIComponent(id)}`;
            break;
        }
    }
    if (id !== receipt.objectId) throw new Error('Invalid canonical creation identity');
    return path;
}

function RecoveryItem({kind}: {kind: CreationKind}) {
    const {t} = useTranslation('pages');
    const {message} = App.useApp();
    const canWrite = useAuthStore(
        (state) =>
            canWriteResearch(state.currentUser?.roles),
    );
    const user = useAuthStore((state) => state.currentUser?.userId);
    const [original, setOriginal] = useState<PendingCreation | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [result, setResult] = useState<{
        id: string;
        path: string;
        operationId: string;
        ownerId: number;
    } | null>(null);
    const [busyScope, setBusyScope] = useState<string | null>(null);
    useEffect(() => {
        const update = () => {
            try {
                setOriginal(pendingCreation(kind));
            } catch {
                setError(t('creationRecovery.storage'));
            }
        };
        update();
        window.addEventListener(creationChanged, update);
        window.addEventListener('storage', update);
        return () => {
            window.removeEventListener(creationChanged, update);
            window.removeEventListener('storage', update);
        };
    }, [kind, user, t]);
    if (!original) return error ? <Alert type="error" message={error} /> : null;
    const scope = `${user}/${original.operationId}`;
    const busy = busyScope === scope;
    const currentResult = creationResultMatches(original, user ?? 0, result) ? result : null;
    const recover = async (retry: boolean) => {
        if (busy || !user || !creationStillCurrent(original, user)) return;
        const ownerId = user;
        setBusyScope(scope);
        setError(null);
        setResult(null);
        try {
            const receipt = await (retry ? retryCreation(original) : queryCreation(original));
            const path = await canonical(receipt);
            // 跨标签页确认/新建或切换账户后，旧读取不能覆盖新操作的恢复结果。
            if (creationStillCurrent(original, ownerId))
                setResult({id: receipt.objectId, path, operationId: original.operationId, ownerId});
        } catch (reason) {
            if (creationStillCurrent(original, ownerId))
                setError(
                    (reason as AppApiError).status === 404
                        ? t('creationRecovery.notRecorded')
                        : formatApiError(reason as AppApiError),
                );
        } finally {
            setBusyScope((current) => (current === scope ? null : current));
        }
    };
    const input = original.input as Record<string, unknown>;
    return (
        <Alert
            type={currentResult ? 'success' : 'warning'}
            showIcon
            message={t('creationRecovery.title', {kind: t(`creationRecovery.kinds.${kind}`)})}
            description={
                <Space direction="vertical" style={{width: '100%'}}>
                    <Typography.Text>{t('creationRecovery.meaning')}</Typography.Text>
                    <Typography.Text>
                        {String(
                            input.name ??
                                input.strategyName ??
                                input.versionName ??
                                input.backtestConfigId ??
                                '',
                        )}
                    </Typography.Text>
                    <Typography.Text type="secondary" copyable>
                        {original.operationId}
                    </Typography.Text>
                    {error && (
                        <Typography.Text type="danger" role="alert">
                            {error}
                        </Typography.Text>
                    )}
                    {currentResult && (
                        <>
                            <Typography.Text copyable>{currentResult.id}</Typography.Text>
                            <Link to={currentResult.path}>{t('creationRecovery.open')}</Link>
                        </>
                    )}
                    <Space wrap>
                        <Button loading={busy} onClick={() => void recover(false)}>
                            {t('creationRecovery.query')}
                        </Button>
                        {!currentResult && (
                            <Button disabled={busy || !canWrite} onClick={() => void recover(true)}>
                                {t('creationRecovery.retry')}
                            </Button>
                        )}
                        {currentResult && (
                            <Button
                                disabled={busy}
                                onClick={() => {
                                    if (!creationResultMatches(original, user ?? 0, currentResult))
                                        return;
                                    acknowledgeCreation(original);
                                    setResult(null);
                                    message.success(t('creationRecovery.acknowledged'));
                                }}
                            >
                                {t('creationRecovery.acknowledge')}
                            </Button>
                        )}
                    </Space>
                </Space>
            }
        />
    );
}
/** 恢复面板不扫描列表、不猜最新对象；读取成功前不允许丢弃原身份。 */
export function CreationRecoveryPanel({kinds}: {kinds: CreationKind[]}) {
    return (
        <Space direction="vertical" style={{width: '100%'}}>
            {kinds.map((kind) => (
                <RecoveryItem key={kind} kind={kind} />
            ))}
        </Space>
    );
}
