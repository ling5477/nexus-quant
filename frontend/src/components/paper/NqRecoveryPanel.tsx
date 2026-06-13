import {App, Button, Card, Space} from 'antd';

import {formatApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, NqStatusTag} from '@/components/nq';
import {
    usePaperRecoveryEventsQuery,
    useRecoverMutation,
    useRetryFailedStepMutation,
    useRunMonitorOnceMutation,
} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunRecoveryEventItem} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/**
 * NqRecoveryPanel — Paper Run 恢复 / 监控守护面板。
 *
 * 职责：展示恢复事件，支持手动恢复、重试失败步骤、执行一次监控守护（会按运行态生成告警）。
 * 边界：复用既有 paper-trading 恢复接口，不新增 API；这些操作只作用于 SIM/Paper，不触发真实 LIVE。
 */
interface NqRecoveryPanelProps {
    paperRunId: string;
}

export function NqRecoveryPanel({paperRunId}: NqRecoveryPanelProps) {
    const {message} = App.useApp();
    const recoveryEventsQuery = usePaperRecoveryEventsQuery(paperRunId);
    const recoverMutation = useRecoverMutation();
    const retryFailedStepMutation = useRetryFailedStepMutation();
    const runMonitorOnceMutation = useRunMonitorOnceMutation();

    const data = recoveryEventsQuery.data ?? [];

    return (
        <Card
            className="page-section"
            size="small"
            title="恢复事件"
            extra={(
                <Space size={4} wrap>
                    <Button
                        size="small" type="primary" ghost loading={recoverMutation.isPending}
                        onClick={() => recoverMutation.mutate(
                            {paperRunId, request: {reason: '手动恢复测试'}},
                            {onSuccess: () => message.success('已记录恢复事件。'), onError: (err) => message.error(formatApiError(err as AppApiError))},
                        )}
                    >
                        执行恢复
                    </Button>
                    <Button
                        size="small" loading={retryFailedStepMutation.isPending}
                        onClick={() => retryFailedStepMutation.mutate(
                            {paperRunId, request: {failedStep: 'manual-test', reason: '手动重试测试'}},
                            {onSuccess: () => message.success('已记录重试事件。'), onError: (err) => message.error(formatApiError(err as AppApiError))},
                        )}
                    >
                        重试失败步骤
                    </Button>
                    <Button
                        size="small" loading={runMonitorOnceMutation.isPending}
                        onClick={() => runMonitorOnceMutation.mutate(
                            {paperRunId},
                            {
                                onSuccess: (result) => message.success(`监控守护已执行，新建告警 ${result.createdAlertCount} 条。`),
                                onError: (err) => message.error(formatApiError(err as AppApiError)),
                            },
                        )}
                    >
                        执行监控守护
                    </Button>
                </Space>
            )}
        >
            {recoveryEventsQuery.isFetching && data.length === 0 ? (
                <NqLoadingState/>
            ) : recoveryEventsQuery.error ? (
                <NqErrorState error={recoveryEventsQuery.error as AppApiError} onRetry={() => recoveryEventsQuery.refetch()}/>
            ) : data.length === 0 ? (
                <NqEmptyState description="当前 Paper run 暂无恢复事件。"/>
            ) : (
                <NqDataTable<PaperRunRecoveryEventItem>
                    rowKey="recoveryEventId"
                    pagination={false}
                    dataSource={data}
                    scroll={{y: 240}}
                    columns={[
                        {title: '类型', dataIndex: 'recoveryType', key: 'recoveryType', width: 180},
                        {title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (v: string) => <NqStatusTag status={v} tone={v === 'SUCCEEDED' ? 'success' : v === 'FAILED' ? 'danger' : v === 'SKIPPED' ? 'neutral' : 'info'}/>},
                        {title: '原因', dataIndex: 'reason', key: 'reason'},
                        {title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 170, render: (v: string) => formatDateTime(v)},
                        {title: '完成时间', dataIndex: 'finishedAt', key: 'finishedAt', width: 170, render: (v: string | null) => (v ? formatDateTime(v) : '-')},
                    ]}
                />
            )}
        </Card>
    );
}
