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
    Space,
    Table,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
import {
    useCreateResearchMutation,
    useResearchDetailQuery,
    useResearchListQuery,
} from '@/hooks/useResearchListQuery';
import type {AppApiError} from '@/types/api';
import type {ResearchConfigCreateRequest} from '@/types/research';
import {
    defaultResearchListFilters,
    type ResearchConfigListItem,
    type ResearchListFilters,
} from '@/types/research';
import {containsIgnoreCase, formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type ResearchRow = ResearchConfigListItem;

export function ResearchPage() {
    const {message} = App.useApp();
    const [queryForm] = Form.useForm<ResearchListFilters>();
    const [createForm] = Form.useForm<ResearchConfigCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<ResearchListFilters>(defaultResearchListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedConfigId, setSelectedConfigId] = useState<string | null>(null);
    const [createOpen, setCreateOpen] = useState(false);
    const researchQuery = useResearchListQuery(submittedFilters.sourceStrategyId, searchVersion);
    const researchDetailQuery = useResearchDetailQuery(selectedConfigId);
    const createResearchMutation = useCreateResearchMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = (researchQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.researchConfigId, submittedFilters.researchConfigId)
        && containsIgnoreCase(item.name, submittedFilters.name)
    ));

    const researchColumns: ColumnsType<ResearchRow> = [
        {
            title: '研究配置 ID',
            dataIndex: 'researchConfigId',
            key: 'researchConfigId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '源策略 ID',
            dataIndex: 'sourceStrategyId',
            key: 'sourceStrategyId',
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
            width: 260,
            render: (value: string) => value || '-',
        },
        {
            title: '创建时间',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
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
                <Button type="link" onClick={() => setSelectedConfigId(record.researchConfigId)}>
                    查看详情
                </Button>
            ),
        },
    ];

    const handleSearch = (values: ResearchListFilters) => {
        setSubmittedFilters({
            sourceStrategyId: normalizeOptionalText(values.sourceStrategyId),
            researchConfigId: normalizeOptionalText(values.researchConfigId),
            name: normalizeOptionalText(values.name),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        queryForm.resetFields();
        setSubmittedFilters(defaultResearchListFilters);
        setSearchVersion(0);
    };

    const handleCreate = (values: ResearchConfigCreateRequest) => {
        createResearchMutation.mutate(
            {
                ...values,
                sourceStrategyId: normalizeOptionalText(values.sourceStrategyId),
                name: normalizeOptionalText(values.name),
                description: normalizeOptionalText(values.description),
                parameterSchema: normalizeOptionalText(values.parameterSchema),
                parameterDefaults: normalizeOptionalText(values.parameterDefaults),
                datasetSpec: normalizeOptionalText(values.datasetSpec),
            },
            {
                onSuccess: () => {
                    message.success('研究配置已创建。');
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

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <PageHero
                        title="研究配置"
                        description="查看研究配置、源策略关联和参数定义，并提供既有契约下的最小新建动作闭环。"
                        badge="Research"
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
                        initialValues={defaultResearchListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="源策略 ID" name="sourceStrategyId">
                                    <Input placeholder="按源策略 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="研究配置 ID" name="researchConfigId">
                                    <Input placeholder="按研究配置 ID 筛选"/>
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
                            新建研究配置
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
                        <Empty description="点击查询后加载研究配置列表。"/>
                    ) : researchQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="研究配置列表查询失败"
                            description={formatApiError(researchQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="researchConfigId"
                            columns={researchColumns}
                            dataSource={visibleItems}
                            loading={researchQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1520}}
                            locale={{
                                emptyText: '当前筛选条件下没有匹配的研究配置。',
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedConfigId)}
                width={760}
                title="研究配置详情"
                onClose={() => setSelectedConfigId(null)}
                destroyOnClose
            >
                {researchDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载研究配置详情..."/>
                ) : researchDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message="研究配置详情加载失败"
                        description={formatApiError(researchDetailQuery.error as AppApiError)}
                    />
                ) : researchDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item
                                label="研究配置 ID">{researchDetailQuery.data.researchConfigId}</Descriptions.Item>
                            <Descriptions.Item
                                label="源策略 ID">{researchDetailQuery.data.sourceStrategyId}</Descriptions.Item>
                            <Descriptions.Item label="名称">{researchDetailQuery.data.name}</Descriptions.Item>
                            <Descriptions.Item
                                label="描述">{researchDetailQuery.data.description || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label="创建时间">{formatDateTime(researchDetailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label="更新时间">{formatDateTime(researchDetailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label="参数 Schema" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {researchDetailQuery.data.parameterSchema || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="参数默认值" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {researchDetailQuery.data.parameterDefaults || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="数据集规格" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {researchDetailQuery.data.datasetSpec || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title="动作区" size="small">
                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                <Alert type="info" showIcon
                                       message="当前无基于研究配置详情的写动作，创建入口在页面动作区。"/>
                                <Button onClick={() => researchDetailQuery.refetch()}>
                                    刷新详情
                                </Button>
                            </Space>
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
            <Drawer
                open={createOpen}
                width={720}
                title="新建研究配置"
                onClose={() => setCreateOpen(false)}
                destroyOnClose
            >
                <Form form={createForm} layout="vertical" onFinish={handleCreate}>
                    <Form.Item label="源策略 ID" name="sourceStrategyId"
                               rules={[{required: true, message: '请输入 sourceStrategyId'}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label="名称" name="name" rules={[{required: true, message: '请输入名称'}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label="描述" name="description">
                        <Input.TextArea rows={3}/>
                    </Form.Item>
                    <Form.Item label="参数 Schema" name="parameterSchema"
                               rules={[{required: true, message: '请输入 parameterSchema'}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Form.Item label="参数默认值" name="parameterDefaults"
                               rules={[{required: true, message: '请输入 parameterDefaults'}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Form.Item label="数据集规格" name="datasetSpec"
                               rules={[{required: true, message: '请输入 datasetSpec'}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={createResearchMutation.isPending}>
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
