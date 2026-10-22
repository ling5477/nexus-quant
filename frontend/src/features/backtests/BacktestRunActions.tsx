import {canWriteResearch} from '@/utils/distribution';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {Alert, App, Button, Card, Space, Typography} from 'antd';
import {useRef, useState} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {ResearchResultRecovery, usePendingResultAction} from '@/features/ResearchResultRecovery';
import {backtestsApi} from './backtestsApi';
import type {BacktestRunDetailItem} from './backtestsTypes';
import {evaluationsApi} from '@/api/evaluations';
import {publishesApi} from '@/features/publishes/api/publishes';
import {backtestsQueryKeys, evaluationsQueryKeys, publishesQueryKeys} from '@/api/query-keys';
import {formatApiError, showApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {useAuthStore} from '@/store/auth-store';
import type {BacktestPublishDetailItem} from '@/features/publishes/types/publishes';

/** 只恢复当前运行的原身份；损坏字段或不可编码的导航身份不得使页面崩溃。 */
function publicationMatchesRun(
    value: BacktestPublishDetailItem | undefined,
    run: BacktestRunDetailItem,
): boolean {
    if (
        !value ||
        value.backtestRunId !== run.backtestRunId ||
        value.backtestConfigId !== run.backtestConfigId ||
        typeof value.publishRecordId !== 'string' ||
        !value.publishRecordId.trim() ||
        typeof value.publishStatus !== 'string' ||
        !value.publishStatus.trim() ||
        (value.failureCode != null && typeof value.failureCode !== 'string')
    )
        return false;
    try {
        encodeURIComponent(value.publishRecordId);
        return true;
    } catch {
        return false;
    }
}

/** 只连接已有的显式用户动作；状态与发布许可由正式后端重新校验。 */
export function BacktestRunActions({
    run,
    onChanged,
}: {
    run: BacktestRunDetailItem;
    onChanged: () => void;
}) {
    const {t} = useTranslation('pages');
    const {message} = App.useApp();
    const cache = useQueryClient();
    const pendingEvaluation = usePendingResultAction('EVALUATE');
    const canWrite = useAuthStore(
        (state) =>
            canWriteResearch(state.currentUser?.roles),
    );
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    const scope = `${owner ?? ''}/${run.backtestConfigId}/${run.backtestRunId}`;
    const [publishAttemptScope, setPublishAttemptScope] = useState<string | null>(null);
    const actionRequestInFlight = useRef(false);
    const mutation = useMutation({
        onMutate: (action: 'start' | 'evaluate' | 'publish') => {
            if (action === 'publish') setPublishAttemptScope(scope);
        },
        mutationFn: async (action: 'start' | 'evaluate' | 'publish') => {
            if (action === 'start') return backtestsApi.startRun(run.backtestRunId);
            if (action === 'evaluate') return evaluationsApi.evaluate(run.backtestRunId);
            return publishesApi.publish(run.backtestRunId, {
                strategyVersionId: run.strategyVersionId ?? undefined,
            });
        },
        onSuccess: async (result, action) => {
            if (
                action === 'publish' &&
                'publishStatus' in result &&
                result.publishStatus !== 'SUCCEEDED'
            ) {
                message.warning(t('pages:workflowPublishNotSucceeded'));
            } else if (
                action === 'evaluate' &&
                'evaluationStatus' in result &&
                result.evaluationStatus !== 'SUCCEEDED'
            ) {
                message.warning(t('pages:resultRecovery.failed'));
            } else {
                message.success(t('pages:workflowActionCompleted'));
            }
            await Promise.all([
                cache.invalidateQueries({queryKey: backtestsQueryKeys.all}),
                cache.invalidateQueries({queryKey: evaluationsQueryKeys.all}),
                cache.invalidateQueries({queryKey: publishesQueryKeys.all}),
            ]);
            onChanged();
        },
        onError: async (error) => {
            showApiError(error as AppApiError, message);
            // 启动失败也可能已持久化FAILED，写响应丢失也不能推断未执行；先重读原运行再恢复动作。
            // 这里只使正式查询失效，不重发任何启动、评估或发布命令，也不生成替代身份。
            await Promise.all([
                cache.invalidateQueries({queryKey: backtestsQueryKeys.all}),
                cache.invalidateQueries({queryKey: evaluationsQueryKeys.all}),
                cache.invalidateQueries({queryKey: publishesQueryKeys.all}),
            ]);
            onChanged();
        },
    });
    const perform = (action: 'start' | 'evaluate' | 'publish') => {
        // 异步loading不能阻止同一帧双击；只发送一次用户动作，结果仍由正式查询确认。
        if (actionRequestInFlight.current) return;
        actionRequestInFlight.current = true;
        mutation.mutate(action, {
            onSettled: () => {
                actionRequestInFlight.current = false;
            },
        });
    };
    const shouldReadPublish = Boolean(run.publishStatus) || publishAttemptScope === scope;
    const originalPublish = useQuery({
        queryKey: publishesQueryKeys.forRun(owner, run.backtestRunId),
        queryFn: ({signal}) => publishesApi.forRun(run.backtestRunId, signal),
        enabled: Boolean(owner && owner > 0) && shouldReadPublish && !mutation.isPending,
        retry: false,
    });
    // 禁用查询不清除缓存；只有当前主体/运行查询的精确父子身份匹配时，才恢复原发布入口。
    const publishMatches = publicationMatchesRun(originalPublish.data, run);
    const publishedResult =
        shouldReadPublish && !originalPublish.isError && publishMatches
            ? originalPublish.data
            : null;
    const lookupFailed =
        shouldReadPublish &&
        (originalPublish.isError || (originalPublish.isSuccess && !publishMatches));
    const publishBlocked =
        shouldReadPublish &&
        (publishedResult?.publishStatus !== 'FAILED' || run.publishStatus === 'SUCCEEDED');
    const succeeded = run.status === 'SUCCEEDED';
    return (
        <Card size="small" title={t('pages:workflowNextStep')} data-testid="backtest-run-actions">
            <Space direction="vertical" style={{width: '100%'}}>
                <ResearchResultRecovery kind="EVALUATE" />
                <Typography.Text copyable>{run.backtestRunId}</Typography.Text>
                <Space wrap>
                    <Button
                        type="primary"
                        disabled={!canWrite || run.status !== 'CREATED' || mutation.isPending}
                        loading={mutation.isPending && mutation.variables === 'start'}
                        onClick={() => perform('start')}
                    >
                        {t('pages:workflowStartBacktest')}
                    </Button>
                    <Button
                        disabled={
                            !canWrite ||
                            !succeeded ||
                            mutation.isPending ||
                            Boolean(pendingEvaluation)
                        }
                        loading={mutation.isPending && mutation.variables === 'evaluate'}
                        onClick={() => perform('evaluate')}
                    >
                        {t('pages:workflowEvaluateRun')}
                    </Button>
                    <Button
                        disabled={
                            !canWrite ||
                            !succeeded ||
                            run.evaluationStatus !== 'SUCCEEDED' ||
                            publishBlocked ||
                            owner == null ||
                            owner <= 0 ||
                            mutation.isPending
                        }
                        loading={mutation.isPending && mutation.variables === 'publish'}
                        onClick={() => perform('publish')}
                    >
                        {t('pages:workflowPublishRun')}
                    </Button>
                    <Link
                        to={`/backtests/${encodeURIComponent(run.backtestConfigId)}?backtestRunId=${encodeURIComponent(run.backtestRunId)}`}
                    >
                        {t('pages:visualization')}
                    </Link>
                </Space>
                <Typography.Text type="secondary">
                    {t('pages:workflowBacktestPrerequisites')}
                </Typography.Text>
                {run.status === 'FAILED' && (
                    <Alert
                        type="error"
                        message={t('pages:workflowRunFailed')}
                        description={run.failureCode ?? t('pages:workflowInspectFailure')}
                    />
                )}
                {lookupFailed && (
                    <Alert
                        type="error"
                        data-testid="published-result-recovery"
                        message={t('pages:workflowPublishReadFailed')}
                        description={
                            originalPublish.isError
                                ? formatApiError(originalPublish.error as AppApiError)
                                : t('pages:workflowPublishSourceMismatch')
                        }
                        action={
                            <Button
                                loading={originalPublish.isFetching}
                                onClick={() => void originalPublish.refetch()}
                            >
                                {t('pages:workflowReloadPublish')}
                            </Button>
                        }
                    />
                )}
                {shouldReadPublish && !publishedResult && !lookupFailed && !mutation.isPending && (
                    <Alert
                        type="info"
                        data-testid="published-result-recovery"
                        message={t('pages:workflowReadingPublish')}
                    />
                )}
                {publishedResult && (
                    <Alert
                        type={publishedResult.publishStatus === 'SUCCEEDED' ? 'success' : 'warning'}
                        message={
                            publishedResult.publishStatus === 'SUCCEEDED'
                                ? t('pages:workflowPublished')
                                : t('pages:workflowPublishNotSucceeded')
                        }
                        description={
                            <Space wrap>
                                <Typography.Text copyable>
                                    {publishedResult.publishRecordId}
                                </Typography.Text>
                                {publishedResult.publishStatus === 'SUCCEEDED' ? (
                                    <Link
                                        to={`/paper-trading/runs?publishId=${encodeURIComponent(publishedResult.publishRecordId)}`}
                                    >
                                        {t('pages:workflowOpenSim')}
                                    </Link>
                                ) : (
                                    <Typography.Text>
                                        {publishedResult.publishStatus} ·{' '}
                                        {publishedResult.failureCode ?? '—'}
                                    </Typography.Text>
                                )}
                            </Space>
                        }
                    />
                )}
            </Space>
        </Card>
    );
}
