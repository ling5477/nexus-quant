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
import {
    useStrategyDetailQuery,
    useStrategyVersionsQuery,
    useCreateStrategyVersionMutation,
    useStrategyListQuery,
    useUpdateStrategyStatusMutation,
} from '@/hooks/useStrategyListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultStrategyListFilters,
    type StrategyDefinitionListItem,
    type StrategyListFilters,
    type StrategyVersionCreateRequest,
    type StrategyVersionItem,
} from '@/types/strategies';
import {containsIgnoreCase, formatDateTime, matchesBooleanFilter, normalizeOptionalText} from '@/utils/formatters';

type StrategyRow = StrategyDefinitionListItem;

export function StrategiesPage() {
    const {message} = App.useApp();
    const [form] = Form.useForm<StrategyListFilters>();
    const [versionForm] = Form.useForm<StrategyVersionCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<StrategyListFilters>(defaultStrategyListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedStrategyCode, setSelectedStrategyCode] = useState<string | null>(null);
    const strategiesQuery = useStrategyListQuery(searchVersion);
    const strategyDetailQuery = useStrategyDetailQuery(selectedStrategyCode);
    const strategyVersionsQuery = useStrategyVersionsQuery(selectedStrategyCode);
    const updateStatusMutation = useUpdateStrategyStatusMutation();
    const createVersionMutation = useCreateStrategyVersionMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = (strategiesQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.strategyCode, submittedFilters.strategyCode)
        && containsIgnoreCase(item.strategyType, submittedFilters.strategyType)
        && containsIgnoreCase(item.exchangeCode, submittedFilters.exchangeCode)
        && containsIgnoreCase(item.tradeEnv, submittedFilters.tradeEnv)
        && matchesBooleanFilter(item.enabled, submittedFilters.enabled)
    ));

    const strategyColumns: ColumnsType<StrategyRow> = [
        {
            title: '策略编码',
            dataIndex: 'strategyCode',
            key: 'strategyCode',
            width: 180,
        },
        {
            title: '策略名称',
            dataIndex: 'strategyName',
            key: 'strategyName',
            width: 220,
        },
        {
            title: '策略 ID',
            dataIndex: 'strategyId',
            key: 'strategyId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '类型',
            dataIndex: 'strategyType',
            key: 'strategyType',
            width: 140,
        },
        {
            title: '交易所',
            dataIndex: 'exchangeCode',
            key: 'exchangeCode',
            width: 120,
        },
        {
            title: '环境',
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 120,
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
                <Button type="link" onClick={() => setSelectedStrategyCode(record.strategyCode)}>
                    查看详情
                </Button>
            ),
        },
    ];

    const versionColumns: ColumnsType<StrategyVersionItem> = [
        {
            title: '版本 ID',
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '版本号',
            dataIndex: 'version',
            key: 'version',
            width: 90,
        },
        {
            title: '版本名称',
            dataIndex: 'versionName',
            key: 'versionName',
            width: 180,
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 110,
            render: (value: string) => <Tag color={value === 'ACTIVE' ? 'success' : 'blue'}>{value}</Tag>,
        },
        {
            title: 'Checksum',
            dataIndex: 'checksum',
            key: 'checksum',
            width: 220,
            render: (value: string) => <Typography.Text copyable ellipsis>{value}</Typography.Text>,
        },
        {
            title: '创建时间',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
    ];

    const handleSearch = (values: StrategyListFilters) => {
        setSubmittedFilters({
            strategyCode: normalizeOptionalText(values.strategyCode),
            strategyType: normalizeOptionalText(values.strategyType),
            exchangeCode: normalizeOptionalText(values.exchangeCode),
            tradeEnv: normalizeOptionalText(values.tradeEnv),
            enabled: values.enabled ?? 'all',
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        form.resetFields();
        setSubmittedFilters(defaultStrategyListFilters);
        setSearchVersion(0);
    };

    const handleStatusUpdate = (enabled: boolean) => {
        if (!strategyDetailQuery.data) {
            return;
        }

        updateStatusMutation.mutate(
            {
                strategyCode: strategyDetailQuery.data.strategyCode,
                request: {enabled},
            },
            {
                onSuccess: () => {
                    message.success(enabled ? '策略已启用。' : '策略已停用。');
                    strategyDetailQuery.refetch();
                },
                onError: (error) => {
                    message.error(formatApiError(error as AppApiError));
                },
            },
        );
    };

    const handleCreateVersion = (values: StrategyVersionCreateRequest) => {
        if (!selectedStrategyCode) {
            return;
        }
        createVersionMutation.mutate(
            {
                strategyCode: selectedStrategyCode,
                request: {
                    versionName: values.versionName,
                    status: values.status || 'DRAFT',
                    paramSnapshotJson: values.paramSnapshotJson || '{}',
                    configSnapshotJson: values.configSnapshotJson || undefined,
                    sourceSnapshotJson: values.sourceSnapshotJson || '{}',
                },
            },
            {
                onSuccess: () => {
                    message.success('策略版本已创建。');
                    versionForm.resetFields();
                    strategyVersionsQuery.refetch();
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
                        title="策略定义"
                        description="当前页面已对接真实 `GET /api/strategies` 列表接口，并支持打开详情与执行最小启停动作。"
                        badge="Strategies"
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
                        initialValues={defaultStrategyListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="策略编码" name="strategyCode">
                                    <Input placeholder="例如：alpha-grid-btc"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="策略类型" name="strategyType">
                                    <Input placeholder="例如：SPOT_GRID"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="交易所" name="exchangeCode">
                                    <Input placeholder="例如：OKX"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="交易环境" name="tradeEnv">
                                    <Input placeholder="例如：SIM / LIVE"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="启用状态" name="enabled">
                                    <Select
                                        options={[
                                            {label: '全部', value: 'all'},
                                            {label: '已启用', value: 'true'},
                                            {label: '未启用', value: 'false'},
                                        ]}
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
                        <Empty description="点击查询后加载策略列表。"/>
                    ) : strategiesQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="策略列表查询失败"
                            description={formatApiError(strategiesQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="strategyId"
                            columns={strategyColumns}
                            dataSource={visibleItems}
                            loading={strategiesQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1520}}
                            locale={{
                                emptyText: '当前筛选条件下没有匹配的策略记录。',
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedStrategyCode)}
                width={680}
                title="策略详情"
                onClose={() => setSelectedStrategyCode(null)}
                destroyOnClose
            >
                {strategyDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载策略详情..."/>
                ) : strategyDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message="策略详情加载失败"
                        description={formatApiError(strategyDetailQuery.error as AppApiError)}
                    />
                ) : strategyDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item
                                label="策略编码">{strategyDetailQuery.data.strategyCode}</Descriptions.Item>
                            <Descriptions.Item
                                label="策略名称">{strategyDetailQuery.data.strategyName}</Descriptions.Item>
                            <Descriptions.Item label="策略 ID">{strategyDetailQuery.data.strategyId}</Descriptions.Item>
                            <Descriptions.Item
                                label="策略类型">{strategyDetailQuery.data.strategyType}</Descriptions.Item>
                            <Descriptions.Item
                                label="交易所">{strategyDetailQuery.data.exchangeCode}</Descriptions.Item>
                            <Descriptions.Item
                                label="账户">{strategyDetailQuery.data.accountId ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="交易环境">{strategyDetailQuery.data.tradeEnv}</Descriptions.Item>
                            <Descriptions.Item label="状态">
                                <Tag color="blue">{strategyDetailQuery.data.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="启用状态">
                                <Tag color={strategyDetailQuery.data.enabled ? 'success' : 'default'}>
                                    {strategyDetailQuery.data.enabled ? '已启用' : '未启用'}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="版本">{strategyDetailQuery.data.version}</Descriptions.Item>
                            <Descriptions.Item
                                label="创建时间">{formatDateTime(strategyDetailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label="更新时间">{formatDateTime(strategyDetailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label="配置快照" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {strategyDetailQuery.data.configSnapshot || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title="动作区" size="small">
                            <Space wrap>
                                <Button
                                    type="primary"
                                    disabled={strategyDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(true)}
                                >
                                    启用策略
                                </Button>
                                <Button
                                    danger
                                    disabled={!strategyDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(false)}
                                >
                                    停用策略
                                </Button>
                                <Button onClick={() => strategyDetailQuery.refetch()}>
                                    刷新详情
                                </Button>
                            </Space>
                        </Card>
                        <Card
                            title="策略版本"
                            size="small"
                            extra={<Button onClick={() => strategyVersionsQuery.refetch()}>刷新版本</Button>}
                        >
                            {strategyVersionsQuery.error ? (
                                <Alert
                                    type="error"
                                    showIcon
                                    message="策略版本查询失败"
                                    description={formatApiError(strategyVersionsQuery.error as AppApiError)}
                                />
                            ) : (
                                <Table
                                    rowKey="strategyVersionId"
                                    size="small"
                                    columns={versionColumns}
                                    dataSource={strategyVersionsQuery.data ?? []}
                                    loading={strategyVersionsQuery.isFetching}
                                    pagination={{pageSize: 5, showSizeChanger: false}}
                                    scroll={{x: 1000}}
                                    locale={{emptyText: '当前策略还没有版本。'}}
                                />
                            )}
                        </Card>
                        <Card title="创建策略版本" size="small">
                            <Form
                                form={versionForm}
                                layout="vertical"
                                initialValues={{
                                    status: 'DRAFT',
                                    paramSnapshotJson: '{}',
                                    sourceSnapshotJson: '{}',
                                }}
                                onFinish={handleCreateVersion}
                            >
                                <Row gutter={[16, 0]}>
                                    <Col xs={24} md={12}>
                                        <Form.Item
                                            label="版本名称"
                                            name="versionName"
                                            rules={[{required: true, message: '请输入版本名称'}]}
                                        >
                                            <Input placeholder="例如：GateI-1 baseline"/>
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24} md={12}>
                                        <Form.Item label="版本状态" name="status">
                                            <Select
                                                options={[
                                                    {label: 'DRAFT', value: 'DRAFT'},
                                                    {label: 'ACTIVE', value: 'ACTIVE'},
                                                    {label: 'ARCHIVED', value: 'ARCHIVED'},
                                                ]}
                                            />
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24}>
                                        <Form.Item label="参数快照 JSON" name="paramSnapshotJson">
                                            <Input.TextArea rows={3} placeholder='{"threshold":1}'/>
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24}>
                                        <Form.Item label="配置快照 JSON" name="configSnapshotJson">
                                            <Input.TextArea rows={3} placeholder="留空则使用当前策略配置快照"/>
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24}>
                                        <Form.Item label="来源快照 JSON" name="sourceSnapshotJson">
                                            <Input.TextArea rows={3} placeholder='{"source":"manual"}'/>
                                        </Form.Item>
                                    </Col>
                                </Row>
                                <Space>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={createVersionMutation.isPending}
                                    >
                                        创建版本
                                    </Button>
                                    <Button onClick={() => versionForm.resetFields()}>
                                        清空
                                    </Button>
                                </Space>
                            </Form>
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
        </>
    );
}
