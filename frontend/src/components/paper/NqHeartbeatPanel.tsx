import {App, Button, Card} from 'antd';

import {formatApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, NqStatusTag, nqNumericColumn} from '@/components/nq';
import {usePaperHeartbeatsQuery, useRunHeartbeatOnceMutation} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunHeartbeatItem} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/**
 * NqHeartbeatPanel — Paper Run 心跳面板。
 *
 * 职责：展示心跳记录，支持执行一次心跳检查（写入一条心跳）。
 * 边界：复用既有心跳接口，不新增 API；心跳 STOPPED 视为危险态高亮。
 */
interface NqHeartbeatPanelProps {
    paperRunId: string;
}

export function NqHeartbeatPanel({paperRunId}: NqHeartbeatPanelProps) {
    const {message} = App.useApp();
    const heartbeatsQuery = usePaperHeartbeatsQuery(paperRunId);
    const runHeartbeatOnceMutation = useRunHeartbeatOnceMutation();

    const data = heartbeatsQuery.data ?? [];

    return (
        <Card
            className="page-section"
            size="small"
            title="心跳"
            extra={(
                <Button
                    size="small"
                    loading={runHeartbeatOnceMutation.isPending}
                    onClick={() => runHeartbeatOnceMutation.mutate(paperRunId, {
                        onSuccess: () => message.success('心跳已记录。'),
                        onError: (err) => message.error(formatApiError(err as AppApiError)),
                    })}
                >
                    执行心跳检查
                </Button>
            )}
        >
            {heartbeatsQuery.isFetching && data.length === 0 ? (
                <NqLoadingState/>
            ) : heartbeatsQuery.error ? (
                <NqErrorState error={heartbeatsQuery.error as AppApiError} onRetry={() => heartbeatsQuery.refetch()}/>
            ) : data.length === 0 ? (
                <NqEmptyState description="当前 Paper run 暂无心跳记录。"/>
            ) : (
                <NqDataTable<PaperRunHeartbeatItem>
                    rowKey="heartbeatId"
                    pagination={false}
                    dataSource={data}
                    scroll={{y: 240}}
                    columns={[
                        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'STOPPED' ? 'danger' : undefined}/>},
                        {title: '心跳时间', dataIndex: 'heartbeatTime', key: 'heartbeatTime', width: 170, render: (v: string) => formatDateTime(v)},
                        nqNumericColumn({title: '延迟(s)', dataIndex: 'lagSeconds', key: 'lagSeconds', width: 90}),
                        {title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                    ]}
                />
            )}
        </Card>
    );
}
