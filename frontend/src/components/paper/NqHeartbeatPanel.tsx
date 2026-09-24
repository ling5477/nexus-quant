import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {App, Button, Card} from 'antd';

import {showApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, nqNumericColumn} from '@/components/nq';
import {usePaperHeartbeatsQuery, useRunHeartbeatOnceMutation} from '@/features/paper-trading/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunHeartbeatItem} from '@/features/paper-trading/types/paper-trading';
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
    useTranslation('pages');
    const {message} = App.useApp();
    const heartbeatsQuery = usePaperHeartbeatsQuery(paperRunId);
    const runHeartbeatOnceMutation = useRunHeartbeatOnceMutation();

    const data = heartbeatsQuery.data ?? [];

    return (
        <Card
            className="page-section"
            size="small"
            title={t('pages:heartbeat')}
            extra={(
                <Button
                    size="small"
                    loading={runHeartbeatOnceMutation.isPending}
                    onClick={() => runHeartbeatOnceMutation.mutate(paperRunId, {
                        onSuccess: () => message.success(t('pages:heartbeatRecorded')),
                        onError: (err) => showApiError(err as AppApiError, message),
                    })}
                >
                    {t('pages:runHeartbeatCheck')}</Button>
            )}
        >
            {heartbeatsQuery.isFetching && data.length === 0 ? (
                <NqLoadingState/>
            ) : heartbeatsQuery.error ? (
                <NqErrorState error={heartbeatsQuery.error as AppApiError} onRetry={() => heartbeatsQuery.refetch()}/>
            ) : data.length === 0 ? (
                <NqEmptyState description={t('pages:noHeartbeatRecordsForThisPaperRun')}/>
            ) : (
                <NqDataTable<PaperRunHeartbeatItem>
                    rowKey="heartbeatId"
                    pagination={false}
                    dataSource={data}
                    scroll={{y: 240}}
                    columns={[
                        {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <StatusTag title="" variant="pill" status={v} tone={v === 'STOPPED' ? 'danger' : undefined}/>},
                        {title: t('pages:heartbeatTime'), dataIndex: 'heartbeatTime', key: 'heartbeatTime', width: 170, render: (v: string) => formatDateTime(v)},
                        nqNumericColumn({title: t('pages:latencyS'), dataIndex: 'lagSeconds', key: 'lagSeconds', width: 90}),
                        {title: t('pages:createdAt'), dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                    ]}
                />
            )}
        </Card>
    );
}
