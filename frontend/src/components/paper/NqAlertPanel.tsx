import {App, Button, Card, Space} from 'antd';

import {formatApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, NqStatusTag} from '@/components/nq';
import {
    useAckAlertMutation,
    useCreateAlertMutation,
    usePaperAlertsQuery,
    useResolveAlertMutation,
} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunAlertItem} from '@/types/paper-trading';
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
            title="告警"
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
                                onSuccess: () => message.success('告警已创建。'),
                                onError: (err) => message.error(formatApiError(err as AppApiError)),
                            },
                        );
                    }}
                >
                    创建测试告警
                </Button>
            )}
        >
            {alertsQuery.isFetching && data.length === 0 ? (
                <NqLoadingState/>
            ) : alertsQuery.error ? (
                <NqErrorState error={alertsQuery.error as AppApiError} onRetry={() => alertsQuery.refetch()}/>
            ) : data.length === 0 ? (
                <NqEmptyState description="当前 Paper run 暂无告警。"/>
            ) : (
                <NqDataTable<PaperRunAlertItem>
                    rowKey="alertId"
                    pagination={false}
                    dataSource={data}
                    scroll={{y: 240}}
                    columns={[
                        {title: '类型', dataIndex: 'alertType', key: 'alertType', width: 140},
                        {title: '严重程度', dataIndex: 'severity', key: 'severity', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'CRITICAL' || v === 'HIGH' ? 'danger' : v === 'MEDIUM' ? 'warning' : 'neutral'}/>},
                        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'OPEN' ? 'danger' : v === 'ACKED' ? 'warning' : 'success'}/>},
                        {title: '标题', dataIndex: 'title', key: 'title'},
                        {title: '来源', dataIndex: 'source', key: 'source', width: 100},
                        {title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                        {
                            title: '操作', key: 'action', width: 130, fixed: 'right',
                            render: (_, record) => (
                                <Space size={4}>
                                    {record.status === 'OPEN' && (
                                        <Button
                                            type="link" size="small" loading={ackAlertMutation.isPending}
                                            onClick={() => ackAlertMutation.mutate({paperRunId, alertId: record.alertId}, {onSuccess: () => message.success('已确认。')})}
                                        >
                                            确认
                                        </Button>
                                    )}
                                    {record.status !== 'RESOLVED' && (
                                        <Button
                                            type="link" size="small" loading={resolveAlertMutation.isPending}
                                            onClick={() => resolveAlertMutation.mutate({paperRunId, alertId: record.alertId}, {onSuccess: () => message.success('已解决。')})}
                                        >
                                            解决
                                        </Button>
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
