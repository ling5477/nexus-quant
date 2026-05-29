import {
    Alert,
    App,
    Button,
    Card,
    Col,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    Row,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
import {BOOLEAN_FILTER_OPTIONS, SCHEDULE_STATUS_OPTIONS, SCHEDULE_TYPE_OPTIONS} from '@/constants/filter-options';
import {
    useScheduleDetailQuery,
    useScheduleListQuery,
    useUpdateScheduleStatusMutation,
} from '@/hooks/useScheduleListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultStrategyScheduleListFilters,
    type StrategyScheduleListFilters,
    type StrategyScheduleListItem,
} from '@/types/schedules';
import {containsIgnoreCase, formatDateTime, matchesBooleanFilter, normalizeOptionalText} from '@/utils/formatters';

type ScheduleRow = StrategyScheduleListItem;

export function SchedulesPage() {
    const {message} = App.useApp();
    const [form] = Form.useForm<StrategyScheduleListFilters>();
    const [submittedFilters, setSubmittedFilters] = useState<StrategyScheduleListFilters>(defaultStrategyScheduleListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedScheduleId, setSelectedScheduleId] = useState<string | null>(null);
    const schedulesQuery = useScheduleListQuery(submittedFilters.strategyId, searchVersion);
    const scheduleDetailQuery = useScheduleDetailQuery(selectedScheduleId);
    const updateStatusMutation = useUpdateScheduleStatusMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = (schedulesQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.scheduleType, submittedFilters.scheduleType)
        && containsIgnoreCase(item.status, submittedFilters.status)
        && matchesBooleanFilter(item.enabled, submittedFilters.enabled)
    ));

    const scheduleColumns: ColumnsType<ScheduleRow> = [
        {
            title: '调度 ID',
            dataIndex: 'scheduleJobId',
            key: 'scheduleJobId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '策略 ID',
            dataIndex: 'strategyId',
            key: 'strategyId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '调度类型',
            dataIndex: 'scheduleType',
            key: 'scheduleType',
            width: 140,
        },
        {
            title: 'Cron',
            dataIndex: 'cronExpr',
            key: 'cronExpr',
            width: 180,
            render: (value: string) => value || '-',
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: string) => <Tag color="blue">{value}</Tag>,
        },
        {
            title: '启用',
            dataIndex: 'enabled',
            key: 'enabled',
            width: 100,
            render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '是' : '否'}</Tag>,
        },
        {
            title: '最近触发',
            dataIndex: 'lastTriggeredAt',
            key: 'lastTriggeredAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: '更新时间',
            dataIndex: 'updatedAt',
            key: 'updatedAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button type="link" onClick={() => setSelectedScheduleId(record.scheduleJobId)}>
                    查看详情
                </Button>
            ),
        },
    ];

    const handleSearch = (values: StrategyScheduleListFilters) => {
        setSubmittedFilters({
            strategyId: normalizeOptionalText(values.strategyId),
            scheduleType: normalizeOptionalText(values.scheduleType),
            status: normalizeOptionalText(values.status),
            enabled: values.enabled ?? 'all',
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        form.resetFields();
        setSubmittedFilters(defaultStrategyScheduleListFilters);
        setSearchVersion(0);
    };

    const handleStatusUpdate = (enabled: boolean) => {
        if (!scheduleDetailQuery.data) {
            return;
        }

        updateStatusMutation.mutate(
            {
                scheduleId: scheduleDetailQuery.data.scheduleJobId,
                request: {enabled},
            },
            {
                onSuccess: () => {
                    message.success(enabled ? '调度已启用。' : '调度已停用。');
                    scheduleDetailQuery.refetch();
                },
                onError: (error) => {
                    message.error(formatApiError(error as AppApiError));
                },
            },
        );
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <PageHero
                        title="调度计划"
                        description="按策略查看调度计划、运行状态和启停动作。freeze 期间只展示现有调度事实，不扩展新的调度能力。"
                        badge="Schedules"
                    />
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="查询区"
                    extra={(
                        <Space>
                            <Button type="primary" onClick={() => form.submit()}>
                                查询
                            </Button>
                            <Button onClick={handleReset}>
                                重置
                            </Button>
                        </Space>
                    )}
                >
                    <Form
                        form={form}
                        layout="vertical"
                        initialValues={defaultStrategyScheduleListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label="策略 ID"
                                    name="strategyId"
                                    rules={[{required: true, message: '请输入 strategyId'}]}
                                >
                                    <Input placeholder="必填，例如：strategy-001"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="调度类型" name="scheduleType">
                                    <Select allowClear placeholder="全部类型" options={SCHEDULE_TYPE_OPTIONS}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="状态" name="status">
                                    <Select allowClear placeholder="全部状态" options={SCHEDULE_STATUS_OPTIONS}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="启用状态" name="enabled">
                                    <Select
                                        options={BOOLEAN_FILTER_OPTIONS}
                                    />
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="查询结果"
                    extra={hasSearched ?
                        <Typography.Text type="secondary">共 {visibleItems.length} 条记录</Typography.Text> : null}
                >
                    {!hasSearched ? (
                        <Empty description="请输入策略 ID 后执行查询。"/>
                    ) : schedulesQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="调度列表查询失败"
                            description={formatApiError(schedulesQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="scheduleJobId"
                            columns={scheduleColumns}
                            dataSource={visibleItems}
                            loading={schedulesQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1540}}
                            locale={{
                                emptyText: '当前筛选条件下没有匹配的调度记录。',
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedScheduleId)}
                width={720}
                title="调度详情"
                onClose={() => setSelectedScheduleId(null)}
                destroyOnClose
            >
                {scheduleDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载调度详情..."/>
                ) : scheduleDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message="调度详情加载失败"
                        description={formatApiError(scheduleDetailQuery.error as AppApiError)}
                    />
                ) : scheduleDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item
                                label="调度 ID">{scheduleDetailQuery.data.scheduleJobId}</Descriptions.Item>
                            <Descriptions.Item label="策略 ID">{scheduleDetailQuery.data.strategyId}</Descriptions.Item>
                            <Descriptions.Item
                                label="调度类型">{scheduleDetailQuery.data.scheduleType}</Descriptions.Item>
                            <Descriptions.Item
                                label="Cron">{scheduleDetailQuery.data.cronExpr || '-'}</Descriptions.Item>
                            <Descriptions.Item label="时区">{scheduleDetailQuery.data.timezone}</Descriptions.Item>
                            <Descriptions.Item label="状态">
                                <Tag color="blue">{scheduleDetailQuery.data.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="启用状态">
                                <Tag color={scheduleDetailQuery.data.enabled ? 'success' : 'default'}>
                                    {scheduleDetailQuery.data.enabled ? '已启用' : '未启用'}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item
                                label="最近触发">{formatDateTime(scheduleDetailQuery.data.lastTriggeredAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label="交易所">{scheduleDetailQuery.data.exchangeCode}</Descriptions.Item>
                            <Descriptions.Item
                                label="账户">{scheduleDetailQuery.data.accountId ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="交易环境">{scheduleDetailQuery.data.tradeEnv}</Descriptions.Item>
                            <Descriptions.Item
                                label="创建时间">{formatDateTime(scheduleDetailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label="更新时间">{formatDateTime(scheduleDetailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label="窗口配置" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {scheduleDetailQuery.data.windowConfig || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="去重范围" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {scheduleDetailQuery.data.dedupScope || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title="动作区" size="small">
                            <Space wrap>
                                <Button
                                    type="primary"
                                    disabled={scheduleDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(true)}
                                >
                                    启用调度
                                </Button>
                                <Button
                                    danger
                                    disabled={!scheduleDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(false)}
                                >
                                    停用调度
                                </Button>
                                <Button onClick={() => scheduleDetailQuery.refetch()}>
                                    刷新详情
                                </Button>
                            </Space>
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
        </>
    );
}
