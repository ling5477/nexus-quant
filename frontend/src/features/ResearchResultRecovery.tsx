import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import {useEffect, useRef, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useQueryClient} from '@tanstack/react-query';
import {instrumentsApi} from '@/features/instruments/api/instruments';
import {Alert, App, Button, Space, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {evaluationsApi} from '@/api/evaluations';
import {publishesApi} from '@/features/publishes/api/publishes';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {
    validationReviewApi,
    parseReviewOperationTarget,
    confirmOriginalReviewEvent,
} from '@/features/validation/api/validation-review';
import {validationReviewQueryKeys, paperTradingQueryKeys} from '@/api/query-keys';
import {
    clearResultAction,
    originalResult,
    isResultActionInFlight,
    queryResultAction,
    readResultAction,
    resultActionChanged,
    type ResultAction,
} from './research-result-recovery';

export function usePendingResultAction(kind: ResultAction) {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [, refresh] = useState(0);
    useEffect(() => {
        const changed = () => refresh((value) => value + 1);
        window.addEventListener(resultActionChanged, changed);
        window.addEventListener('storage', changed);
        return () => {
            window.removeEventListener(resultActionChanged, changed);
            window.removeEventListener('storage', changed);
        };
    }, []);
    return readResultAction(owner, kind);
}
/** 恢复只读原运行；评估缺少原请求回执时保持未知，不把当前报告冒充原操作结果。 */
export function ResearchResultRecovery({kind}: {kind: ResultAction}) {
    const {t} = useTranslation('pages');
    const {message} = App.useApp();
    const navigate = useNavigate();
    const cache = useQueryClient();
    const pending = usePendingResultAction(kind);
    const [busy, setBusy] = useState(false);
    const [feedback, setFeedback] = useState<{
        owner: number;
        recordedAt: number;
        diagnostic: string;
        link: string;
        scopeEpoch?: number;
    } | null>(null);
    const location = useLocation();
    const sessionToken = useAuthStore(selectSessionIdentity);
    const sessionOwner = useAuthStore((state) => state.currentUser?.userId);
    const emergencyScope = useRef({
        active: true,
        epoch: 0,
        route: location.key,
        token: sessionToken,
        owner: sessionOwner,
        kind,
    });
    const scope = emergencyScope.current;
    if (
        scope.route !== location.key ||
        scope.token !== sessionToken ||
        scope.owner !== sessionOwner ||
        scope.kind !== kind
    ) {
        scope.epoch += 1;
        Object.assign(scope, {route: location.key, token: sessionToken, owner: sessionOwner, kind});
    }
    useEffect(() => {
        emergencyScope.current.active = true;
        return () => {
            emergencyScope.current.active = false;
            emergencyScope.current.epoch += 1;
        };
    }, []);
    useEffect(() => {
        if (kind === 'PAPER_EMERGENCY') {
            setBusy(false);
            setFeedback(null);
        }
    }, [kind, location.key, sessionToken, sessionOwner]);
    const diagnostic =
        pending &&
        pending !== 'UNAVAILABLE' &&
        feedback?.owner === pending.owner &&
        feedback.recordedAt === pending.recordedAt &&
        (kind !== 'PAPER_EMERGENCY' || feedback.scopeEpoch === scope.epoch)
            ? feedback.diagnostic
            : '';
    const resultLink = diagnostic ? feedback?.link : '';
    const sending = Boolean(
        pending && pending !== 'UNAVAILABLE' && isResultActionInFlight(pending),
    );
    if (!pending) return null;
    const recover = async () => {
        if (pending === 'UNAVAILABLE') return;
        const epoch = emergencyScope.current.epoch;
        const token = selectSessionIdentity(useAuthStore.getState());
        // 旧GET在锁释放后仍可能延迟交付；主体、会话、路由及组件生命周期都必须保持原范围。
        const currentScope = () =>
            kind !== 'PAPER_EMERGENCY' ||
            (emergencyScope.current.active &&
                emergencyScope.current.epoch === epoch &&
                selectSessionIdentity(useAuthStore.getState()) === token &&
                useAuthStore.getState().currentUser?.userId === pending.owner);
        const currentOriginal = () =>
            kind !== 'PAPER_EMERGENCY' ||
            (currentScope() &&
                JSON.stringify(readResultAction(pending.owner, kind)) === JSON.stringify(pending));
        setBusy(true);
        setFeedback(null);
        try {
            if (kind === 'PUBLISH') {
                const result = await queryResultAction(pending, () =>
                    publishesApi.forRun(pending.target),
                );
                if (useAuthStore.getState().currentUser?.userId !== pending.owner) return;
                if (
                    result.backtestRunId !== pending.target ||
                    !result.publishRecordId ||
                    result.publishStatus !== 'SUCCEEDED' ||
                    !result.targetStrategyDefinitionId ||
                    (result.strategyVersionId ?? '') !== (pending.strategyVersionId ?? '')
                )
                    throw new Error(t('resultRecovery.unconfirmed'));
                clearResultAction(pending);
                message.success(t('resultRecovery.publicationRecovered'));
            } else {
                // 旧版本只保存范围的记录没有服务端UUID，不能事后生成身份冒领结果。
                if (!pending.requestId) throw new Error(t('resultRecovery.legacyUnconfirmed'));
                if (kind === 'EVALUATE') {
                    const receipt = await queryResultAction(pending, () =>
                        evaluationsApi.requestResult(pending.requestId!),
                    );
                    const result = originalResult(receipt, pending.requestId, pending.target);
                    if (useAuthStore.getState().currentUser?.userId !== pending.owner) return;
                    if (
                        !result.evalReportId ||
                        result.backtestRunId !== pending.target ||
                        result.evaluationStatus !== receipt.state
                    )
                        throw new Error(t('resultRecovery.unconfirmed'));
                    navigate(
                        `/evaluations?evaluationRequestId=${encodeURIComponent(pending.requestId)}`,
                    );
                    clearResultAction(pending);
                    if (receipt.state === 'FAILED') message.warning(t('resultRecovery.failed'));
                    else message.success(t('resultRecovery.originalRecovered'));
                } else if (kind === 'PAPER_EMERGENCY') {
                    const event = await queryResultAction(pending, () =>
                        paperTradingApi.emergencyStopOriginalResult(
                            pending.target,
                            pending.requestId!,
                        ),
                    );
                    if (!currentOriginal()) return;
                    clearResultAction(pending);
                    setBusy(false);
                    if (!currentScope()) return;
                    // 成功提示先于后续缓存等待；等待期间换主体不能收到旧停机结果提示。
                    if (event.status === 'APPLIED') message.success(t('emergencyStopCompleted'));
                    else message.warning(t('emergencyStopNotApplied'));
                    navigate(
                        `/paper-trading/runs?paperRunId=${encodeURIComponent(pending.target)}`,
                    );
                    await cache.invalidateQueries({queryKey: paperTradingQueryKeys.all});
                } else if (kind === 'VALIDATION_REVIEW') {
                    const scope = parseReviewOperationTarget(pending.target);
                    const event = await queryResultAction(pending, () =>
                        validationReviewApi.originalResult(scope.caseId, pending.requestId!),
                    );
                    confirmOriginalReviewEvent(event, pending.target, pending.owner);
                    if (useAuthStore.getState().currentUser?.userId !== pending.owner) return;
                    clearResultAction(pending);
                    // 仅刷新当前正式状态，不把原事件的历史状态写回最新 case 缓存。
                    await cache.invalidateQueries({queryKey: validationReviewQueryKeys.all});
                    message.success(t('resultRecovery.originalRecovered'));
                } else {
                    const receipt = await queryResultAction(pending, () =>
                        instrumentsApi.syncRequestResult(pending.requestId!),
                    );
                    originalResult(receipt, pending.requestId, pending.target);
                    if (useAuthStore.getState().currentUser?.userId !== pending.owner) return;
                    clearResultAction(pending);
                    message.success(t('resultRecovery.originalRecovered'));
                    await cache.invalidateQueries({queryKey: ['instruments']});
                }
            }
        } catch (error) {
            if (useAuthStore.getState().currentUser?.userId === pending.owner && currentOriginal())
                setFeedback({
                    owner: pending.owner,
                    recordedAt: pending.recordedAt,
                    scopeEpoch: epoch,
                    link: '',
                    diagnostic:
                        (error as AppApiError).status !== undefined
                            ? formatApiError(error as AppApiError)
                            : (error as Error).message,
                });
        } finally {
            if (currentOriginal()) setBusy(false);
        }
    };
    return (
        <Alert
            type="warning"
            showIcon
            message={t(sending ? 'resultRecovery.submitting' : 'resultRecovery.unknown')}
            description={
                <Space direction="vertical">
                    <Typography.Text>
                        {pending === 'UNAVAILABLE'
                            ? t('resultRecovery.storage')
                            : t('resultRecovery.retained', {target: pending.target})}
                    </Typography.Text>
                    {!sending &&
                    kind !== 'PUBLISH' &&
                    pending !== 'UNAVAILABLE' &&
                    !pending.requestId ? (
                        <Typography.Text>{t('resultRecovery.legacyUnconfirmed')}</Typography.Text>
                    ) : (
                        pending !== 'UNAVAILABLE' &&
                        !sending && (
                            <Button loading={busy} onClick={recover}>
                                {t('resultRecovery.query')}
                            </Button>
                        )
                    )}
                    {diagnostic && <Typography.Text role="status">{diagnostic}</Typography.Text>}
                    {resultLink && (
                        <Typography.Link href={resultLink}>
                            {t('resultRecovery.viewCurrent')}
                        </Typography.Link>
                    )}
                </Space>
            }
        />
    );
}
