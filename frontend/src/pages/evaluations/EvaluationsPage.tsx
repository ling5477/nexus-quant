import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
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

import {formatApiError, showApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {EVALUATION_STATUS_OPTIONS} from '@/constants/filter-options';
import {
    useEvaluateMutation,
    useEvaluationDetailQuery,
    useEvaluationsListQuery,
} from '@/pages/evaluations/useEvaluationsListQuery';
import type {AppApiError} from '@/types/api';
import {
    type BacktestEvaluationListItem,
    defaultEvaluationsListFilters,
    type EvaluationsListFilters,
} from '@/types/evaluations';
import {containsIgnoreCase, formatDateTime, formatNumber, normalizeOptionalText} from '@/utils/formatters';

type EvaluationRow = BacktestEvaluationListItem;

export function EvaluationsPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const [form] = useLocalizedForm<EvaluationsListFilters>();
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
            title: t('pages:evaluationReportId'),
            dataIndex: 'evalReportId',
            key: 'evalReportId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:backtestRunId'),
            dataIndex: 'backtestRunId',
            key: 'backtestRunId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:evaluationStatus'),
            dataIndex: 'evaluationStatus',
            key: 'evaluationStatus',
            width: 120,
            render: (value: string) => <Tag color="default">{value}</Tag>,
        },
        {
            title: t('pages:netReturn'),
            dataIndex: 'netPnl',
            key: 'netPnl',
            width: 120,
            render: (value: number | null) => formatNumber(value, 2),
        },
        {
            title: t('pages:evaluatedAt'),
            dataIndex: 'evaluatedAt',
            key: 'evaluatedAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: t('pages:totalReturn'),
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 120,
            render: (value: number | null, record) => formatNumber(value ?? record.totalReturnRate),
        },
        {
            title: t('pages:maximumDrawdown'),
            dataIndex: 'maxDrawdownRate',
            key: 'maxDrawdownRate',
            width: 120,
            render: (value: number | null) => formatNumber(value),
        },
        {
            title: t('pages:winRate'),
            dataIndex: 'winRate',
            key: 'winRate',
            width: 120,
            render: (value: number | null) => formatNumber(value),
        },
        {
            title: t('pages:profitLossRatio'),
            dataIndex: 'profitLossRatio',
            key: 'profitLossRatio',
            width: 120,
            render: (value: number | null) => formatNumber(value),
        },
        {
            title: t('pages:tradeCount2'),
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
            title: t('pages:actions'),
            key: 'action',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button type="link" onClick={() => setSelectedRow(record)}>
                    {t('pages:viewDetails')}</Button>
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
                message.success(t('pages:evaluationCompletedWithTheLatestResult'));
                evaluationDetailQuery.refetch();
                setSearchVersion((value) => value + 1);
            },
            onError: (error) => {
                showApiError(error as AppApiError, message);
            },
        });
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:evaluationResults')}
                        description={t('pages:viewBacktestEvaluationReportsReturnAndRiskMetricsAndStatusEvaluateAnExistingRunFromItsDetails')}
                        badge="Evaluations"
                    />
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title={t('pages:filters')}
                    extra={(
                        <Space>
                            <Button type="primary" onClick={() => form.submit()}>
                                {t('pages:search')}</Button>
                            <Button onClick={handleReset}>
                                {t('pages:reset')}</Button>
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
                                <Form.Item label={t('pages:researchConfigurationId')} name="researchConfigId">
                                    <Input placeholder={t('pages:filterByResearchConfigurationId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:backtestConfigurationId')} name="backtestConfigId">
                                    <Input placeholder={t('pages:filterByBacktestConfigurationId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:backtestRunId')} name="sourceStrategyId">
                                    <Input placeholder={t('pages:filterByBacktestRunId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:evaluationStatus')} name="evaluationStatus">
                                    <Select allowClear placeholder={t('pages:allStatuses')} options={EVALUATION_STATUS_OPTIONS}/>
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title={t('pages:results')}
                    extra={hasSearched ?
                        <Typography.Text type="secondary">{t('pages:total')}{visibleItems.length} {t('pages:records')}</Typography.Text> : null}
                >
                    {!hasSearched ? (
                        <Empty description={t('pages:searchToLoadEvaluationResults')}/>
                    ) : evaluationsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryEvaluationResults')}
                            description={formatApiError(evaluationsQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    {t('pages:retry')}</Button>
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
                                emptyText: t('pages:noEvaluationResultsMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedRow)}
                width={760}
                title={t('pages:evaluationDetails')}
                onClose={() => setSelectedRow(null)}
                destroyOnClose
            >
                {!selectedRow ? null : (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label={t('pages:backtestRunId')}>{selectedRow.backtestRunId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:evaluationReportId')}>{selectedRow.evalReportId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:evaluationStatus')}>{selectedRow.evaluationStatus || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:evaluatedAt')}>{formatDateTime(selectedRow.evaluatedAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:totalReturn')}>{formatNumber(selectedRow.totalReturn ?? selectedRow.totalReturnRate)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:annualizedReturn')}>{formatNumber(selectedRow.annualizedReturn)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:maximumDrawdown')}>{formatNumber(selectedRow.maxDrawdownRate)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:winRate')}>{formatNumber(selectedRow.winRate)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:profitLossRatio')}>{formatNumber(selectedRow.profitLossRatio)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:tradeCount2')}>{selectedRow.tradeCount ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="Sharpe">{formatNumber(selectedRow.sharpeRatio)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:metricsJson')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {selectedRow.metricsJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        {evaluationDetailQuery.isLoading ? (
                            <Alert type="info" showIcon message={t('pages:loadingEvaluationDetails')}/>
                        ) : evaluationDetailQuery.error ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:fullEvaluationDetailsAreNotAvailable')}
                                description={formatApiError(evaluationDetailQuery.error as AppApiError)}
                            />
                        ) : evaluationDetailQuery.data ? (
                            <Descriptions bordered column={2} size="small">
                                <Descriptions.Item
                                    label={t('pages:evaluationReportId')}>{evaluationDetailQuery.data.evalReportId}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:evaluationStatus')}>{evaluationDetailQuery.data.evaluationStatus}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:initialCapital')}>{formatNumber(evaluationDetailQuery.data.initialCapital, 2)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:finalEquity')}>{formatNumber(evaluationDetailQuery.data.finalEquity, 2)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:netReturn')}>{formatNumber(evaluationDetailQuery.data.netPnl, 2)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:totalReturnRate')}>{formatNumber(evaluationDetailQuery.data.totalReturnRate)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:totalReturn')}>{formatNumber(evaluationDetailQuery.data.totalReturn)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:annualizedReturn')}>{formatNumber(evaluationDetailQuery.data.annualizedReturn)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:maximumDrawdownRate')}>{formatNumber(evaluationDetailQuery.data.maxDrawdownRate)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:winRate')}>{formatNumber(evaluationDetailQuery.data.winRate)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:profitLossRatio')}>{formatNumber(evaluationDetailQuery.data.profitLossRatio)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:tradeCount2')}>{evaluationDetailQuery.data.tradeCount ?? '-'}</Descriptions.Item>
                                <Descriptions.Item
                                    label="Sharpe">{formatNumber(evaluationDetailQuery.data.sharpeRatio)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:evaluatedAt')}>{formatDateTime(evaluationDetailQuery.data.evaluatedAt)}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:metricsJson')} span={2}>
                                    <Typography.Paragraph style={{marginBottom: 0}}>
                                        {evaluationDetailQuery.data.metricsJson || '{}'}
                                    </Typography.Paragraph>
                                </Descriptions.Item>
                            </Descriptions>
                        ) : null}
                        <Card title={t('pages:actions2')} size="small">
                            <Space wrap>
                                <Button type="primary" loading={evaluateMutation.isPending} onClick={handleEvaluate}>
                                    {t('pages:runEvaluation')}</Button>
                                <Button onClick={() => evaluationDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}</Button>
                            </Space>
                        </Card>
                    </Space>
                )}
            </Drawer>
        </>
    );
}
