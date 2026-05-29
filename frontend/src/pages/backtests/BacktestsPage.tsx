import {
    Alert,
    App,
    Button,
    Card,
    Col,
    DatePicker,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    InputNumber,
    Row,
    Select,
    Space,
    Table,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';

import {formatApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {PageHero} from '@/components/page/PageHero';
import {
    useBindBacktestDatasetMutation,
    useBindBacktestStrategyVersionMutation,
    useBacktestDetailQuery,
    useBacktestRunDetailQuery,
    useBacktestsListQuery,
    useCreateBacktestRunMutation,
    useCreateBacktestMutation,
} from '@/hooks/useBacktestsListQuery';
import type {AppApiError} from '@/types/api';
import type {BacktestConfigCreateRequest} from '@/types/backtests';
import {
    type BacktestConfigListItem,
    type BacktestsListFilters,
    defaultBacktestsListFilters,
} from '@/types/backtests';
import {containsIgnoreCase, formatDateTime, formatNumber, normalizeOptionalText} from '@/utils/formatters';

type BacktestRow = BacktestConfigListItem;
type DateFormValue = string | null | undefined | {
    toISOString?: () => string;
    toDate?: () => Date;
};

type BacktestConfigCreateFormValues = Omit<BacktestConfigCreateRequest, 'startTime' | 'endTime'> & {
    startTime?: DateFormValue;
    endTime?: DateFormValue;
};

function toIsoDateTime(value: DateFormValue): string {
    if (!value) {
        return '';
    }
    if (typeof value === 'string') {
        return value;
    }
    if (typeof value.toISOString === 'function') {
        return value.toISOString();
    }
    if (typeof value.toDate === 'function') {
        return value.toDate().toISOString();
    }
    return String(value);
}

export function BacktestsPage() {
    const {message} = App.useApp();
    const [queryForm] = Form.useForm<BacktestsListFilters>();
    const [createForm] = Form.useForm<BacktestConfigCreateFormValues>();
    const [bindDatasetForm] = Form.useForm<{datasetId: string}>();
    const [bindStrategyVersionForm] = Form.useForm<{strategyVersionId: string}>();
    const [submittedFilters, setSubmittedFilters] = useState<BacktestsListFilters>(defaultBacktestsListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedConfigId, setSelectedConfigId] = useState<string | null>(null);
    const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
    const [createOpen, setCreateOpen] = useState(false);
    const backtestsQuery = useBacktestsListQuery(submittedFilters.researchConfigId, searchVersion);
    const backtestDetailQuery = useBacktestDetailQuery(selectedConfigId);
    const createBacktestMutation = useCreateBacktestMutation();
    const createBacktestRunMutation = useCreateBacktestRunMutation();
    const backtestRunDetailQuery = useBacktestRunDetailQuery(selectedRunId);
    const bindDatasetMutation = useBindBacktestDatasetMutation(selectedConfigId);
    const bindStrategyVersionMutation = useBindBacktestStrategyVersionMutation(selectedConfigId);
    const datasetsQuery = useQuery({
        queryKey: ['marketdata-datasets'],
        queryFn: marketdataApi.listDatasets,
        enabled: Boolean(selectedConfigId),
    });
    const hasSearched = searchVersion > 0;

    const visibleItems = (backtestsQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.backtestConfigId, submittedFilters.backtestConfigId)
        && containsIgnoreCase(item.name, submittedFilters.name)
    ));

    const backtestColumns: ColumnsType<BacktestRow> = [
        {
            title: '回测配置 ID',
            dataIndex: 'backtestConfigId',
            key: 'backtestConfigId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '研究配置 ID',
            dataIndex: 'researchConfigId',
            key: 'researchConfigId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '名称',
            dataIndex: 'name',
            key: 'name',
            width: 180,
        },
        {
            title: '描述',
            dataIndex: 'description',
            key: 'description',
            width: 240,
            render: (value: string) => value || '-',
        },
        {
            title: '开始时间',
            dataIndex: 'startTime',
            key: 'startTime',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: '结束时间',
            dataIndex: 'endTime',
            key: 'endTime',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: '初始资金',
            dataIndex: 'initialCapital',
            key: 'initialCapital',
            width: 140,
            render: (value: number | null) => formatNumber(value, 2),
        },
        {
            title: '策略版本 ID',
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 220,
            render: (value: string | null) => value ? <Typography.Text copyable>{value}</Typography.Text> : '未绑定',
        },
        {
            title: 'Dataset ID',
            dataIndex: 'datasetId',
            key: 'datasetId',
            width: 220,
            render: (value: string | null) => value ? <Typography.Text copyable>{value}</Typography.Text> : '未绑定',
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
                <Button type="link" onClick={() => setSelectedConfigId(record.backtestConfigId)}>
                    查看详情
                </Button>
            ),
        },
    ];

    const datasetOptions = (datasetsQuery.data ?? []).map((dataset) => ({
        label: `${dataset.datasetName} / ${dataset.exchangeCode} ${dataset.symbol} ${dataset.interval} / ${dataset.qualityStatus}`,
        value: dataset.datasetId,
    }));

    const handleSearch = (values: BacktestsListFilters) => {
        setSubmittedFilters({
            researchConfigId: normalizeOptionalText(values.researchConfigId),
            backtestConfigId: normalizeOptionalText(values.backtestConfigId),
            name: normalizeOptionalText(values.name),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        queryForm.resetFields();
        setSubmittedFilters(defaultBacktestsListFilters);
        setSearchVersion(0);
    };

    const handleCreate = (values: BacktestConfigCreateFormValues) => {
        createBacktestMutation.mutate(
            {
                ...values,
                researchConfigId: normalizeOptionalText(values.researchConfigId),
                name: normalizeOptionalText(values.name),
                description: normalizeOptionalText(values.description),
                startTime: toIsoDateTime(values.startTime),
                endTime: toIsoDateTime(values.endTime),
                executionSpec: normalizeOptionalText(values.executionSpec),
                evaluationSpec: normalizeOptionalText(values.evaluationSpec),
            },
            {
                onSuccess: () => {
                    message.success('回测配置已创建。');
                    setCreateOpen(false);
                    createForm.resetFields();
                    setSearchVersion((value) => (value === 0 ? 1 : value + 1));
                },
                onError: (error) => {
                    message.error(formatApiError(error as AppApiError));
                },
            },
        );
    };

    const handleBindDataset = (values: {datasetId: string}) => {
        if (!selectedConfigId) {
            return;
        }
        bindDatasetMutation.mutate(values, {
            onSuccess: () => {
                message.success('Dataset 已绑定到回测配置。');
                bindDatasetForm.resetFields();
            },
            onError: (error) => {
                message.error(formatApiError(error as AppApiError));
            },
        });
    };

    const handleBindStrategyVersion = (values: {strategyVersionId: string}) => {
        if (!selectedConfigId) {
            return;
        }
        bindStrategyVersionMutation.mutate(
            {strategyVersionId: normalizeOptionalText(values.strategyVersionId)},
            {
                onSuccess: () => {
                    message.success('Strategy Version 已绑定到回测配置。');
                    bindStrategyVersionForm.resetFields();
                },
                onError: (error) => {
                    message.error(formatApiError(error as AppApiError));
                },
            },
        );
    };

    const handleCreateRun = () => {
        if (!selectedConfigId) {
            return;
        }
        createBacktestRunMutation.mutate(selectedConfigId, {
            onSuccess: (run) => {
                message.success('回测运行已创建，已固化当前配置快照。');
                setSelectedRunId(run.backtestRunId);
            },
            onError: (error) => {
                message.error(formatApiError(error as AppApiError));
            },
        });
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <PageHero
                        title="回测配置"
                        description="查看回测配置、Dataset 绑定和策略版本快照，并提供既有契约下的最小新建动作闭环。"
                        badge="Backtests"
                    />
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="查询区"
                    extra={(
                        <Space>
                            <Button type="primary" onClick={() => queryForm.submit()}>
                                查询
                            </Button>
                            <Button onClick={handleReset}>
                                重置
                            </Button>
                        </Space>
                    )}
                >
                    <Form
                        form={queryForm}
                        layout="vertical"
                        initialValues={defaultBacktestsListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="研究配置 ID" name="researchConfigId">
                                    <Input placeholder="按研究配置 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="回测配置 ID" name="backtestConfigId">
                                    <Input placeholder="按回测配置 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="名称" name="name">
                                    <Input placeholder="按配置名称筛选"/>
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="动作区"
                    extra={(
                        <Button type="primary" onClick={() => setCreateOpen(true)}>
                            新建回测配置
                        </Button>
                    )}
                >
                    <Alert
                        type="info"
                        showIcon
                        message="当前页面动作区仅接入最小 create 动作；详情抽屉展示为只读，避免扩成大而全编辑页。"
                    />
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="查询结果"
                    extra={hasSearched ?
                        <Typography.Text type="secondary">共 {visibleItems.length} 条记录</Typography.Text> : null}
                >
                    {!hasSearched ? (
                        <Empty description="点击查询后加载回测配置列表。"/>
                    ) : backtestsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="回测配置列表查询失败"
                            description={formatApiError(backtestsQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="backtestConfigId"
                            columns={backtestColumns}
                            dataSource={visibleItems}
                            loading={backtestsQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1980}}
                            locale={{
                                emptyText: '当前筛选条件下没有匹配的回测配置。',
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedConfigId)}
                width={760}
                title="回测配置详情"
                onClose={() => {
                    setSelectedConfigId(null);
                    setSelectedRunId(null);
                }}
                destroyOnClose
            >
                {backtestDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载回测配置详情..."/>
                ) : backtestDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message="回测配置详情加载失败"
                        description={formatApiError(backtestDetailQuery.error as AppApiError)}
                    />
                ) : backtestDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item
                                label="回测配置 ID">{backtestDetailQuery.data.backtestConfigId}</Descriptions.Item>
                            <Descriptions.Item
                                label="研究配置 ID">{backtestDetailQuery.data.researchConfigId}</Descriptions.Item>
                            <Descriptions.Item label="名称">{backtestDetailQuery.data.name}</Descriptions.Item>
                            <Descriptions.Item
                                label="描述">{backtestDetailQuery.data.description || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label="开始时间">{formatDateTime(backtestDetailQuery.data.startTime)}</Descriptions.Item>
                            <Descriptions.Item
                                label="结束时间">{formatDateTime(backtestDetailQuery.data.endTime)}</Descriptions.Item>
                            <Descriptions.Item
                                label="初始资金">{formatNumber(backtestDetailQuery.data.initialCapital, 2)}</Descriptions.Item>
                            <Descriptions.Item
                                label="创建时间">{formatDateTime(backtestDetailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label="更新时间">{formatDateTime(backtestDetailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label="执行参数" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.executionSpec || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="评估参数" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.evaluationSpec || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="Strategy Version ID" span={2}>
                                <Typography.Text copyable={Boolean(backtestDetailQuery.data.strategyVersionId)}>
                                    {backtestDetailQuery.data.strategyVersionId || '未绑定'}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="Strategy Version Snapshot" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.strategyVersionSnapshotJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="Param Snapshot" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.paramSnapshotJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="Dataset ID" span={2}>
                                <Typography.Text copyable={Boolean(backtestDetailQuery.data.datasetId)}>
                                    {backtestDetailQuery.data.datasetId || '未绑定'}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="Dataset Snapshot" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.datasetSnapshotJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="Config Snapshot JSON" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.configSnapshotJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="配置快照" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.configSnapshot || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title="动作区" size="small">
                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                <Alert type="info" showIcon
                                       message="当前仅绑定 strategy version 和 marketdata dataset，不会启动回测或修改策略逻辑。"/>
                                <Form
                                    form={bindStrategyVersionForm}
                                    layout="inline"
                                    onFinish={handleBindStrategyVersion}
                                >
                                    <Form.Item
                                        label="Strategy Version"
                                        name="strategyVersionId"
                                        rules={[{required: true, message: '请输入 strategy version ID'}]}
                                    >
                                        <Input style={{width: 360}} placeholder="例如：sv-..."/>
                                    </Form.Item>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={bindStrategyVersionMutation.isPending}
                                    >
                                        绑定 Strategy Version
                                    </Button>
                                </Form>
                                <Form form={bindDatasetForm} layout="inline" onFinish={handleBindDataset}>
                                    <Form.Item
                                        label="Dataset"
                                        name="datasetId"
                                        rules={[{required: true, message: '请选择 dataset'}]}
                                    >
                                        <Select
                                            showSearch
                                            style={{width: 420}}
                                            placeholder="选择 marketdata dataset"
                                            loading={datasetsQuery.isLoading || datasetsQuery.isFetching}
                                            options={datasetOptions}
                                            optionFilterProp="label"
                                        />
                                    </Form.Item>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={bindDatasetMutation.isPending}
                                        disabled={datasetOptions.length === 0}
                                    >
                                        绑定 Dataset
                                    </Button>
                                </Form>
                                {datasetsQuery.error ? (
                                    <Alert
                                        type="error"
                                        showIcon
                                        message="Dataset 列表加载失败"
                                        description={formatApiError(datasetsQuery.error as AppApiError)}
                                    />
                                ) : datasetOptions.length === 0 ? (
                                    <Alert type="warning" showIcon message="暂无可绑定 dataset，请先在 Marketdata 页面创建。"/>
                                ) : null}
                                <Button onClick={() => backtestDetailQuery.refetch()}>
                                    刷新详情
                                </Button>
                                <Button
                                    onClick={handleCreateRun}
                                    loading={createBacktestRunMutation.isPending}
                                >
                                    创建回测运行
                                </Button>
                            </Space>
                        </Card>
                        <Card title="回测运行详情" size="small">
                            {!selectedRunId ? (
                                <Empty description="创建回测运行后展示 run 级固化快照。"/>
                            ) : backtestRunDetailQuery.isLoading ? (
                                <Alert type="info" showIcon message="正在加载回测运行详情..."/>
                            ) : backtestRunDetailQuery.error ? (
                                <Alert
                                    type="error"
                                    showIcon
                                    message="回测运行详情加载失败"
                                    description={formatApiError(backtestRunDetailQuery.error as AppApiError)}
                                />
                            ) : backtestRunDetailQuery.data ? (
                                <Descriptions bordered column={2} size="small">
                                    <Descriptions.Item label="回测运行 ID" span={2}>
                                        <Typography.Text copyable>
                                            {backtestRunDetailQuery.data.backtestRunId}
                                        </Typography.Text>
                                    </Descriptions.Item>
                                    <Descriptions.Item label="运行状态">{backtestRunDetailQuery.data.status}</Descriptions.Item>
                                    <Descriptions.Item
                                        label="请求时间">{formatDateTime(backtestRunDetailQuery.data.requestedAt)}</Descriptions.Item>
                                    <Descriptions.Item label="Strategy Version Snapshot" span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {backtestRunDetailQuery.data.strategyVersionSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item label="Dataset Snapshot" span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {backtestRunDetailQuery.data.datasetSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item label="Param Snapshot" span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {backtestRunDetailQuery.data.paramSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item label="Config Snapshot" span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {backtestRunDetailQuery.data.configSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                </Descriptions>
                            ) : null}
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
            <Drawer
                open={createOpen}
                width={720}
                title="新建回测配置"
                onClose={() => setCreateOpen(false)}
                destroyOnClose
            >
                <Form form={createForm} layout="vertical" onFinish={handleCreate}>
                    <Form.Item label="研究配置 ID" name="researchConfigId"
                               rules={[{required: true, message: '请输入 researchConfigId'}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label="名称" name="name" rules={[{required: true, message: '请输入名称'}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label="描述" name="description">
                        <Input.TextArea rows={3}/>
                    </Form.Item>
                    <Form.Item label="开始时间" name="startTime"
                               rules={[{required: true, message: '请选择开始时间'}]}>
                        <DatePicker showTime style={{width: '100%'}}/>
                    </Form.Item>
                    <Form.Item label="结束时间" name="endTime"
                               rules={[{required: true, message: '请选择结束时间'}]}>
                        <DatePicker showTime style={{width: '100%'}}/>
                    </Form.Item>
                    <Form.Item label="初始资金" name="initialCapital"
                               rules={[{required: true, message: '请输入初始资金'}]}>
                        <InputNumber style={{width: '100%'}} min={0.0001}/>
                    </Form.Item>
                    <Form.Item label="执行参数" name="executionSpec"
                               rules={[{required: true, message: '请输入 executionSpec'}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Form.Item label="评估参数" name="evaluationSpec"
                               rules={[{required: true, message: '请输入 evaluationSpec'}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={createBacktestMutation.isPending}>
                            提交创建
                        </Button>
                        <Button onClick={() => setCreateOpen(false)}>
                            取消
                        </Button>
                    </Space>
                </Form>
            </Drawer>
        </>
    );
}
