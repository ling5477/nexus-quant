import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {Alert, App, Button, Card, Descriptions, InputNumber, Modal, Popconfirm, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {Link} from 'react-router-dom';

import {schedulerQueryKeys, operationalReadinessQueryKeys} from '@/api/query-keys';
import {formatApiError, showApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {schedulerApi} from '@/features/scheduler/api/scheduler';
import type {ScheduledJobControl, ScheduledJobPatch} from '@/features/scheduler/types';
import {operationalReadinessApi} from '@/features/runtime/api/operational-readiness';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';

const DEFERRED_JOBS = new Set([
    'STRATEGY_RECOVERY', 'OKX_RECOVERY', 'OKX_RECONCILIATION', 'BINANCE_RECONCILIATION',
]);

const JOB_NAMES: Record<string, string> = {
    CONTINUOUS_SIM_POLL: 'pages:schedulerJobContinuousSim',
    PAPER_MATCHING: 'pages:schedulerJobPaperMatching',
    STRATEGY_RECOVERY: 'pages:schedulerJobStrategyRecovery',
    LEDGER_RECONCILIATION: 'pages:schedulerJobLedgerReconciliation',
    OKX_RECOVERY: 'pages:schedulerJobOkxRecovery',
    OKX_RECONCILIATION: 'pages:schedulerJobOkxReconciliation',
    BINANCE_RECONCILIATION: 'pages:schedulerJobBinanceReconciliation',
    VALIDATION_EVIDENCE_REFRESH: 'pages:schedulerJobValidationEvidence',
};

function statusColor(status: ScheduledJobControl['lastStatus']): string {
    switch (status) {
        case 'SUCCESS': return 'success';
        case 'FAILED': return 'error';
        case 'RUNNING': return 'processing';
        case 'SKIPPED': return 'warning';
        default: return 'default';
    }
}

/** 服务端控制状态只通过 Query 刷新；静态不可启用提示不替代服务端资格检查。 */
export function SchedulerJobsPage() {
    const {t} = useTranslation();
    const {message} = App.useApp();
    const queryClient = useQueryClient();
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const canManage = roles.some((role) => ['ADMIN', 'ROLE_ADMIN'].includes(role.toUpperCase()));
    const [editing, setEditing] = useState<ScheduledJobControl | null>(null);
    const [delay, setDelay] = useState<number | null>(null);

    const jobs = useQuery({
        queryKey: schedulerQueryKeys.list(),
        queryFn: schedulerApi.list,
        refetchInterval: 5_000,
    });
    const readiness = useQuery({
        queryKey: operationalReadinessQueryKeys.status(),
        queryFn: operationalReadinessApi.getReadiness,
        refetchInterval: 30_000,
    });
    const patch = useMutation({
        mutationFn: ({jobKey, request}: {jobKey: string; request: ScheduledJobPatch}) =>
            schedulerApi.patch(jobKey, request),
        onSuccess: async () => {
            await queryClient.invalidateQueries({queryKey: schedulerQueryKeys.all});
            message.success(t('pages:schedulerChangeSaved'));
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });
    const run = useMutation({
        mutationFn: schedulerApi.runOnce,
        onSuccess: async (result) => {
            await queryClient.invalidateQueries({queryKey: schedulerQueryKeys.all});
            if (result.result === 'SUCCESS') message.success(t('pages:schedulerRunSuccess'));
            else message.warning(t('pages:schedulerRunNotSuccessful', {result: result.result}));
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });

    const columns: ColumnsType<ScheduledJobControl> = [
        {
            title: t('pages:schedulerJob'), dataIndex: 'jobKey', width: 270,
            render: (key: string) => <Space direction="vertical" size={0}>
                <Typography.Text strong>{t(JOB_NAMES[key] ?? key)}</Typography.Text>
                <Typography.Text type="secondary" copyable>{key}</Typography.Text>
            </Space>,
        },
        {
            title: t('pages:schedulerEnabled'), dataIndex: 'enabled', width: 110,
            render: (enabled: boolean, row) => <Space direction="vertical" size={0}>
                <Tag color={enabled ? 'success' : 'default'}>{enabled ? 'ON' : 'OFF'}</Tag>
                {DEFERRED_JOBS.has(row.jobKey) ? <Typography.Text type="secondary">
                    {t('pages:schedulerDeferred')}
                </Typography.Text> : null}
            </Space>,
        },
        {title: t('pages:schedulerFixedDelay'), dataIndex: 'fixedDelayMs', width: 125,
            render: (value: number) => `${value.toLocaleString()} ms`},
        {title: t('pages:schedulerNextRun'), dataIndex: 'nextRunAt', width: 170,
            render: (value: string | null) => formatDateTime(value)},
        {title: t('pages:schedulerLastRun'), dataIndex: 'lastFinishedAt', width: 190,
            render: (_: string | null, row) => <Space direction="vertical" size={0}>
                <Typography.Text>{formatDateTime(row.lastFinishedAt ?? row.lastStartedAt)}</Typography.Text>
                {row.lastStartedAt && row.lastFinishedAt ? <Typography.Text type="secondary">
                    {t('pages:schedulerStartedAt')}: {formatDateTime(row.lastStartedAt)}
                </Typography.Text> : null}
            </Space>},
        {title: t('pages:schedulerLastResult'), dataIndex: 'lastStatus', width: 190,
            render: (status: ScheduledJobControl['lastStatus'], row) => <Space direction="vertical" size={0}>
                <Tag color={statusColor(status)}>{status}</Tag>
                {row.lastErrorCode ? <Typography.Text type="danger" copyable>
                    {row.lastErrorCode}
                </Typography.Text> : null}
            </Space>},
        {title: t('pages:schedulerFailures'), dataIndex: 'consecutiveFailures', width: 90},
        {
            title: t('pages:actions'), key: 'actions', width: 260,
            render: (_: unknown, row) => canManage ? <Space wrap>
                <Popconfirm
                    title={row.enabled ? t('pages:schedulerConfirmDisable') : t('pages:schedulerConfirmEnable')}
                    description={row.jobKey}
                    onConfirm={() => patch.mutate({jobKey: row.jobKey,
                        request: {enabled: !row.enabled, expectedVersion: row.version}})}
                    okText={t('pages:schedulerConfirm')}
                    cancelText={t('pages:cancel')}
                >
                    <Button size="small" disabled={patch.isPending || (DEFERRED_JOBS.has(row.jobKey) && !row.enabled)}>
                        {row.enabled ? t('pages:schedulerTurnOff') : t('pages:schedulerTurnOn')}
                    </Button>
                </Popconfirm>
                <Button size="small" disabled={patch.isPending} onClick={() => {
                    setEditing(row);
                    setDelay(row.fixedDelayMs);
                }}>{t('pages:schedulerEditDelay')}</Button>
                <Popconfirm
                    title={t('pages:schedulerConfirmRun')}
                    description={row.jobKey}
                    onConfirm={() => run.mutate(row.jobKey)}
                    okText={t('pages:schedulerConfirm')}
                    cancelText={t('pages:cancel')}
                >
                    <Button size="small" disabled={run.isPending || DEFERRED_JOBS.has(row.jobKey)}>
                        {t('pages:schedulerRunOnce')}
                    </Button>
                </Popconfirm>
            </Space> : <Typography.Text type="secondary">{t('pages:schedulerReadOnly')}</Typography.Text>,
        },
    ];

    return <Space direction="vertical" size="middle" style={{width: '100%'}}>
        <NqPageHeader
            title={t('pages:schedulerTitle')}
            description={t('pages:schedulerDescription')}
            extra={<Button onClick={() => void jobs.refetch()} loading={jobs.isFetching}>
                {t('pages:schedulerRefresh')}
            </Button>}
        />
        <Alert type="warning" showIcon message={t('pages:schedulerSafetyTitle')}
            description={t('pages:schedulerSafetyDescription')}/>
        <Card size="small" title={t('pages:schedulerRuntimeBoundary')}>
            <Descriptions size="small" column={{xs: 1, sm: 2, md: 4}}>
                <Descriptions.Item label={t('pages:schedulerSimScope')}>SIM / Paper</Descriptions.Item>
                <Descriptions.Item label={t('pages:schedulerLiveStatus')}>
                    {readiness.data?.liveStatus.status ?? t('pages:schedulerUnknown')}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:schedulerProviderStatus')}>
                    {readiness.data?.realProviderStatus.status ?? t('pages:schedulerUnknown')}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:schedulerRiskKillStatus')}>
                    {t('pages:schedulerNotInstrumented')}
                </Descriptions.Item>
            </Descriptions>
            <Typography.Text type="secondary">
                {t('pages:schedulerReadinessSource')} {formatDateTime(readiness.data?.generatedAt)} · {' '}
                <Link to="/runtime/readiness">{t('pages:schedulerReadinessLink')}</Link>
            </Typography.Text>
            {readiness.isError ? <Alert type="warning" showIcon
                message={t('pages:schedulerReadinessUnavailable')}/> : null}
        </Card>
        {jobs.isError ? <Alert type="error" showIcon message={t('pages:schedulerLoadFailed')}
            description={formatApiError(jobs.error as AppApiError)}
            action={<Button onClick={() => void jobs.refetch()}>{t('pages:schedulerRetry')}</Button>}/> : null}
        <Card size="small" title={t('pages:schedulerJobsTitle')}>
            <Table rowKey="jobKey" columns={columns} dataSource={jobs.data ?? []}
                loading={jobs.isLoading} scroll={{x: 1400}} pagination={false}
                locale={{emptyText: jobs.isError ? t('pages:schedulerUnavailable') : t('pages:schedulerEmpty')}}/>
            {jobs.dataUpdatedAt > 0 ? <Typography.Text type="secondary">
                {t('pages:schedulerUpdatedAt')}: {formatDateTime(new Date(jobs.dataUpdatedAt).toISOString())}
            </Typography.Text> : null}
        </Card>
        <Modal title={t('pages:schedulerEditDelay')} open={editing !== null}
            onCancel={() => setEditing(null)}
            onOk={() => {
                if (!editing || delay === null || !Number.isInteger(delay)) return;
                patch.mutate({jobKey: editing.jobKey,
                    request: {fixedDelayMs: delay, expectedVersion: editing.version}},
                {onSuccess: () => setEditing(null)});
            }}
            okButtonProps={{disabled: delay === null || !Number.isInteger(delay), loading: patch.isPending}}
            okText={t('pages:save')} cancelText={t('pages:cancel')}>
            <Space direction="vertical" style={{width: '100%'}}>
                <Typography.Text>{editing?.jobKey}</Typography.Text>
                <Space.Compact style={{width: '100%'}}>
                    <InputNumber aria-label={t('pages:schedulerFixedDelay')} value={delay}
                        min={1000} max={86400000} precision={0} style={{width: '100%'}}
                        onChange={setDelay}/>
                    <Button disabled>ms</Button>
                </Space.Compact>
                <Typography.Text type="secondary">{t('pages:schedulerDelayHint')}</Typography.Text>
            </Space>
        </Modal>
    </Space>;
}
