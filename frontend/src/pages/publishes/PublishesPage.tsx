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
import {PUBLISH_STATUS_OPTIONS} from '@/constants/filter-options';
import {
    usePublishDetailQuery,
    usePublishMutation,
    usePublishesListQuery,
} from '@/hooks/usePublishesListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultPublishesListFilters,
    type BacktestPublishListItem,
    type PublishesListFilters,
} from '@/types/publishes';
import {containsIgnoreCase, formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type PublishRow = BacktestPublishListItem;

export function PublishesPage() {
    const {message} = App.useApp();
    const [queryForm] = Form.useForm<PublishesListFilters>();
    const [publishForm] = Form.useForm<{ displayName?: string; strategyVersionId?: string }>();
    const [submittedFilters, setSubmittedFilters] = useState<PublishesListFilters>(defaultPublishesListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedRow, setSelectedRow] = useState<PublishRow | null>(null);
    const publishesQuery = usePublishesListQuery(
        {
            researchConfigId: submittedFilters.researchConfigId || undefined,
            backtestConfigId: submittedFilters.backtestConfigId || undefined,
            strategyVersionId: submittedFilters.strategyVersionId || undefined,
        },
        searchVersion,
    );
    const publishDetailQuery = usePublishDetailQuery(selectedRow?.publishRecordId ?? null);
    const publishMutation = usePublishMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = (publishesQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.sourceStrategyId, submittedFilters.sourceStrategyId)
        && containsIgnoreCase(item.researchConfigId, submittedFilters.researchConfigId)
        && containsIgnoreCase(item.backtestConfigId, submittedFilters.backtestConfigId)
        && containsIgnoreCase(item.strategyVersionId, submittedFilters.strategyVersionId)
        && containsIgnoreCase(item.publishStatus, submittedFilters.publishStatus)
    ));

    const publishColumns: ColumnsType<PublishRow> = [
        {
            title: '发布记录 ID',
            dataIndex: 'publishRecordId',
            key: 'publishRecordId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '回测运行 ID',
            dataIndex: 'backtestRunId',
            key: 'backtestRunId',
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
            title: '回测配置 ID',
            dataIndex: 'backtestConfigId',
            key: 'backtestConfigId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '源策略 ID',
            dataIndex: 'sourceStrategyId',
            key: 'sourceStrategyId',
            width: 180,
        },
        {
            title: '策略版本 ID',
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 220,
            render: (value: string | null) => value ? <Typography.Text copyable>{value}</Typography.Text> : '-',
        },
        {
            title: '发布状态',
            dataIndex: 'publishStatus',
            key: 'publishStatus',
            width: 120,
            render: (value: string | null) => value ? <Tag color="blue">{value}</Tag> : '-',
        },
        {
            title: '发布时间',
            dataIndex: 'publishedAt',
            key: 'publishedAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: '发布名称',
            dataIndex: 'publishName',
            key: 'publishName',
            width: 180,
            render: (value: string | null) => value || '-',
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button type="link" onClick={() => setSelectedRow(record)}>
                    查看详情
                </Button>
            ),
        },
    ];

    const handleSearch = (values: PublishesListFilters) => {
        setSubmittedFilters({
            researchConfigId: normalizeOptionalText(values.researchConfigId),
            backtestConfigId: normalizeOptionalText(values.backtestConfigId),
            sourceStrategyId: normalizeOptionalText(values.sourceStrategyId),
            strategyVersionId: normalizeOptionalText(values.strategyVersionId),
            publishStatus: normalizeOptionalText(values.publishStatus),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        queryForm.resetFields();
        setSubmittedFilters(defaultPublishesListFilters);
        setSearchVersion(0);
    };

    const handlePublish = (values: { displayName?: string; strategyVersionId?: string }) => {
        if (!selectedRow) {
            return;
        }

        publishMutation.mutate(
            {
                runId: selectedRow.backtestRunId,
                request: {
                    displayName: normalizeOptionalText(values.displayName),
                    strategyVersionId: normalizeOptionalText(values.strategyVersionId),
                },
            },
            {
                onSuccess: () => {
                    message.success('发布动作已执行并返回最新结果。');
                    publishDetailQuery.refetch();
                    setSearchVersion((value) => value + 1);
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
                        title="发布结果"
                        description="查看回测发布记录、策略版本绑定和失败信息，并可在详情中对既有回测运行执行发布。"
                        badge="Publishes"
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
                        initialValues={defaultPublishesListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="研究配置 ID" name="researchConfigId">
                                    <Input placeholder="按研究配置 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="回测配置 ID" name="backtestConfigId">
                                    <Input placeholder="按回测配置 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="源策略 ID" name="sourceStrategyId">
                                    <Input placeholder="按源策略 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="策略版本 ID" name="strategyVersionId">
                                    <Input placeholder="按策略版本 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="发布状态" name="publishStatus">
                                    <Select allowClear placeholder="全部状态" options={PUBLISH_STATUS_OPTIONS}/>
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
                        <Empty description="点击查询后加载发布结果列表。"/>
                    ) : publishesQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="发布结果列表查询失败"
                            description={formatApiError(publishesQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="publishRecordId"
                            columns={publishColumns}
                            dataSource={visibleItems}
                            loading={publishesQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1700}}
                            locale={{
                                emptyText: '当前筛选条件下没有匹配的发布结果。',
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedRow)}
                width={760}
                title="发布详情"
                onClose={() => setSelectedRow(null)}
                destroyOnClose
            >
                {!selectedRow ? null : (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label="回测运行 ID">{selectedRow.backtestRunId}</Descriptions.Item>
                            <Descriptions.Item label="研究配置 ID">{selectedRow.researchConfigId}</Descriptions.Item>
                            <Descriptions.Item label="回测配置 ID">{selectedRow.backtestConfigId}</Descriptions.Item>
                            <Descriptions.Item label="源策略 ID">{selectedRow.sourceStrategyId}</Descriptions.Item>
                            <Descriptions.Item label="策略版本 ID">{selectedRow.strategyVersionId || '-'}</Descriptions.Item>
                            <Descriptions.Item label="发布状态">{selectedRow.publishStatus || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label="发布时间">{formatDateTime(selectedRow.publishedAt)}</Descriptions.Item>
                            <Descriptions.Item label="发布名称">{selectedRow.publishName || '-'}</Descriptions.Item>
                            <Descriptions.Item label="失败码">{selectedRow.failureCode || '-'}</Descriptions.Item>
                        </Descriptions>
                        {publishDetailQuery.isLoading ? (
                            <Alert type="info" showIcon message="正在加载发布详情..."/>
                        ) : publishDetailQuery.error ? (
                            <Alert
                                type="warning"
                                showIcon
                                message="当前未取到完整发布详情"
                                description={formatApiError(publishDetailQuery.error as AppApiError)}
                            />
                        ) : publishDetailQuery.data ? (
                            <Descriptions bordered column={2} size="small">
                                <Descriptions.Item
                                    label="发布记录 ID">{publishDetailQuery.data.publishRecordId}</Descriptions.Item>
                                <Descriptions.Item
                                    label="目标策略定义 ID">{publishDetailQuery.data.targetStrategyDefinitionId || '-'}</Descriptions.Item>
                                <Descriptions.Item
                                    label="策略版本 ID">{publishDetailQuery.data.strategyVersionId || '-'}</Descriptions.Item>
                                <Descriptions.Item
                                    label="发布状态">{publishDetailQuery.data.publishStatus}</Descriptions.Item>
                                <Descriptions.Item
                                    label="发布时间">{formatDateTime(publishDetailQuery.data.publishedAt)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="发布名称">{publishDetailQuery.data.publishName || '-'}</Descriptions.Item>
                                <Descriptions.Item
                                    label="失败信息">{publishDetailQuery.data.failureMessage || '-'}</Descriptions.Item>
                                <Descriptions.Item label="版本快照" span={2}>
                                    <Typography.Paragraph style={{marginBottom: 0}}>
                                        {publishDetailQuery.data.versionSnapshotJson || '{}'}
                                    </Typography.Paragraph>
                                </Descriptions.Item>
                            </Descriptions>
                        ) : null}
                        <Card title="动作区" size="small">
                            <Form form={publishForm} layout="vertical" onFinish={handlePublish}>
                                <Form.Item label="发布名称" name="displayName">
                                    <Input placeholder="可空，留空则沿用默认命名"/>
                                </Form.Item>
                                <Form.Item label="策略版本 ID" name="strategyVersionId">
                                    <Input placeholder="可空；填写后发布记录会固化 version snapshot"/>
                                </Form.Item>
                                <Space wrap>
                                    <Button type="primary" htmlType="submit" loading={publishMutation.isPending}>
                                        执行发布
                                    </Button>
                                    <Button onClick={() => publishDetailQuery.refetch()}>
                                        刷新详情
                                    </Button>
                                </Space>
                            </Form>
                        </Card>
                    </Space>
                )}
            </Drawer>
        </>
    );
}
