import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {
    Alert,
    App,
    Button,
    Card,
    Descriptions,
    Grid,
    InputNumber,
    Modal,
    Popconfirm,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {Link} from 'react-router-dom';

import {schedulerQueryKeys, operationalReadinessQueryKeys} from '@/api/query-keys';
import {formatApiError, showApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {schedulerApi} from '@/features/scheduler/api/scheduler';
import type {ScheduledJobControl, ScheduledJobPatch} from '@/features/scheduler/types';
import {
    hasScheduledOperation,
    scheduledOperations,
    type ScheduledOperationResponse,
} from '@/features/scheduler/operations';
import {
    ScheduledOperationRecovery,
    ScheduledNotificationSource,
    usePendingScheduledOperation,
} from '@/features/scheduler/ScheduledOperationRecovery';
import {operationalReadinessApi} from '@/features/runtime/api/operational-readiness';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';
import {
    parseScheduledDelayDraft,
    scheduledDelayLimits,
    validScheduledDelay,
} from '@/features/scheduler/job-delay';

import './SchedulerJobsPage.css';

const DEFERRED_JOBS = new Set([
    'STRATEGY_RECOVERY',
    'OKX_RECOVERY',
    'OKX_RECONCILIATION',
    'BINANCE_RECONCILIATION',
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
        case 'SUCCESS':
            return 'success';
        case 'FAILED':
            return 'error';
        case 'RUNNING':
            return 'processing';
        case 'SKIPPED':
            return 'warning';
        default:
            return 'default';
    }
}

/** 服务端控制状态只通过 Query 刷新；静态不可启用提示不替代服务端资格检查。 */
export function SchedulerJobsPage() {
    const {t} = useTranslation();
    const {message} = App.useApp();
    const queryClient = useQueryClient();
    const compact = Grid.useBreakpoint().xs === true;
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const pendingOperation = usePendingScheduledOperation();
    const operationPending = Boolean(pendingOperation) || hasScheduledOperation(owner);
    const canManage = roles.some((role) => ['ADMIN', 'ROLE_ADMIN'].includes(role.toUpperCase()));
    const [editing, setEditing] = useState<ScheduledJobControl | null>(null);
    const [delay, setDelay] = useState<number | null>(null);
    const [delayDraft, setDelayDraft] = useState('');
    const limits = scheduledDelayLimits(editing);
    const draftDelay = parseScheduledDelayDraft(delayDraft);

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
    const operationResult = async (result: ScheduledOperationResponse) => {
        await queryClient.invalidateQueries({queryKey: schedulerQueryKeys.all});
        if (result.status === 'COMPLETED') {
            if (result.type === 'UPDATE_CONTROL') {
                setEditing(null);
                message.success(t('pages:schedulerChangeSaved'));
            } else if (result.result === 'SUCCESS') message.success(t('pages:schedulerRunSuccess'));
            else message.warning(t('pages:schedulerRunNotSuccessful', {result: result.result}));
        } else if (result.status === 'REJECTED')
            message.warning(t('pages:schedulerOperations.rejected', {reason: result.reasonCode}));
    };
    const patch = useMutation({
        mutationFn: ({jobKey, request}: {jobKey: string; request: ScheduledJobPatch}) =>
            scheduledOperations.submit({type: 'UPDATE_CONTROL', jobKey, ...request}),
        onSuccess: operationResult,
        onError: (error) => showApiError(error as AppApiError, message),
    });
    const run = useMutation({
        mutationFn: (jobKey: string) => scheduledOperations.submit({type: 'RUN_ONCE', jobKey}),
        onSuccess: operationResult,
        onError: (error) => showApiError(error as AppApiError, message),
    });

    const columns: ColumnsType<ScheduledJobControl> = [
        {
            title: t('pages:schedulerJob'),
            dataIndex: 'jobKey',
            width: compact ? 196 : 210,
            fixed: compact ? undefined : 'left',
            render: (key: string, row) => (
                <Space direction="vertical" size={4} className="scheduler-job-object">
                    <Typography.Text strong>{t(JOB_NAMES[key] ?? key)}</Typography.Text>
                    <Typography.Text type="secondary" copyable>
                        {key}
                    </Typography.Text>
                    {compact ? (
                        <Descriptions size="small" column={1} className="scheduler-job-summary">
                            <Descriptions.Item label={t('pages:schedulerEnabled')}>
                                <Tag color={row.enabled ? 'success' : 'default'}>
                                    {t(
                                        row.enabled
                                            ? 'pages:schedulerStateEnabled'
                                            : 'pages:schedulerStateDisabled',
                                    )}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:schedulerFixedDelay')}>
                                {row.fixedDelayMs.toLocaleString()} ms
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:schedulerLastResult')}>
                                <Tag color={statusColor(row.lastStatus)}>
                                    {t(`pages:schedulerStatus_${row.lastStatus}`, {
                                        defaultValue: row.lastStatus,
                                    })}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:schedulerFailures')}>
                                {row.consecutiveFailures}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:schedulerNextRun')}>
                                {formatDateTime(row.nextRunAt)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:schedulerStartedAt')}>
                                {formatDateTime(row.lastStartedAt)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:schedulerFinishedAt')}>
                                {formatDateTime(row.lastFinishedAt)}
                            </Descriptions.Item>
                        </Descriptions>
                    ) : null}
                    {DEFERRED_JOBS.has(key) ? (
                        <Typography.Text type="secondary">
                            {t('pages:schedulerDeferred')}
                        </Typography.Text>
                    ) : null}
                    <details className="scheduler-job-technical">
                        <summary>{t('pages:schedulerTechnicalStatus')}</summary>
                        <Typography.Text code copyable>
                            {row.lastStatus}
                        </Typography.Text>
                        {row.lastErrorCode ? (
                            <Typography.Text type="danger" copyable>
                                {row.lastErrorCode}
                            </Typography.Text>
                        ) : null}
                    </details>
                </Space>
            ),
        },
        {
            title: t('pages:schedulerEnabled'),
            dataIndex: 'enabled',
            width: 88,
            responsive: ['sm'],
            render: (enabled: boolean) => (
                <Space direction="vertical" size={0}>
                    <Tag color={enabled ? 'success' : 'default'}>
                        {t(
                            enabled
                                ? 'pages:schedulerStateEnabled'
                                : 'pages:schedulerStateDisabled',
                        )}
                    </Tag>
                </Space>
            ),
        },
        {
            title: t('pages:schedulerFixedDelay'),
            dataIndex: 'fixedDelayMs',
            width: 105,
            responsive: ['sm'],
            render: (value: number) => `${value.toLocaleString()} ms`,
        },
        {
            title: t('pages:schedulerNextRun'),
            dataIndex: 'nextRunAt',
            width: 150,
            responsive: ['sm'],
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: t('pages:schedulerLastRun'),
            dataIndex: 'lastFinishedAt',
            width: 150,
            responsive: ['sm'],
            render: (_: string | null, row) => (
                <Space direction="vertical" size={0}>
                    <Typography.Text>
                        {formatDateTime(row.lastFinishedAt ?? row.lastStartedAt)}
                    </Typography.Text>
                    {row.lastStartedAt && row.lastFinishedAt ? (
                        <Typography.Text type="secondary">
                            {t('pages:schedulerStartedAt')}: {formatDateTime(row.lastStartedAt)}
                        </Typography.Text>
                    ) : null}
                </Space>
            ),
        },
        {
            title: t('pages:schedulerLastResult'),
            dataIndex: 'lastStatus',
            width: 140,
            responsive: ['sm'],
            render: (status: ScheduledJobControl['lastStatus'], row) => (
                <Space direction="vertical" size={0}>
                    <Tag color={statusColor(status)}>
                        {t(`pages:schedulerStatus_${status}`, {defaultValue: status})}
                    </Tag>
                    {row.lastErrorCode ? (
                        <Typography.Text type="danger" copyable>
                            {row.lastErrorCode}
                        </Typography.Text>
                    ) : null}
                </Space>
            ),
        },
        {
            title: t('pages:schedulerFailures'),
            dataIndex: 'consecutiveFailures',
            width: 76,
            responsive: ['sm'],
        },
        {
            title: t('pages:actions'),
            key: 'actions',
            width: compact ? 138 : 224,
            fixed: compact ? undefined : 'right',
            render: (_: unknown, row) =>
                canManage ? (
                    <Space wrap className="scheduler-job-actions">
                        <Popconfirm
                            title={
                                row.enabled
                                    ? t('pages:schedulerConfirmDisable')
                                    : t('pages:schedulerConfirmEnable')
                            }
                            description={`${t(JOB_NAMES[row.jobKey] ?? row.jobKey)} · ${row.jobKey}`}
                            onConfirm={() =>
                                patch.mutate({
                                    jobKey: row.jobKey,
                                    request: {enabled: !row.enabled, expectedVersion: row.version},
                                })
                            }
                            okText={t('pages:schedulerConfirm')}
                            cancelText={t('pages:cancel')}
                        >
                            <Button
                                size="small"
                                disabled={
                                    patch.isPending ||
                                    operationPending ||
                                    (DEFERRED_JOBS.has(row.jobKey) && !row.enabled)
                                }
                            >
                                {row.enabled
                                    ? t('pages:schedulerTurnOff')
                                    : t('pages:schedulerTurnOn')}
                            </Button>
                        </Popconfirm>
                        <Button
                            size="small"
                            disabled={
                                patch.isPending || operationPending || !scheduledDelayLimits(row)
                            }
                            title={
                                !scheduledDelayLimits(row)
                                    ? t('pages:schedulerLimitsUnavailable')
                                    : undefined
                            }
                            onClick={() => {
                                setEditing(row);
                                setDelay(row.fixedDelayMs);
                                setDelayDraft(String(row.fixedDelayMs));
                            }}
                        >
                            {t('pages:schedulerEditDelay')}
                        </Button>
                        <Popconfirm
                            title={t('pages:schedulerConfirmRun')}
                            description={`${t(JOB_NAMES[row.jobKey] ?? row.jobKey)} · ${row.jobKey}`}
                            onConfirm={() => run.mutate(row.jobKey)}
                            okText={t('pages:schedulerConfirm')}
                            cancelText={t('pages:cancel')}
                        >
                            <Button
                                size="small"
                                disabled={
                                    run.isPending ||
                                    operationPending ||
                                    DEFERRED_JOBS.has(row.jobKey)
                                }
                            >
                                {t('pages:schedulerRunOnce')}
                            </Button>
                        </Popconfirm>
                    </Space>
                ) : (
                    <Typography.Text type="secondary">
                        {t('pages:schedulerReadOnly')}
                    </Typography.Text>
                ),
        },
    ];

    return (
        <Space
            direction="vertical"
            size="middle"
            style={{width: '100%'}}
            className="scheduler-jobs-page"
        >
            <NqPageHeader
                title={t('pages:schedulerTitle')}
                description={t('pages:schedulerDescription')}
                extra={
                    <Button onClick={() => void jobs.refetch()} loading={jobs.isFetching}>
                        {t('pages:schedulerRefresh')}
                    </Button>
                }
            />
            <Alert
                type="warning"
                showIcon
                message={t('pages:schedulerSafetyTitle')}
                description={t('pages:schedulerSafetyDescription')}
            />
            <ScheduledOperationRecovery
                jobName={(key) => t(JOB_NAMES[key] ?? key)}
                onResult={operationResult}
            />
            <ScheduledNotificationSource jobName={(key) => t(JOB_NAMES[key] ?? key)} />
            <Card size="small" title={t('pages:schedulerRuntimeBoundary')}>
                <Descriptions size="small" column={{xs: 1, sm: 2, md: 4}}>
                    <Descriptions.Item label={t('pages:schedulerSimScope')}>
                        SIM / Paper
                    </Descriptions.Item>
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
                    {t('pages:schedulerReadinessSource')}{' '}
                    {formatDateTime(readiness.data?.generatedAt)} ·{' '}
                    <Link to="/runtime/readiness">{t('pages:schedulerReadinessLink')}</Link>
                </Typography.Text>
                {readiness.isError ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:schedulerReadinessUnavailable')}
                    />
                ) : null}
            </Card>
            {jobs.isError ? (
                <Alert
                    type="error"
                    showIcon
                    message={t('pages:schedulerLoadFailed')}
                    description={formatApiError(jobs.error as AppApiError)}
                    action={
                        <Button onClick={() => void jobs.refetch()}>
                            {t('pages:schedulerRetry')}
                        </Button>
                    }
                />
            ) : null}
            <Card size="small" title={t('pages:schedulerJobsTitle')}>
                <Table
                    size="small"
                    rowKey="jobKey"
                    columns={columns}
                    dataSource={jobs.data ?? []}
                    loading={jobs.isLoading}
                    // 窄屏将全部事实并入同一任务单元，避免滚到动作后丢失被操作对象。
                    scroll={{x: compact ? 334 : 1243}}
                    pagination={false}
                    locale={{
                        emptyText: jobs.isError
                            ? t('pages:schedulerUnavailable')
                            : t('pages:schedulerEmpty'),
                    }}
                />
                {jobs.dataUpdatedAt > 0 ? (
                    <Typography.Text type="secondary">
                        {t('pages:schedulerUpdatedAt')}:{' '}
                        {formatDateTime(new Date(jobs.dataUpdatedAt).toISOString())}
                    </Typography.Text>
                ) : null}
            </Card>
            <Modal
                title={t('pages:schedulerEditDelay')}
                open={editing !== null}
                onCancel={() => setEditing(null)}
                onOk={() => {
                    if (
                        !editing ||
                        draftDelay === null ||
                        !validScheduledDelay(editing, draftDelay)
                    )
                        return;
                    patch.mutate(
                        {
                            jobKey: editing.jobKey,
                            request: {fixedDelayMs: draftDelay, expectedVersion: editing.version},
                        },
                        {onSuccess: () => setEditing(null)},
                    );
                }}
                okButtonProps={{
                    disabled: operationPending || !validScheduledDelay(editing, draftDelay),
                    loading: patch.isPending,
                }}
                okText={t('pages:save')}
                cancelText={t('pages:cancel')}
            >
                <Space direction="vertical" style={{width: '100%'}}>
                    <Typography.Text>{editing?.jobKey}</Typography.Text>
                    <Space.Compact style={{width: '100%'}}>
                        <InputNumber
                            aria-label={t('pages:schedulerFixedDelay')}
                            value={delay}
                            min={limits?.min}
                            max={limits?.max}
                            disabled={!limits}
                            precision={0}
                            style={{width: '100%'}}
                            onChange={(value) => {
                                setDelay(value);
                                setDelayDraft(value === null ? '' : String(value));
                            }}
                            // 范围外输入不会触发onChange；必须记录可见文本，不能让旧合法值保持可提交。
                            onInput={setDelayDraft}
                        />
                        <Button disabled>ms</Button>
                    </Space.Compact>
                    <Typography.Text type="secondary">
                        {t('pages:schedulerDelayHint')}
                    </Typography.Text>
                    <Typography.Text
                        type={validScheduledDelay(editing, draftDelay) ? 'secondary' : 'danger'}
                    >
                        {limits
                            ? t('pages:schedulerDelayRange', {
                                  min: limits.min.toLocaleString(),
                                  max: limits.max.toLocaleString(),
                              })
                            : t('pages:schedulerLimitsUnavailable')}
                    </Typography.Text>
                </Space>
            </Modal>
        </Space>
    );
}
