import {useEffect, useRef, useState, type Key} from 'react';
import {useTranslation} from 'react-i18next';
import {Alert, App, Button, Card, Descriptions, Space, Typography} from 'antd';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {paperTradingApi} from '../api/paper-trading';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState} from '@/components/nq';
import {
    useAckAlertMutation,
    useCreateAlertMutation,
    usePaperAlertsQuery,
    useResolveAlertMutation,
} from '../hooks/usePaperTradingQuery';
import {canOperatePaperAlerts} from '../paper-alert-operations';
import {paperAlertErrorMessage, usePendingPaperAlertOperation} from './PaperAlertOperationRecovery';
import type {AppApiError} from '@/types/api';
import type {PaperRunAlertItem} from '../types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/** 具体来源独立读取，不依赖列表命中；业务处理和通知已读保持独立。 */
export function NqAlertPanel({
    paperRunId,
    focusAlertId,
}: {
    paperRunId: string;
    focusAlertId?: string;
}) {
    const {t} = useTranslation('pages');
    const statusLabel = (status: string) =>
        t(`paperAlerts.status_${status}`, {defaultValue: t('paperAnalysisUnknownValue')});
    const {message, modal} = App.useApp();
    const queryClient = useQueryClient();
    const user = useAuthStore((state) => state.currentUser);
    const token = useAuthStore((state) => state.accessToken);
    const stored = usePendingPaperAlertOperation();
    const root = useRef<HTMLDivElement>(null);
    const [expanded, setExpanded] = useState<Key[]>(focusAlertId ? [focusAlertId] : []);
    const currentRun = useRef(paperRunId);
    currentRun.current = paperRunId;
    const alerts = usePaperAlertsQuery(paperRunId);
    const create = useCreateAlertMutation();
    const ack = useAckAlertMutation();
    const resolve = useResolveAlertMutation();
    const source = useQuery({
        queryKey: [
            ...paperTradingQueryKeys.all,
            'alert-source',
            user?.userId,
            paperRunId,
            focusAlertId,
        ],
        queryFn: ({signal}) => paperTradingApi.getAlert(paperRunId, focusAlertId!, signal),
        enabled: Boolean(focusAlertId),
        retry: false,
    });
    useEffect(() => {
        setExpanded(focusAlertId ? [focusAlertId] : []);
        if (focusAlertId) root.current?.scrollIntoView({block: 'start', behavior: 'auto'});
    }, [paperRunId, focusAlertId]);
    const busy = create.isPending || ack.isPending || resolve.isPending;
    const blocked = stored.kind !== 'none' || busy || !canOperatePaperAlerts();
    const data = (alerts.data ?? []).filter((item) => item.paperRunId === paperRunId);
    const currentSource =
        source.isSuccess && !source.isError && !source.isFetching ? source.data : null;
    const rows = currentSource
        ? [currentSource, ...data.filter((item) => item.alertId !== focusAlertId)]
        : data.filter((item) => item.alertId !== focusAlertId || !source.isError);
    const refresh = () => {
        void alerts.refetch();
        if (focusAlertId) void source.refetch();
    };
    const confirm = (type: 'CREATE' | 'ACK' | 'RESOLVE', record?: PaperRunAlertItem) => {
        const scope = {owner: user?.userId, token, paperRunId};
        modal.confirm({
            title: t('paperAlerts.confirmTitle', {action: t(`paperAlerts.${type}`)}),
            content: (
                <Space direction="vertical" style={{maxWidth: '100%'}}>
                    <Typography.Text>{t(`paperAlerts.meaning_${type}`)}</Typography.Text>
                    <Typography.Text style={{overflowWrap: 'anywhere'}}>
                        {paperRunId}
                    </Typography.Text>
                    {record && (
                        <>
                            <Typography.Text>{record.title}</Typography.Text>
                            <Typography.Text style={{overflowWrap: 'anywhere'}}>
                                {record.alertId}
                            </Typography.Text>
                        </>
                    )}
                </Space>
            ),
            okText: t(`paperAlerts.${type}`),
            cancelText: t('cancel'),
            onOk: async () => {
                // 弹窗打开后若切换主体，不能以新会话提交旧确认；服务端仍校验真实角色与来源权限。
                if (
                    currentRun.current !== scope.paperRunId ||
                    useAuthStore.getState().currentUser?.userId !== scope.owner ||
                    useAuthStore.getState().accessToken !== scope.token
                ) {
                    message.error(t('paperAlerts.sessionChanged'));
                    return;
                }
                try {
                    if (type === 'CREATE')
                        await create.mutateAsync({
                            paperRunId,
                            request: {
                                alertType: 'SYSTEM_NOTICE',
                                severity: 'LOW',
                                title: t('paperAlerts.testTitle'),
                                message: t('paperAlerts.testMessage'),
                                source: 'MANUAL',
                            },
                        });
                    else if (type === 'ACK')
                        await ack.mutateAsync({paperRunId, alertId: record!.alertId});
                    else await resolve.mutateAsync({paperRunId, alertId: record!.alertId});
                    message.success(t('paperAlerts.completed'));
                } catch (error) {
                    message.error(paperAlertErrorMessage(error, t));
                    // 冲突或超时只刷新现状，原编号仍由恢复区管理，不再自动发送写请求。
                    refresh();
                }
                void queryClient.invalidateQueries({queryKey: ['user-notifications']});
            },
        });
    };
    return (
        <div ref={root} data-testid="paper-alert-panel" style={{scrollMarginTop: 176}}>
            <Card
                className="page-section"
                size="small"
                title={t('alert')}
                extra={
                    <Space wrap>
                        <Button
                            size="small"
                            loading={alerts.isFetching || source.isFetching}
                            onClick={refresh}
                        >
                            {t('refresh')}
                        </Button>
                        <Button
                            size="small"
                            type="primary"
                            ghost
                            disabled={blocked}
                            loading={create.isPending}
                            onClick={() => confirm('CREATE')}
                        >
                            {t('createTestAlert')}
                        </Button>
                    </Space>
                }
            >
                <Typography.Paragraph type="secondary">
                    {t('paperAlerts.scope')}
                </Typography.Paragraph>
                {!canOperatePaperAlerts() && (
                    <Alert type="info" message={t('paperAlerts.permission')} showIcon />
                )}
                {focusAlertId && (
                    <Alert
                        type={source.isError ? 'error' : 'info'}
                        showIcon
                        message={t('paperAlerts.focused')}
                        description={
                            <Space direction="vertical" style={{width: '100%'}}>
                                <Typography.Text style={{overflowWrap: 'anywhere'}}>
                                    {focusAlertId}
                                </Typography.Text>
                                {source.isFetching
                                    ? t('paperAlerts.readingSource')
                                    : source.isError
                                      ? paperAlertErrorMessage(source.error, t)
                                      : t('paperAlerts.sourceMeaning')}
                            </Space>
                        }
                        style={{marginBottom: 12}}
                    />
                )}
                {alerts.isFetching && !alerts.data ? (
                    <NqLoadingState />
                ) : alerts.error ? (
                    <NqErrorState error={alerts.error as AppApiError} onRetry={refresh} />
                ) : null}
                {rows.length === 0 &&
                !alerts.isFetching &&
                !alerts.error &&
                !source.isFetching &&
                !source.isError ? (
                    <NqEmptyState description={t('noAlertsForThisPaperRun')} />
                ) : (
                    rows.length > 0 && (
                        <NqDataTable<PaperRunAlertItem>
                            rowKey="alertId"
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            dataSource={rows}
                            scroll={{x: 1000}}
                            expandable={{
                                expandedRowKeys: expanded,
                                onExpandedRowsChange: (keys) => setExpanded([...keys]),
                                expandedRowRender: (row) => (
                                    <Descriptions
                                        size="small"
                                        column={{xs: 1, md: 2}}
                                        layout="vertical"
                                    >
                                        <Descriptions.Item label={t('status')}>
                                            <StatusTag
                                                title=""
                                                variant="pill"
                                                status={row.status}
                                                label={statusLabel(row.status)}
                                                tone={
                                                    row.status === 'RESOLVED'
                                                        ? 'success'
                                                        : row.status === 'OPEN'
                                                          ? 'danger'
                                                          : row.status === 'ACKED'
                                                            ? 'warning'
                                                            : 'neutral'
                                                }
                                            />
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('paperAlerts.alertId')}>
                                            <Typography.Text
                                                copyable
                                                style={{overflowWrap: 'anywhere'}}
                                            >
                                                {row.alertId}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('message')}>
                                            {row.message ?? '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('paperAlerts.acknowledgedBy')}>
                                            {row.acknowledgedBy ?? '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('paperAlerts.acknowledgedAt')}>
                                            {row.acknowledgedAt
                                                ? formatDateTime(row.acknowledgedAt)
                                                : '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('paperAlerts.resolvedAt')}>
                                            {row.resolvedAt ? formatDateTime(row.resolvedAt) : '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('updatedAt')}>
                                            {formatDateTime(row.updatedAt)}
                                        </Descriptions.Item>
                                    </Descriptions>
                                ),
                            }}
                            columns={[
                                {title: t('title'), dataIndex: 'title', key: 'title', width: 200},
                                {title: t('type'), dataIndex: 'alertType', key: 'type', width: 150},
                                {
                                    title: t('severity'),
                                    dataIndex: 'severity',
                                    key: 'severity',
                                    width: 105,
                                    render: (value: string) => (
                                        <StatusTag
                                            title=""
                                            variant="pill"
                                            status={value}
                                            tone={
                                                ['CRITICAL', 'HIGH'].includes(value)
                                                    ? 'danger'
                                                    : value === 'MEDIUM'
                                                      ? 'warning'
                                                      : 'neutral'
                                            }
                                        />
                                    ),
                                },
                                {
                                    title: t('status'),
                                    dataIndex: 'status',
                                    key: 'status',
                                    width: 105,
                                    render: (value: string) => (
                                        <StatusTag
                                            title=""
                                            variant="pill"
                                            status={value}
                                            label={statusLabel(value)}
                                            tone={
                                                value === 'OPEN'
                                                    ? 'danger'
                                                    : value === 'ACKED'
                                                      ? 'warning'
                                                      : value === 'RESOLVED'
                                                        ? 'success'
                                                        : 'neutral'
                                            }
                                        />
                                    ),
                                },
                                {
                                    title: t('source'),
                                    dataIndex: 'source',
                                    key: 'source',
                                    width: 110,
                                },
                                {
                                    title: t('createdAt'),
                                    dataIndex: 'createdAt',
                                    key: 'createdAt',
                                    width: 185,
                                    render: (value: string) => formatDateTime(value),
                                },
                                {
                                    title: t('actions'),
                                    key: 'actions',
                                    width: 160,
                                    fixed: 'right',
                                    render: (_, row) => (
                                        <Space size={4}>
                                            {row.status === 'OPEN' && (
                                                <Button
                                                    type="link"
                                                    size="small"
                                                    disabled={
                                                        blocked ||
                                                        (row.alertId === focusAlertId &&
                                                            (source.isError || source.isFetching))
                                                    }
                                                    loading={
                                                        ack.isPending &&
                                                        ack.variables?.alertId === row.alertId
                                                    }
                                                    onClick={() => confirm('ACK', row)}
                                                >
                                                    {t('acknowledge')}
                                                </Button>
                                            )}
                                            {['OPEN', 'ACKED'].includes(row.status) && (
                                                <Button
                                                    type="link"
                                                    size="small"
                                                    disabled={
                                                        blocked ||
                                                        (row.alertId === focusAlertId &&
                                                            (source.isError || source.isFetching))
                                                    }
                                                    loading={
                                                        resolve.isPending &&
                                                        resolve.variables?.alertId === row.alertId
                                                    }
                                                    onClick={() => confirm('RESOLVE', row)}
                                                >
                                                    {t('resolve')}
                                                </Button>
                                            )}
                                        </Space>
                                    ),
                                },
                            ]}
                        />
                    )
                )}
            </Card>
        </div>
    );
}
