import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Alert, App, Button, Card, Space, Typography} from 'antd';
import {useState} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {backtestsApi} from './backtestsApi';
import type {BacktestRunDetailItem} from './backtestsTypes';
import {evaluationsApi} from '@/api/evaluations';
import {publishesApi} from '@/features/publishes/api/publishes';
import {backtestsQueryKeys, evaluationsQueryKeys, publishesQueryKeys} from '@/api/query-keys';
import {showApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import type {BacktestPublishDetailItem} from '@/features/publishes/types/publishes';

/** 只连接已有的显式用户动作；状态与发布许可由正式后端重新校验。 */
export function BacktestRunActions({run, onChanged}: {
    run: BacktestRunDetailItem; onChanged: () => void;
}) {
    const {t} = useTranslation('pages');
    const {message} = App.useApp();
    const cache = useQueryClient();
    const [publishedResult, setPublishedResult] = useState<BacktestPublishDetailItem | null>(null);
    const mutation = useMutation({
        mutationFn: async (action: 'start' | 'evaluate' | 'publish') => {
            if (action === 'start') return backtestsApi.startRun(run.backtestRunId);
            if (action === 'evaluate') return evaluationsApi.evaluate(run.backtestRunId);
            const published = await publishesApi.publish(run.backtestRunId, {
                strategyVersionId: run.strategyVersionId ?? undefined,
            });
            setPublishedResult(published);
            return published;
        },
        onSuccess: async (result, action) => {
            if (action === 'publish' && 'publishStatus' in result && result.publishStatus !== 'SUCCEEDED') {
                message.warning(t('pages:workflowPublishNotSucceeded'));
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
        onError: (error) => showApiError(error as AppApiError, message),
    });
    const succeeded = run.status === 'SUCCEEDED';
    return <Card size="small" title={t('pages:workflowNextStep')} data-testid="backtest-run-actions">
        <Space direction="vertical" style={{width: '100%'}}>
            <Typography.Text copyable>{run.backtestRunId}</Typography.Text>
            <Space wrap>
                <Button type="primary" disabled={run.status !== 'CREATED' || mutation.isPending}
                    loading={mutation.isPending && mutation.variables === 'start'}
                    onClick={() => mutation.mutate('start')}>{t('pages:workflowStartBacktest')}</Button>
                <Button disabled={!succeeded || mutation.isPending}
                    loading={mutation.isPending && mutation.variables === 'evaluate'}
                    onClick={() => mutation.mutate('evaluate')}>{t('pages:workflowEvaluateRun')}</Button>
                <Button disabled={!succeeded || run.evaluationStatus !== 'SUCCEEDED'
                    || run.publishStatus === 'SUCCEEDED' || mutation.isPending}
                    loading={mutation.isPending && mutation.variables === 'publish'}
                    onClick={() => mutation.mutate('publish')}>{t('pages:workflowPublishRun')}</Button>
                <Link to={`/backtests/${encodeURIComponent(run.backtestConfigId)}?backtestRunId=${encodeURIComponent(run.backtestRunId)}`}>
                    {t('pages:visualization')}
                </Link>
            </Space>
            <Typography.Text type="secondary">{t('pages:workflowBacktestPrerequisites')}</Typography.Text>
            {run.status === 'FAILED' && <Alert type="error" message={t('pages:workflowRunFailed')}
                description={run.failureCode ?? t('pages:workflowInspectFailure')}/>}
            {publishedResult && <Alert type={publishedResult.publishStatus === 'SUCCEEDED' ? 'success' : 'warning'}
                message={publishedResult.publishStatus === 'SUCCEEDED' ? t('pages:workflowPublished') : t('pages:workflowPublishNotSucceeded')}
                description={<Space wrap><Typography.Text copyable>{publishedResult.publishRecordId}</Typography.Text>
                    {publishedResult.publishStatus === 'SUCCEEDED'
                        ? <Link to={`/paper-trading/runs?publishId=${encodeURIComponent(publishedResult.publishRecordId)}`}>{t('pages:workflowOpenSim')}</Link>
                        : <Typography.Text>{publishedResult.publishStatus} · {publishedResult.failureCode ?? '—'}</Typography.Text>}
                </Space>}/>}
        </Space>
    </Card>;
}
