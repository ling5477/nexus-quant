import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n/index';
import {App, Button, Card, Space} from 'antd';

import {showApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState} from '@/components/nq/index';
import {
    useAckAlertMutation,
    useCreateAlertMutation,
    usePaperAlertsQuery,
    useResolveAlertMutation,
} from '@/features/paper-trading/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunAlertItem} from '@/features/paper-trading/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/**
 * NqAlertPanel — Paper Run 告警面板。
 *
 * 职责：展示焦点 run 的告警列表，支持创建测试告警、确认（ACK）、解决（RESOLVE）。
 * 边界：自包含既有 paper-trading 告警接口，不新增 API；危险/风险态（OPEN/CRITICAL）必须高亮可见。
 * 复用 React Query 缓存键，与控制台顶部状态条共享同一份数据，不会重复请求。
 */
interface NqAlertPanelProps {
    paperRunId: string;
}

export function NqAlertPanel({paperRunId}: NqAlertPanelProps) {
    useTranslation('pages');
    const {message} = App.useApp();
    const alertsQuery = usePaperAlertsQuery(paperRunId);
    const createAlertMutation = useCreateAlertMutation();
    const ackAlertMutation = useAckAlertMutation();
    const resolveAlertMutation = useResolveAlertMutation();

    const data = alertsQuery.data ?? [];

    return (
        <Card
            className="page-section"
            size="small"
            title={t('pages:alert')}
            extra={(
                <Button
                    size="small"
                    type="primary"
                    ghost
                    loading={createAlertMutation.isPending}
                    onClick={() => {
                        createAlertMutation.mutate(
                            {
                                paperRunId,
                                request: {alertType: 'SYSTEM_NOTICE', severity: 'LOW', title: '手动测试告警', message: '手动创建的测试告警', source: 'MANUAL'},
                            },
                            {
                                onSuccess: () => message.success(t('pages:alertCreated')),
                                onError: (err) => showApiError(err as AppApiError, message),
                            },
                        );
                    }}
                >
                    {t('pages:createTestAlert')}</Button>
            )}
        >
            {alertsQuery.isFetching && data.length === 0 ? (
                <NqLoadingState/>
            ) : alertsQuery.error ? (
                <NqErrorState error={alertsQuery.error as AppApiError} onRetry={() => alertsQuery.refetch()}/>
            ) : data.length === 0 ? (
                <NqEmptyState description={t('pages:noAlertsForThisPaperRun')}/>
            ) : (
                <NqDataTable<PaperRunAlertItem>
                    rowKey="alertId"
                    pagination={false}
                    dataSource={data}
                    scroll={{y: 240}}
                    columns={[
                        {title: t('pages:type'), dataIndex: 'alertType', key: 'alertType', width: 140},
                        {title: t('pages:severity'), dataIndex: 'severity', key: 'severity', width: 100, render: (v: string) => <StatusTag title="" variant="pill" status={v} tone={v === 'CRITICAL' || v === 'HIGH' ? 'danger' : v === 'MEDIUM' ? 'warning' : 'neutral'}/>},
                        {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <StatusTag title="" variant="pill" status={v} tone={v === 'OPEN' ? 'danger' : v === 'ACKED' ? 'warning' : 'success'}/>},
                        {title: t('pages:title'), dataIndex: 'title', key: 'title'},
                        {title: t('pages:source'), dataIndex: 'source', key: 'source', width: 100},
                        {title: t('pages:createdAt'), dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                        {
                            title: t('pages:actions'), key: 'action', width: 130, fixed: 'right',
                            render: (_, record) => (
                                <Space size={4}>
                                    {record.status === 'OPEN' && (
                                        <Button
                                            type="link" size="small" loading={ackAlertMutation.isPending}
                                            onClick={() => ackAlertMutation.mutate({paperRunId, alertId: record.alertId}, {onSuccess: () => message.success(t('pages:acknowledged'))})}
                                        >
                                            {t('pages:acknowledge')}</Button>
                                    )}
                                    {record.status !== 'RESOLVED' && (
                                        <Button
                                            type="link" size="small" loading={resolveAlertMutation.isPending}
                                            onClick={() => resolveAlertMutation.mutate({paperRunId, alertId: record.alertId}, {onSuccess: () => message.success(t('pages:resolved'))})}
                                        >
                                            {t('pages:resolve')}</Button>
                                    )}
                                </Space>
                            ),
                        },
                    ]}
                />
            )}
        </Card>
    );
}
