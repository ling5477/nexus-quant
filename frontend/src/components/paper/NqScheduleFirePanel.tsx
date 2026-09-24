import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {App, Button, Card, Form, Input, Modal, Space} from 'antd';
import {useState} from 'react';

import {showApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, nqNumericColumn} from '@/components/nq';
import {
    useCreateScheduleMutation,
    usePaperFiresQuery,
    usePaperSchedulesQuery,
    useRunScheduleOnceMutation,
    useUpdateScheduleStatusMutation,
} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunScheduleCreateRequest, PaperRunScheduleFireItem, PaperRunScheduleItem} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/**
 * NqScheduleFirePanel — Paper Run 调度 / 触发面板。
 *
 * 职责：展示调度计划列表，支持创建调度、执行一次、启用/禁用、查看触发记录。
 * 边界：复用既有调度接口，不新增 API；调度只驱动 SIM/Paper 运行编排，不触发真实 LIVE。
 */
interface NqScheduleFirePanelProps {
    paperRunId: string;
}

export function NqScheduleFirePanel({paperRunId}: NqScheduleFirePanelProps) {
    useTranslation('pages');
    const {message} = App.useApp();
    const [scheduleForm] = useLocalizedForm<PaperRunScheduleCreateRequest>();
    const [createOpen, setCreateOpen] = useState(false);
    const [selectedScheduleId, setSelectedScheduleId] = useState<string | null>(null);

    const schedulesQuery = usePaperSchedulesQuery(paperRunId);
    const firesQuery = usePaperFiresQuery(selectedScheduleId);
    const createScheduleMutation = useCreateScheduleMutation();
    const updateScheduleStatusMutation = useUpdateScheduleStatusMutation();
    const runScheduleOnceMutation = useRunScheduleOnceMutation();

    const data = schedulesQuery.data ?? [];

    return (
        <Card
            className="page-section"
            size="small"
            title={t('pages:schedules')}
            extra={(
                <Button size="small" type="primary" ghost onClick={() => setCreateOpen(true)}>
                    {t('pages:createSchedule')}</Button>
            )}
        >
            <Space direction="vertical" size={8} style={{display: 'flex'}}>
                {schedulesQuery.isFetching && data.length === 0 ? (
                    <NqLoadingState/>
                ) : schedulesQuery.error ? (
                    <NqErrorState error={schedulesQuery.error as AppApiError} onRetry={() => schedulesQuery.refetch()}/>
                ) : data.length === 0 ? (
                    <NqEmptyState description={t('pages:noSchedulesForThisPaperRun')}/>
                ) : (
                    <NqDataTable<PaperRunScheduleItem>
                        rowKey="scheduleId"
                        pagination={false}
                        dataSource={data}
                        scroll={{x: 760, y: 240}}
                        columns={[
                            {title: t('pages:name'), dataIndex: 'scheduleName', key: 'scheduleName', width: 140},
                            {title: 'Cron', dataIndex: 'cronExpr', key: 'cronExpr', width: 140, className: 'nq-mono'},
                            {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <StatusTag title="" variant="pill" status={v}/>},
                            {title: t('pages:lastTrigger'), dataIndex: 'lastFireTime', key: 'lastFireTime', width: 170, render: (v: string | null) => formatDateTime(v)},
                            {
                                title: t('pages:actions'), key: 'action', width: 220, fixed: 'right',
                                render: (_, record) => (
                                    <Space size={4}>
                                        <Button type="link" size="small" onClick={() => setSelectedScheduleId(record.scheduleId)}>{t('pages:triggerRecords')}</Button>
                                        <Button
                                            type="link" size="small" loading={runScheduleOnceMutation.isPending} disabled={record.status !== 'ENABLED'}
                                            onClick={() => runScheduleOnceMutation.mutate(record.scheduleId, {
                                                onSuccess: () => message.success(t('pages:scheduleTriggered')),
                                                onError: (err) => showApiError(err as AppApiError, message),
                                            })}
                                        >
                                            {t('pages:runOnce')}</Button>
                                        {record.status === 'ENABLED' ? (
                                            <Button type="link" size="small" onClick={() => updateScheduleStatusMutation.mutate({scheduleId: record.scheduleId, request: {status: 'DISABLED'}}, {onSuccess: () => message.success(t('pages:disabled2'))})}>{t('pages:disable2')}</Button>
                                        ) : (
                                            <Button type="link" size="small" onClick={() => updateScheduleStatusMutation.mutate({scheduleId: record.scheduleId, request: {status: 'ENABLED'}}, {onSuccess: () => message.success(t('pages:enabled2'))})}>{t('pages:enable')}</Button>
                                        )}
                                    </Space>
                                ),
                            },
                        ]}
                    />
                )}

                {selectedScheduleId && (
                    <Card
                        size="small"
                        title={t('pages:triggerRecordsValue1', {value1: selectedScheduleId.substring(0, 12)})}
                        extra={<Button type="link" size="small" onClick={() => setSelectedScheduleId(null)}>{t('pages:close')}</Button>}
                    >
                        {firesQuery.isFetching && (firesQuery.data ?? []).length === 0 ? (
                            <NqLoadingState/>
                        ) : firesQuery.error ? (
                            <NqErrorState error={firesQuery.error as AppApiError} onRetry={() => firesQuery.refetch()}/>
                        ) : (firesQuery.data ?? []).length === 0 ? (
                            <NqEmptyState description={t('pages:noTriggerRecords')}/>
                        ) : (
                            <NqDataTable<PaperRunScheduleFireItem>
                                rowKey="fireId"
                                pagination={false}
                                dataSource={firesQuery.data ?? []}
                                scroll={{y: 200}}
                                columns={[
                                    {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <StatusTag title="" variant="pill" status={v}/>},
                                    {title: t('pages:triggeredAt'), dataIndex: 'firedAt', key: 'firedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                    nqNumericColumn({title: t('pages:durationMs'), dataIndex: 'durationMs', key: 'durationMs', width: 100}),
                                    {title: t('pages:error'), dataIndex: 'errorMessage', key: 'errorMessage'},
                                ]}
                            />
                        )}
                    </Card>
                )}
            </Space>

            <Modal
                open={createOpen}
                title={t('pages:createSchedulePlan')}
                onCancel={() => setCreateOpen(false)}
                onOk={() => scheduleForm.submit()}
                confirmLoading={createScheduleMutation.isPending}
                destroyOnClose
            >
                <Form
                    form={scheduleForm}
                    layout="vertical"
                    initialValues={{cronExpr: '0 */5 * * * *', timezone: 'UTC'}}
                    onFinish={(values) => {
                        createScheduleMutation.mutate({...values, paperRunId}, {
                            onSuccess: () => {
                                message.success(t('pages:scheduleCreated'));
                                setCreateOpen(false);
                                scheduleForm.resetFields();
                            },
                            onError: (err) => showApiError(err as AppApiError, message),
                        });
                    }}
                >
                    <Form.Item label={t('pages:scheduleName')} name="scheduleName" rules={[{required: true, message: t('pages:enterAScheduleName')}]}>
                        <Input placeholder={t('pages:forExampleHeartbeatEvery5Minutes')}/>
                    </Form.Item>
                    <Form.Item label={t('pages:cronExpression')} name="cronExpr" rules={[{required: true, message: t('pages:enterACronExpression')}]}>
                        <Input placeholder="0 */5 * * * *"/>
                    </Form.Item>
                    <Form.Item label={t('pages:timeZone')} name="timezone">
                        <Input placeholder="UTC"/>
                    </Form.Item>
                </Form>
            </Modal>
        </Card>
    );
}
