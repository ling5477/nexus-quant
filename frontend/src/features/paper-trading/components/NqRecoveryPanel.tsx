import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n/index';
import {useState} from 'react';
import {Alert, App, Button, Card, Form, Input, Modal, Space} from 'antd';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';

import {showApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState} from '@/components/nq/index';
import {
    usePaperRecoveryEventsQuery,
    useRecoverMutation,
    useRetryFailedStepMutation,
    useRunMonitorOnceMutation,
} from '@/features/paper-trading/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunRecoveryEventItem} from '@/features/paper-trading/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/** 历史恢复面板只保存说明和读取旧事件，不执行失败步骤；监控告警仍沿用原接口，真实恢复复用canonical SIM。 */
interface NqRecoveryPanelProps {
    paperRunId: string;
}

export function NqRecoveryPanel({paperRunId}: NqRecoveryPanelProps) {
    useTranslation('pages');
    const {message} = App.useApp();
    const recoveryEventsQuery = usePaperRecoveryEventsQuery(paperRunId);
    const recoverMutation = useRecoverMutation();
    const retryFailedStepMutation = useRetryFailedStepMutation();
    const runMonitorOnceMutation = useRunMonitorOnceMutation();

    const [retryOpen, setRetryOpen] = useState(false);
    const [retryForm] = useLocalizedForm<{failedStep: string; reason: string}>();
    const data = recoveryEventsQuery.data ?? [];

    return (
        <>
            <Card
                className="page-section"
                size="small"
                title={t('pages:legacyMonitor.recoveryTitle')}
                extra={
                    <Space size={4} wrap>
                        <Button
                            size="small"
                            type="primary"
                            ghost
                            loading={recoverMutation.isPending}
                            onClick={() =>
                                recoverMutation.mutate(
                                    {paperRunId, request: {reason: '用户请求记录历史运行恢复检查'}},
                                    {
                                        onSuccess: () =>
                                            message.success(t('pages:legacyMonitor.recorded')),
                                        onError: (err) => showApiError(err as AppApiError, message),
                                    },
                                )
                            }
                        >
                            {t('pages:legacyMonitor.recordRecovery')}
                        </Button>
                        <Button
                            size="small"
                            loading={retryFailedStepMutation.isPending}
                            onClick={() => setRetryOpen(true)}
                        >
                            {t('pages:legacyMonitor.recordRetry')}
                        </Button>
                        <Button
                            size="small"
                            loading={runMonitorOnceMutation.isPending}
                            onClick={() =>
                                runMonitorOnceMutation.mutate(
                                    {paperRunId},
                                    {
                                        onSuccess: (result) =>
                                            message.success(
                                                t(
                                                    'pages:monitoringGuardCompletedValue1AlertsCreated',
                                                    {value1: result.createdAlertCount},
                                                ),
                                            ),
                                        onError: (err) => showApiError(err as AppApiError, message),
                                    },
                                )
                            }
                        >
                            {t('pages:runMonitoringGuard')}
                        </Button>
                    </Space>
                }
            >
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:legacyMonitor.recovery')}
                    style={{marginBottom: 8}}
                />
                {recoveryEventsQuery.isFetching && data.length === 0 ? (
                    <NqLoadingState />
                ) : recoveryEventsQuery.error ? (
                    <NqErrorState
                        error={recoveryEventsQuery.error as AppApiError}
                        onRetry={() => recoveryEventsQuery.refetch()}
                    />
                ) : data.length === 0 ? (
                    <NqEmptyState description={t('pages:noRecoveryEventsForThisPaperRun')} />
                ) : (
                    <NqDataTable<PaperRunRecoveryEventItem>
                        rowKey="recoveryEventId"
                        pagination={false}
                        dataSource={data}
                        scroll={{y: 240}}
                        columns={[
                            {
                                title: t('pages:type'),
                                dataIndex: 'recoveryType',
                                key: 'recoveryType',
                                width: 180,
                            },
                            {
                                title: t('pages:legacyMonitor.recordState'),
                                dataIndex: 'status',
                                key: 'status',
                                width: 110,
                                render: (v: string) => (
                                    <StatusTag
                                        title=""
                                        variant="pill"
                                        status={v}
                                        tone="neutral"
                                    />
                                ),
                            },
                            {title: t('pages:reason'), dataIndex: 'reason', key: 'reason'},
                            {
                                title: t('pages:startTime'),
                                dataIndex: 'startedAt',
                                key: 'startedAt',
                                width: 170,
                                render: (v: string) => formatDateTime(v),
                            },
                            {
                                title: t('pages:completedAt'),
                                dataIndex: 'finishedAt',
                                key: 'finishedAt',
                                width: 170,
                                render: (v: string | null) => (v ? formatDateTime(v) : '-'),
                            },
                        ]}
                    />
                )}
            </Card>
            <Modal
                open={retryOpen}
                title={t('pages:legacyMonitor.recordRetry')}
                onCancel={() => setRetryOpen(false)}
                onOk={() => retryForm.submit()}
                confirmLoading={retryFailedStepMutation.isPending}
                destroyOnClose
            >
                <Form
                    form={retryForm}
                    layout="vertical"
                    onFinish={(values) => {
                        // 原接口只记录事件；必须保留用户明确输入的步骤，不能编造manual-test步骤。
                        retryFailedStepMutation.mutate(
                            {
                                paperRunId,
                                request: {
                                    failedStep: values.failedStep.trim(),
                                    reason: values.reason?.trim(),
                                },
                            },
                            {
                                onSuccess: () => {
                                    setRetryOpen(false);
                                    retryForm.resetFields();
                                    message.success(t('pages:legacyMonitor.recorded'));
                                },
                                onError: (err) => showApiError(err as AppApiError, message),
                            },
                        );
                    }}
                >
                    <Form.Item
                        name="failedStep"
                        label={t('pages:legacyMonitor.step')}
                        rules={[{required: true, whitespace: true}]}
                    >
                        <Input maxLength={200} />
                    </Form.Item>
                    <Form.Item name="reason" label={t('pages:reason')}>
                        <Input.TextArea maxLength={1000} />
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
}
