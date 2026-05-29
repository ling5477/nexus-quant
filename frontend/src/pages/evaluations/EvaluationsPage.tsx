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
import {EVALUATION_STATUS_OPTIONS} from '@/constants/filter-options';
import {
    useEvaluateMutation,
    useEvaluationDetailQuery,
    useEvaluationsListQuery,
} from '@/hooks/useEvaluationsListQuery';
import type {AppApiError} from '@/types/api';
import {
    type BacktestEvaluationListItem,
    defaultEvaluationsListFilters,
    type EvaluationsListFilters,
} from '@/types/evaluations';
import {containsIgnoreCase, formatDateTime, formatNumber, normalizeOptionalText} from '@/utils/formatters';

type EvaluationRow = BacktestEvaluationListItem;

export function EvaluationsPage() {
    const {message} = App.useApp();
    const [form] = Form.useForm<EvaluationsListFilters>();
    const [submittedFilters, setSubmittedFilters] = useState<EvaluationsListFilters>(defaultEvaluationsListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedRow, setSelectedRow] = useState<EvaluationRow | null>(null);
    const evaluationsQuery = useEvaluationsListQuery(
        {
            researchConfigId: submittedFilters.researchConfigId || undefined,
            backtestConfigId: submittedFilters.backtestConfigId || undefined,
        },
        searchVersion,
    );
    const evaluationDetailQuery = useEvaluationDetailQuery(selectedRow?.evalReportId ?? null);
    const evaluateMutation = useEvaluateMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = (evaluationsQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.backtestRunId, submittedFilters.sourceStrategyId)
        && containsIgnoreCase(item.evaluationStatus, submittedFilters.evaluationStatus)
    ));

    const evaluationColumns: ColumnsType<EvaluationRow> = [
        {
            title: '评估报告 ID',
            dataIndex: 'evalReportId',
            key: 'evalReportId',
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
            title: '评估状态',
            dataIndex: 'evaluationStatus',
            key: 'evaluationStatus',
            width: 120,
            render: (value: string) => <Tag color="default">{value}</Tag>,
        },
        {
            title: '净收益',
            dataIndex: 'netPnl',
            key: 'netPnl',
            width: 120,
            render: (value: number | null) => formatNumber(value, 2),
        },
        {
            title: '评估时间',
            dataIndex: 'evaluatedAt',
            key: 'evaluatedAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: '总收益',
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 120,
            render: (value: number | null, record) => formatNumber(value ?? record.totalReturnRate),
        },
        {
            title: '最大回撤',
            dataIndex: 'maxDrawdownRate',
            key: 'maxDrawdownRate',
            width: 120,
            render: (value: number | null) => formatNumber(value),
        },
        {
            title: '胜率',
            dataIndex: 'winRate',
            key: 'winRate',
            width: 120,
            render: (value: number | null) => formatNumber(value),
        },
        {
            title: '盈亏比',
            dataIndex: 'profitLossRatio',
            key: 'profitLossRatio',
            width: 120,
            render: (value: number | null) => formatNumber(value),
        },
        {
            title: '成交数',
            dataIndex: 'tradeCount',
            key: 'tradeCount',
            width: 100,
            render: (value: number | null) => value ?? '-',
        },
        {
            title: 'Sharpe',
            dataIndex: 'sharpeRatio',
            key: 'sharpeRatio',
            width: 120,
            render: (value: number | null) => formatNumber(value),
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

    const handleSearch = (values: EvaluationsListFilters) => {
        setSubmittedFilters({
            researchConfigId: normalizeOptionalText(values.researchConfigId),
            backtestConfigId: normalizeOptionalText(values.backtestConfigId),
            sourceStrategyId: normalizeOptionalText(values.sourceStrategyId),
            evaluationStatus: normalizeOptionalText(values.evaluationStatus),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        form.resetFields();
        setSubmittedFilters(defaultEvaluationsListFilters);
        setSearchVersion(0);
    };

    const handleEvaluate = () => {
        if (!selectedRow) {
            return;
        }

        evaluateMutation.mutate(selectedRow.backtestRunId, {
            onSuccess: () => {
                message.success('评估已触发并返回最新结果。');
                evaluationDetailQuery.refetch();
                setSearchVersion((value) => value + 1);
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
                        title="评估结果"
                        description="查看回测评估报告、收益风险指标和评估状态，并可在详情中对既有回测运行执行评估。"
                        badge="Evaluations"
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
                        initialValues={defaultEvaluationsListFilters}
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
                                <Form.Item label="回测运行 ID" name="sourceStrategyId">
                                    <Input placeholder="按回测运行 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="评估状态" name="evaluationStatus">
                                    <Select allowClear placeholder="全部状态" options={EVALUATION_STATUS_OPTIONS}/>
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
                        <Empty description="点击查询后加载评估结果列表。"/>
                    ) : evaluationsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="评估结果列表查询失败"
                            description={formatApiError(evaluationsQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="backtestRunId"
                            columns={evaluationColumns}
                            dataSource={visibleItems}
                            loading={evaluationsQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 2460}}
                            locale={{
                                emptyText: '当前筛选条件下没有匹配的评估结果。',
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedRow)}
                width={760}
                title="评估详情"
                onClose={() => setSelectedRow(null)}
                destroyOnClose
            >
                {!selectedRow ? null : (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label="回测运行 ID">{selectedRow.backtestRunId}</Descriptions.Item>
                            <Descriptions.Item label="评估报告 ID">{selectedRow.evalReportId}</Descriptions.Item>
                            <Descriptions.Item label="评估状态">{selectedRow.evaluationStatus || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label="评估时间">{formatDateTime(selectedRow.evaluatedAt)}</Descriptions.Item>
                            <Descriptions.Item label="总收益">{formatNumber(selectedRow.totalReturn ?? selectedRow.totalReturnRate)}</Descriptions.Item>
                            <Descriptions.Item label="年化收益">{formatNumber(selectedRow.annualizedReturn)}</Descriptions.Item>
                            <Descriptions.Item label="最大回撤">{formatNumber(selectedRow.maxDrawdownRate)}</Descriptions.Item>
                            <Descriptions.Item label="胜率">{formatNumber(selectedRow.winRate)}</Descriptions.Item>
                            <Descriptions.Item label="盈亏比">{formatNumber(selectedRow.profitLossRatio)}</Descriptions.Item>
                            <Descriptions.Item label="成交数">{selectedRow.tradeCount ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="Sharpe">{formatNumber(selectedRow.sharpeRatio)}</Descriptions.Item>
                            <Descriptions.Item label="Metrics JSON" span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {selectedRow.metricsJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        {evaluationDetailQuery.isLoading ? (
                            <Alert type="info" showIcon message="正在加载评估详情..."/>
                        ) : evaluationDetailQuery.error ? (
                            <Alert
                                type="warning"
                                showIcon
                                message="当前未取到完整评估详情"
                                description={formatApiError(evaluationDetailQuery.error as AppApiError)}
                            />
                        ) : evaluationDetailQuery.data ? (
                            <Descriptions bordered column={2} size="small">
                                <Descriptions.Item
                                    label="评估报告 ID">{evaluationDetailQuery.data.evalReportId}</Descriptions.Item>
                                <Descriptions.Item
                                    label="评估状态">{evaluationDetailQuery.data.evaluationStatus}</Descriptions.Item>
                                <Descriptions.Item
                                    label="初始资金">{formatNumber(evaluationDetailQuery.data.initialCapital, 2)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="最终权益">{formatNumber(evaluationDetailQuery.data.finalEquity, 2)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="净收益">{formatNumber(evaluationDetailQuery.data.netPnl, 2)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="总收益率">{formatNumber(evaluationDetailQuery.data.totalReturnRate)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="总收益">{formatNumber(evaluationDetailQuery.data.totalReturn)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="年化收益">{formatNumber(evaluationDetailQuery.data.annualizedReturn)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="最大回撤率">{formatNumber(evaluationDetailQuery.data.maxDrawdownRate)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="胜率">{formatNumber(evaluationDetailQuery.data.winRate)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="盈亏比">{formatNumber(evaluationDetailQuery.data.profitLossRatio)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="成交数">{evaluationDetailQuery.data.tradeCount ?? '-'}</Descriptions.Item>
                                <Descriptions.Item
                                    label="Sharpe">{formatNumber(evaluationDetailQuery.data.sharpeRatio)}</Descriptions.Item>
                                <Descriptions.Item
                                    label="评估时间">{formatDateTime(evaluationDetailQuery.data.evaluatedAt)}</Descriptions.Item>
                                <Descriptions.Item label="Metrics JSON" span={2}>
                                    <Typography.Paragraph style={{marginBottom: 0}}>
                                        {evaluationDetailQuery.data.metricsJson || '{}'}
                                    </Typography.Paragraph>
                                </Descriptions.Item>
                            </Descriptions>
                        ) : null}
                        <Card title="动作区" size="small">
                            <Space wrap>
                                <Button type="primary" loading={evaluateMutation.isPending} onClick={handleEvaluate}>
                                    执行评估
                                </Button>
                                <Button onClick={() => evaluationDetailQuery.refetch()}>
                                    刷新详情
                                </Button>
                            </Space>
                        </Card>
                    </Space>
                )}
            </Drawer>
        </>
    );
}
