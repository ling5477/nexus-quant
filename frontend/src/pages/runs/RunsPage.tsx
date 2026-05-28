import {
    Alert,
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
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
import {useRunDetailQuery, useRunListQuery} from '@/hooks/useRunListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultStrategyRunListFilters,
    type StrategyRunDetailItem,
    type StrategyRunListFilters,
    type StrategyRunSummaryItem,
} from '@/types/runs';
import {containsIgnoreCase, formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type RunRow = StrategyRunSummaryItem;

export function RunsPage() {
    const [form] = Form.useForm<StrategyRunListFilters>();
    const [submittedFilters, setSubmittedFilters] = useState<StrategyRunListFilters>(defaultStrategyRunListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
    const runsQuery = useRunListQuery(
        {
            strategyId: submittedFilters.strategyId || undefined,
            scheduleId: submittedFilters.scheduleId || undefined,
        },
        searchVersion,
    );
    const runDetailQuery = useRunDetailQuery(selectedRunId);
    const hasSearched = searchVersion > 0;

    const visibleItems = (runsQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.status, submittedFilters.status)
        && containsIgnoreCase(item.triggerType, submittedFilters.triggerType)
    ));

    const runColumns: ColumnsType<RunRow> = [
        {
            title: '运行 ID',
            dataIndex: 'strategyRunId',
            key: 'strategyRunId',
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
            title: '调度 ID',
            dataIndex: 'scheduleJobId',
            key: 'scheduleJobId',
            width: 220,
            render: (value: string | null) => value ? <Typography.Text copyable>{value}</Typography.Text> : '-',
        },
        {
            title: '触发方式',
            dataIndex: 'triggerType',
            key: 'triggerType',
            width: 140,
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: string) => <Tag color="blue">{value}</Tag>,
        },
        {
            title: '开始时间',
            dataIndex: 'startedAt',
            key: 'startedAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: '结束时间',
            dataIndex: 'finishedAt',
            key: 'finishedAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button type="link" onClick={() => setSelectedRunId(record.strategyRunId)}>
                    查看详情
                </Button>
            ),
        },
    ];

    const handleSearch = (values: StrategyRunListFilters) => {
        setSubmittedFilters({
            strategyId: normalizeOptionalText(values.strategyId),
            scheduleId: normalizeOptionalText(values.scheduleId),
            status: normalizeOptionalText(values.status),
            triggerType: normalizeOptionalText(values.triggerType),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        form.resetFields();
        setSubmittedFilters(defaultStrategyRunListFilters);
        setSearchVersion(0);
    };

    const renderRunDetail = (detail: StrategyRunDetailItem) => (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Descriptions bordered column={2} size="small">
                <Descriptions.Item label="运行 ID">{detail.strategyRunId}</Descriptions.Item>
                <Descriptions.Item label="策略 ID">{detail.strategyId}</Descriptions.Item>
                <Descriptions.Item label="调度 ID">{detail.scheduleJobId || '-'}</Descriptions.Item>
                <Descriptions.Item label="请求 ID">{detail.requestId || '-'}</Descriptions.Item>
                <Descriptions.Item label="触发方式">{detail.triggerType}</Descriptions.Item>
                <Descriptions.Item label="状态">
                    <Tag color="blue">{detail.status}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="交易所">{detail.exchangeCode}</Descriptions.Item>
                <Descriptions.Item label="账户">{detail.accountId ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="交易环境">{detail.tradeEnv}</Descriptions.Item>
                <Descriptions.Item label="开始时间">{formatDateTime(detail.startedAt)}</Descriptions.Item>
                <Descriptions.Item label="结束时间">{formatDateTime(detail.finishedAt)}</Descriptions.Item>
                <Descriptions.Item label="错误信息">{detail.errorMessage || '-'}</Descriptions.Item>
                <Descriptions.Item label="订单数">{detail.orders.length}</Descriptions.Item>
                <Descriptions.Item label="成交数">{detail.trades.length}</Descriptions.Item>
                <Descriptions.Item label="账本摘要" span={2}>
                    <Typography.Paragraph style={{marginBottom: 0}}>
                        {detail.ledgerSummary || '-'}
                    </Typography.Paragraph>
                </Descriptions.Item>
                <Descriptions.Item label="风控摘要" span={2}>
                    <Typography.Paragraph style={{marginBottom: 0}}>
                        {detail.riskSummary || '-'}
                    </Typography.Paragraph>
                </Descriptions.Item>
                <Descriptions.Item label="事件摘要" span={2}>
                    <Typography.Paragraph style={{marginBottom: 0}}>
                        {detail.eventSummary || '-'}
                    </Typography.Paragraph>
                </Descriptions.Item>
            </Descriptions>
            <Card title="动作区" size="small">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="info"
                        showIcon
                        message="当前运行页在现有后端契约下只提供详情读取，没有独立的写动作 API。"
                    />
                    <Space>
                        <Button type="primary" disabled>
                            当前无可执行写动作
                        </Button>
                        <Button onClick={() => runDetailQuery.refetch()}>
                            刷新详情
                        </Button>
                    </Space>
                </Space>
            </Card>
        </Space>
    );

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <PageHero
                        title="运行记录"
                        description="当前页面已对接真实列表与详情接口。后端当前没有独立 run 写动作，本批按真实契约提供详情抽屉和不可操作动作区。"
                        badge="Runs"
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
                        initialValues={defaultStrategyRunListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="策略 ID" name="strategyId">
                                    <Input placeholder="可空"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="调度 ID" name="scheduleId">
                                    <Input placeholder="可空"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="运行状态" name="status">
                                    <Input placeholder="例如：SUCCEEDED"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="触发方式" name="triggerType">
                                    <Input placeholder="例如：MANUAL / SCHEDULE"/>
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
                        <Empty description="请输入条件后执行查询。"/>
                    ) : runsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="运行列表查询失败"
                            description={formatApiError(runsQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="strategyRunId"
                            columns={runColumns}
                            dataSource={visibleItems}
                            loading={runsQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1500}}
                            locale={{
                                emptyText: '当前筛选条件下没有匹配的运行记录。',
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedRunId)}
                width={760}
                title="运行详情"
                onClose={() => setSelectedRunId(null)}
                destroyOnClose
            >
                {runDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载运行详情..."/>
                ) : runDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message="运行详情加载失败"
                        description={formatApiError(runDetailQuery.error as AppApiError)}
                    />
                ) : runDetailQuery.data ? renderRunDetail(runDetailQuery.data) : null}
            </Drawer>
        </>
    );
}
