import {App, Button, Card, Form, Input, Modal, Space} from 'antd';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, NqStatusTag, nqNumericColumn} from '@/components/nq';
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
    const {message} = App.useApp();
    const [scheduleForm] = Form.useForm<PaperRunScheduleCreateRequest>();
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
            title="调度计划"
            extra={(
                <Button size="small" type="primary" ghost onClick={() => setCreateOpen(true)}>
                    创建调度
                </Button>
            )}
        >
            <Space direction="vertical" size={8} style={{display: 'flex'}}>
                {schedulesQuery.isFetching && data.length === 0 ? (
                    <NqLoadingState/>
                ) : schedulesQuery.error ? (
                    <NqErrorState error={schedulesQuery.error as AppApiError} onRetry={() => schedulesQuery.refetch()}/>
                ) : data.length === 0 ? (
                    <NqEmptyState description="当前 Paper run 暂无调度计划。"/>
                ) : (
                    <NqDataTable<PaperRunScheduleItem>
                        rowKey="scheduleId"
                        pagination={false}
                        dataSource={data}
                        scroll={{x: 760, y: 240}}
                        columns={[
                            {title: '名称', dataIndex: 'scheduleName', key: 'scheduleName', width: 140},
                            {title: 'Cron', dataIndex: 'cronExpr', key: 'cronExpr', width: 140, className: 'nq-mono'},
                            {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
                            {title: '上次触发', dataIndex: 'lastFireTime', key: 'lastFireTime', width: 170, render: (v: string | null) => formatDateTime(v)},
                            {
                                title: '操作', key: 'action', width: 220, fixed: 'right',
                                render: (_, record) => (
                                    <Space size={4}>
                                        <Button type="link" size="small" onClick={() => setSelectedScheduleId(record.scheduleId)}>触发记录</Button>
                                        <Button
                                            type="link" size="small" loading={runScheduleOnceMutation.isPending} disabled={record.status !== 'ENABLED'}
                                            onClick={() => runScheduleOnceMutation.mutate(record.scheduleId, {
                                                onSuccess: () => message.success('调度已触发。'),
                                                onError: (err) => message.error(formatApiError(err as AppApiError)),
                                            })}
                                        >
                                            执行一次
                                        </Button>
                                        {record.status === 'ENABLED' ? (
                                            <Button type="link" size="small" onClick={() => updateScheduleStatusMutation.mutate({scheduleId: record.scheduleId, request: {status: 'DISABLED'}}, {onSuccess: () => message.success('已禁用。')})}>禁用</Button>
                                        ) : (
                                            <Button type="link" size="small" onClick={() => updateScheduleStatusMutation.mutate({scheduleId: record.scheduleId, request: {status: 'ENABLED'}}, {onSuccess: () => message.success('已启用。')})}>启用</Button>
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
                        title={`触发记录 (${selectedScheduleId.substring(0, 12)}...)`}
                        extra={<Button type="link" size="small" onClick={() => setSelectedScheduleId(null)}>关闭</Button>}
                    >
                        {firesQuery.isFetching && (firesQuery.data ?? []).length === 0 ? (
                            <NqLoadingState/>
                        ) : firesQuery.error ? (
                            <NqErrorState error={firesQuery.error as AppApiError} onRetry={() => firesQuery.refetch()}/>
                        ) : (firesQuery.data ?? []).length === 0 ? (
                            <NqEmptyState description="暂无触发记录。"/>
                        ) : (
                            <NqDataTable<PaperRunScheduleFireItem>
                                rowKey="fireId"
                                pagination={false}
                                dataSource={firesQuery.data ?? []}
                                scroll={{y: 200}}
                                columns={[
                                    {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
                                    {title: '触发时间', dataIndex: 'firedAt', key: 'firedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                    nqNumericColumn({title: '耗时(ms)', dataIndex: 'durationMs', key: 'durationMs', width: 100}),
                                    {title: '错误', dataIndex: 'errorMessage', key: 'errorMessage'},
                                ]}
                            />
                        )}
                    </Card>
                )}
            </Space>

            <Modal
                open={createOpen}
                title="创建调度计划"
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
                                message.success('调度已创建。');
                                setCreateOpen(false);
                                scheduleForm.resetFields();
                            },
                            onError: (err) => message.error(formatApiError(err as AppApiError)),
                        });
                    }}
                >
                    <Form.Item label="调度名称" name="scheduleName" rules={[{required: true, message: '请输入调度名称'}]}>
                        <Input placeholder="如：每5分钟心跳"/>
                    </Form.Item>
                    <Form.Item label="Cron 表达式" name="cronExpr" rules={[{required: true, message: '请输入 cron 表达式'}]}>
                        <Input placeholder="0 */5 * * * *"/>
                    </Form.Item>
                    <Form.Item label="时区" name="timezone">
                        <Input placeholder="UTC"/>
                    </Form.Item>
                </Form>
            </Modal>
        </Card>
    );
}
