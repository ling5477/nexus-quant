import {businessColumns} from '@/components/layout/business-columns';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    App,
    Button,
    Card,
    Col,
    Collapse,
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
import {useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useAuthStore} from '@/store/auth-store';
import {ResearchResultRecovery, usePendingResultAction} from '@/features/ResearchResultRecovery';
import {submitResultAction} from '@/features/research-result-recovery';

import {formatApiError, showApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {EVALUATION_STATUS_OPTIONS} from '@/constants/filter-options';
import {
    useEvaluateMutation,
    useEvaluationRequestQuery,
    useEvaluationDetailQuery,
    useEvaluationsListQuery,
} from '@/pages/evaluations/useEvaluationsListQuery';
import type {AppApiError} from '@/types/api';
import type {ResearchValidity} from '@/types/evaluations';
import {
    type BacktestEvaluationListItem,
    defaultEvaluationsListFilters,
    type EvaluationsListFilters,
} from '@/types/evaluations';
import {
    formatDateTime,
    formatNumber,
    formatRatioPercent,
    normalizeOptionalText,
} from '@/utils/formatters';
import {FrozenFeeSourceSummary} from '@/features/backtests/FrozenFeeSourceSummary';
import {executionObject, accountFeeMode} from '@/features/backtests/fee-source';
import {annualizedReturnPresentation} from '@/features/backtests/annualized-return';

type EvaluationRow = BacktestEvaluationListItem;

// 标签与数值上下阅读，窄屏每行只放一个字段，避免长英文标签挤窄精确数值和时间。
// 各断点均显式声明，避免组件在宽屏补回默认三列，挤窄固定宽度抽屉中的值。
const evaluationDescriptionColumns = {xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2} as const;
const evaluationDescriptionStyles = {
    label: {whiteSpace: 'nowrap' as const},
    content: {overflowWrap: 'anywhere' as const},
};

/** 年化缺失原因直接可读，窄屏同样保留；不依赖悬停或要求用户解析原始指标JSON。 */
function AnnualizedReturnValue({
    value,
    metricsJson,
}: {
    value: number | string | null;
    metricsJson: string | null;
}) {
    const {t: translate} = useTranslation('pages');
    const presentation = annualizedReturnPresentation(value, metricsJson);
    return (
        <Space direction="vertical" size={2}>
            <span>{presentation.value}</span>
            {presentation.reasonKey && (
                <Typography.Text type="secondary">
                    {translate(presentation.reasonKey)}
                </Typography.Text>
            )}
        </Space>
    );
}

export function researchPercent(value: number | string | null | undefined): string {
    return formatRatioPercent(value);
}

// 两个正式页面消费同一只读契约；这里只格式化数值，不计算基准或划分区间。
export function ResearchValidityDetails({value}: {value?: ResearchValidity | null}) {
    useTranslation('pages');
    const missing = (status?: string) =>
        status === 'INSUFFICIENT_DATA'
            ? t('pages:researchInsufficientData')
            : t('pages:researchNotAvailable');
    const amount = (number?: number | string | null) =>
        number == null ? '—' : formatNumber(number, 8);
    const benchmark = value?.benchmark;
    // 只展示评估自身的冻结执行模型，不能查询当前账户费率替代历史假设。
    let frozenExecution: Record<string, unknown> | null = null;
    try {
        if (value?.assumptions?.executionSpecJson)
            frozenExecution = executionObject(value.assumptions.executionSpecJson);
    } catch {
        // 缺失或损坏的来源保留未知，不默认解释为手动假设或零费率。
    }
    const frozenSource = frozenExecution?.costSource;
    const sourceLabel =
        frozenSource === accountFeeMode
            ? t('pages:feeModel.current')
            : frozenSource === 'EXPERIMENT_ASSUMPTION'
              ? t('pages:feeModel.assumption')
              : t('pages:feeModel.frozenUnavailable');
    return (
        <Card title={t('pages:researchValidity')} size="small" data-testid="research-validity">
            <Space direction="vertical" style={{display: 'flex'}}>
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:researchChronologicalNotice')}
                    description={t('pages:researchContinuityNotice')}
                />
                {value?.validationStatus !== 'AVAILABLE' && (
                    <Alert
                        type="warning"
                        showIcon
                        message={missing(value?.validationStatus)}
                        description={value?.reason || undefined}
                    />
                )}
                <Descriptions
                    className="research-fact-details"
                    layout="vertical"
                    bordered
                    column={evaluationDescriptionColumns}
                    styles={evaluationDescriptionStyles}
                    size="small"
                >
                    <Descriptions.Item label={t('pages:strategyVersionId')}>
                        {value?.identity?.strategyVersionId ?? '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:datasetId')}>
                        {value?.identity?.datasetId ?? '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchInitialCapital')}>
                        {amount(value?.assumptions?.initialCapital)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:feeModel.source')}>
                        {sourceLabel}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchFeeRate')}>
                        {researchPercent(value?.assumptions?.feeRate)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchSlippageBps')}>
                        {amount(value?.assumptions?.slippageBps)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchTiming')}>
                        {value?.assumptions ? t('pages:researchNextOpen') : '—'}
                    </Descriptions.Item>
                </Descriptions>
                {frozenSource === accountFeeMode && (
                    <FrozenFeeSourceSummary
                        configuration={JSON.stringify({executionSpec: frozenExecution})}
                    />
                )}
                <Table
                    size="small"
                    pagination={false}
                    rowKey="key"
                    scroll={{x: 1060}}
                    dataSource={[
                        {key: 'full', label: t('pages:researchFull'), segment: value?.full},
                        {key: 'is', label: t('pages:researchInSample'), segment: value?.inSample},
                        {
                            key: 'oos',
                            label: t('pages:researchOutOfSample'),
                            segment: value?.outOfSample,
                        },
                    ]}
                    columns={[
                        {title: t('pages:researchPeriod'), dataIndex: 'label'},
                        {
                            title: t('pages:researchInterval'),
                            render: (_, row) =>
                                row.segment
                                    ? `${formatDateTime(row.segment.startTime)} → ${formatDateTime(row.segment.endTime)}`
                                    : missing(value?.validationStatus),
                        },
                        {
                            title: t('pages:researchBars'),
                            render: (_, row) => row.segment?.barCount ?? '—',
                        },
                        {
                            title: t('pages:researchStrategyReturn'),
                            render: (_, row) => researchPercent(row.segment?.strategyReturn),
                        },
                        {
                            title: t('pages:netReturn'),
                            render: (_, row) => amount(row.segment?.netPnl),
                        },
                        {
                            title: t('pages:maximumDrawdown'),
                            render: (_, row) => researchPercent(row.segment?.maxDrawdownRate),
                        },
                        {
                            title: t('pages:tradeCount2'),
                            render: (_, row) => row.segment?.tradeCount ?? '—',
                        },
                        {
                            title: t('pages:researchFee'),
                            render: (_, row) => amount(row.segment?.fee),
                        },
                        {
                            title: t('pages:researchSlippage'),
                            render: (_, row) => amount(row.segment?.slippage),
                        },
                    ]}
                />
                <Descriptions
                    className="research-fact-details"
                    layout="vertical"
                    bordered
                    column={evaluationDescriptionColumns}
                    styles={evaluationDescriptionStyles}
                    size="small"
                >
                    <Descriptions.Item label={t('pages:researchBenchmarkReturn')}>
                        {researchPercent(benchmark?.benchmarkReturn)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchBenchmarkEquity')}>
                        {amount(benchmark?.finalEquity)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchDifference')}>
                        {researchPercent(value?.strategyVsBenchmarkDifference)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchValuation')}>
                        {benchmark ? t('pages:researchMarkToMarket') : '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchEntryTime')}>
                        {formatDateTime(benchmark?.entryTime)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchEndTime')}>
                        {formatDateTime(benchmark?.endTime)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchEntryPrice')}>
                        {amount(benchmark?.entryPrice)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchEndPrice')}>
                        {amount(benchmark?.endPrice)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchFee')}>
                        {amount(benchmark?.fee)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:researchSlippage')}>
                        {amount(benchmark?.slippage)}
                    </Descriptions.Item>
                </Descriptions>
                {benchmark?.status !== 'AVAILABLE' && (
                    <Typography.Text>
                        {missing(benchmark?.status)}
                        {benchmark?.reason ? ` · ${benchmark.reason}` : ''}
                    </Typography.Text>
                )}
            </Space>
        </Card>
    );
}

export function EvaluationsPage() {
    const lastDetailTrigger = useRef<string | null>(null);
    useTranslation('pages');
    const {message} = App.useApp();
    const [form] = useLocalizedForm<EvaluationsListFilters>();
    const [submittedFilters, setSubmittedFilters] = useState<EvaluationsListFilters>(
        defaultEvaluationsListFilters,
    );
    const [searchVersion, setSearchVersion] = useState(1);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const currentUser = useAuthStore((state) => state.currentUser);
    const [selectedListRow, setSelectedListRow] = useState<{
        owner: number;
        row: EvaluationRow;
    } | null>(null);
    const evaluationsQuery = useEvaluationsListQuery(
        {...submittedFilters, limit: pageSize, offset: (currentPage - 1) * pageSize},
        searchVersion,
    );
    const [params, setParams] = useSearchParams();
    const requestId = params.get('evaluationRequestId');
    const selectedId = params.get('evaluationId');
    const canonicalDetailQuery = useEvaluationDetailQuery(requestId ? null : selectedId);
    const originalDetailQuery = useEvaluationRequestQuery(requestId);
    const evaluationDetailQuery = requestId ? originalDetailQuery : canonicalDetailQuery;
    // 详情是正式事实；列表行仅在首次详情尚未返回时提供预览，不能盖住刷新后的结果。
    const selectedRow =
        evaluationDetailQuery.data &&
        (requestId || evaluationDetailQuery.data.evalReportId === selectedId)
            ? evaluationDetailQuery.data
            : !requestId &&
                selectedId &&
                selectedListRow &&
                selectedListRow.owner === currentUser?.userId &&
                selectedListRow.row.evalReportId === selectedId
              ? selectedListRow.row
              : null;
    const setSelectedRow = (row: EvaluationRow | null) => {
        setSelectedListRow(row && currentUser ? {owner: currentUser.userId, row} : null);
        const next = new URLSearchParams(params);
        if (row) next.set('evaluationId', row.evalReportId);
        else next.delete('evaluationId');
        if (row && 'evaluationRequestId' in row && row.evaluationRequestId)
            next.set('evaluationRequestId', String(row.evaluationRequestId));
        else next.delete('evaluationRequestId');
        setParams(next);
    };
    const pendingEvaluation = usePendingResultAction('EVALUATE');
    const roles = currentUser?.roles ?? [];
    const canWrite = roles.some((role) =>
        ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
    );
    const evaluateMutation = useEvaluateMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = evaluationsQuery.data?.items ?? [];

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
            render: (value: number | null) => `${formatNumber(value, 2)} USDT`,
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
            render: (value: number | null, record) =>
                formatRatioPercent(record.totalReturnRate ?? value),
        },
        {
            title: t('pages:maximumDrawdown'),
            dataIndex: 'maxDrawdownRate',
            key: 'maxDrawdownRate',
            width: 120,
            render: (value: number | null) => formatRatioPercent(value),
        },
        {
            title: t('pages:winRate'),
            dataIndex: 'winRate',
            key: 'winRate',
            width: 120,
            render: (value: number | null) => formatRatioPercent(value),
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
                <Button
                    id={'evaluation-detail-' + record.evalReportId}
                    type="link"
                    onClick={(event) => {
                        lastDetailTrigger.current = event.currentTarget.id;
                        setSelectedRow(record);
                    }}
                >
                    {t('pages:viewDetails')}
                </Button>
            ),
        },
    ];

    const handleSearch = (values: EvaluationsListFilters) => {
        setCurrentPage(1);
        setSubmittedFilters({
            researchConfigId: normalizeOptionalText(values.researchConfigId),
            backtestConfigId: normalizeOptionalText(values.backtestConfigId),
            backtestRunId: normalizeOptionalText(values.backtestRunId),
            evaluationStatus: normalizeOptionalText(values.evaluationStatus),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        form.resetFields();
        setSubmittedFilters(defaultEvaluationsListFilters);
        setSearchVersion(1);
        setCurrentPage(1);
    };

    const handleEvaluate = async () => {
        if (!selectedRow || !canWrite || pendingEvaluation) return;
        const ownerAtSubmit = currentUser?.userId;
        try {
            const result = await submitResultAction(
                'EVALUATE',
                selectedRow.backtestRunId,
                (requestId) =>
                    evaluateMutation.mutateAsync({runId: selectedRow.backtestRunId, requestId}),
            );
            if (useAuthStore.getState().currentUser?.userId !== ownerAtSubmit) return;
            setSelectedRow(result);
            setSearchVersion((value) => value + 1);
            if (result.evaluationStatus === 'SUCCEEDED')
                message.success(t('pages:evaluationCompletedWithTheLatestResult'));
            else message.warning(t('pages:resultRecovery.failed'));
        } catch (error) {
            if (useAuthStore.getState().currentUser?.userId !== ownerAtSubmit) return;
            showApiError(error as AppApiError, message);
        }
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <ResearchResultRecovery kind="EVALUATE" />
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:evaluationResults')}
                        description={t(
                            'pages:viewBacktestEvaluationReportsReturnAndRiskMetricsAndStatusEvaluateAnExistingRunFromItsDetails',
                        )}
                        badge="Evaluations"
                    />
                </Card>
                <Card
                    className="page-section workspace-filter-panel"
                    bordered={false}
                    title={t('pages:filters')}
                    extra={
                        <Space>
                            <Button
                                id="evaluations-query"
                                type="primary"
                                onClick={() => form.submit()}
                            >
                                {t('pages:search')}
                            </Button>
                            <Button onClick={handleReset}>{t('pages:reset')}</Button>
                        </Space>
                    }
                >
                    <Form
                        form={form}
                        layout="vertical"
                        initialValues={defaultEvaluationsListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:researchConfigurationId')}
                                    name="researchConfigId"
                                >
                                    <Input
                                        placeholder={t('pages:filterByResearchConfigurationId')}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:backtestConfigurationId')}
                                    name="backtestConfigId"
                                >
                                    <Input
                                        placeholder={t('pages:filterByBacktestConfigurationId')}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:backtestRunId')} name="backtestRunId">
                                    <Input placeholder={t('pages:filterByBacktestRunId')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                {/* 空字符串仍表示全部状态；映射为空值以显示提示，不改变提交口径。 */}
                                <Form.Item
                                    label={t('pages:evaluationStatus')}
                                    name="evaluationStatus"
                                    getValueProps={(value?: string) => ({
                                        value: value || undefined,
                                    })}
                                >
                                    <Select
                                        allowClear
                                        placeholder={t('pages:allStatuses')}
                                        options={EVALUATION_STATUS_OPTIONS}
                                    />
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>
                <Card
                    className="page-section workspace-results-panel"
                    bordered={false}
                    title={t('pages:results')}
                    extra={
                        hasSearched ? (
                            <Typography.Text type="secondary">
                                {t('pages:total')}
                                {evaluationsQuery.data?.total ?? 0} {t('pages:records')}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {!hasSearched ? (
                        <Empty description={t('product:loaded')} />
                    ) : evaluationsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryEvaluationResults')}
                            description={formatApiError(evaluationsQuery.error as AppApiError)}
                            action={
                                <Button
                                    size="small"
                                    onClick={() => setSearchVersion((value) => value + 1)}
                                >
                                    {t('pages:retry')}
                                </Button>
                            }
                        />
                    ) : (
                        <Table
                            className="evaluation-result-list-table"
                            rowKey="evalReportId"
                            columns={businessColumns(evaluationColumns)}
                            dataSource={visibleItems}
                            loading={evaluationsQuery.isFetching}
                            pagination={{
                                current: currentPage,
                                pageSize,
                                total: evaluationsQuery.data?.total ?? 0,
                                showSizeChanger: true,
                                responsive: true,
                                showLessItems: true,
                                pageSizeOptions: [10, 20, 50, 100],
                                onChange: (page, size) => {
                                    setCurrentPage(size === pageSize ? page : 1);
                                    setPageSize(size);
                                },
                            }}
                            scroll={{x: 2460}}
                            locale={{
                                emptyText: t('pages:noEvaluationResultsMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedId || requestId)}
                width={760}
                title={t('pages:evaluationDetails')}
                onClose={() => setSelectedRow(null)}
                afterOpenChange={(open) => {
                    if (open) return;
                    // rc-drawer在回调之后恢复其旧焦点引用；微任务在该同步恢复完成后再定位当前按钮。
                    // 列表节点可能已重新挂载，必须使用稳定报告身份；深链关闭时返回查询。
                    queueMicrotask(() => {
                        const trigger = lastDetailTrigger.current
                            ? document.getElementById(lastDetailTrigger.current)
                            : null;
                        (trigger ?? document.getElementById('evaluations-query'))?.focus();
                    });
                }}
                destroyOnClose
            >
                {!selectedRow && evaluationDetailQuery.error && (
                    <Alert
                        type="error"
                        showIcon
                        message={formatApiError(evaluationDetailQuery.error as AppApiError)}
                        action={
                            <Button onClick={() => evaluationDetailQuery.refetch()}>
                                {t('pages:refreshDetails')}
                            </Button>
                        }
                    />
                )}
                {!selectedRow ? null : (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        {selectedRow.evaluationStatus === 'FAILED' && (
                            <Alert
                                type="error"
                                showIcon
                                message={t('pages:evaluationStatus') + ': FAILED'}
                                description={
                                    [selectedRow.failureCode, selectedRow.failureMessage]
                                        .filter(Boolean)
                                        .join(' · ') || t('pages:researchNotAvailable')
                                }
                            />
                        )}
                        {!evaluationDetailQuery.data && (
                            <Descriptions
                                className="research-fact-details"
                                layout="vertical"
                                bordered
                                column={evaluationDescriptionColumns}
                                styles={evaluationDescriptionStyles}
                                size="small"
                            >
                                <Descriptions.Item label={t('pages:backtestRunId')}>
                                    {selectedRow.backtestRunId}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:evaluationReportId')}>
                                    {selectedRow.evalReportId}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:evaluationStatus')}>
                                    {selectedRow.evaluationStatus || '-'}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:evaluatedAt')}>
                                    {formatDateTime(selectedRow.evaluatedAt)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:totalReturn')}>
                                    {formatRatioPercent(
                                        selectedRow.totalReturnRate ?? selectedRow.totalReturn,
                                    )}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:annualizedReturn')}>
                                    <AnnualizedReturnValue
                                        value={selectedRow.annualizedReturn}
                                        metricsJson={selectedRow.metricsJson}
                                    />
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:maximumDrawdown')}>
                                    {formatRatioPercent(selectedRow.maxDrawdownRate)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:winRate')}>
                                    {formatRatioPercent(selectedRow.winRate)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:profitLossRatio')}>
                                    {formatNumber(selectedRow.profitLossRatio)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:tradeCount2')}>
                                    {selectedRow.tradeCount ?? '-'}
                                </Descriptions.Item>
                                <Descriptions.Item label="Sharpe">
                                    {formatNumber(selectedRow.sharpeRatio)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:metricsJson')} span="filled">
                                    <Typography.Paragraph style={{marginBottom: 0}}>
                                        {selectedRow.metricsJson || '{}'}
                                    </Typography.Paragraph>
                                </Descriptions.Item>
                            </Descriptions>
                        )}
                        {evaluationDetailQuery.isLoading ? (
                            <Alert
                                type="info"
                                showIcon
                                message={t('pages:loadingEvaluationDetails')}
                            />
                        ) : evaluationDetailQuery.error ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:fullEvaluationDetailsAreNotAvailable')}
                                description={formatApiError(
                                    evaluationDetailQuery.error as AppApiError,
                                )}
                            />
                        ) : evaluationDetailQuery.data ? (
                            <Descriptions
                                className="research-fact-details"
                                layout="vertical"
                                bordered
                                column={evaluationDescriptionColumns}
                                styles={evaluationDescriptionStyles}
                                size="small"
                            >
                                <Descriptions.Item label={t('pages:backtestRunId')}>
                                    {evaluationDetailQuery.data.backtestRunId}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:evaluationReportId')}>
                                    {evaluationDetailQuery.data.evalReportId}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:evaluationStatus')}>
                                    {evaluationDetailQuery.data.evaluationStatus}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:initialCapital')}>
                                    {formatNumber(evaluationDetailQuery.data.initialCapital, 2)}{' '}
                                    USDT
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:finalEquity')}>
                                    {formatNumber(evaluationDetailQuery.data.finalEquity, 2)} USDT
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:netReturn')}>
                                    {formatNumber(evaluationDetailQuery.data.netPnl, 2)} USDT
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:totalReturnRate')}>
                                    {formatRatioPercent(evaluationDetailQuery.data.totalReturnRate)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:totalReturn')}>
                                    {formatRatioPercent(evaluationDetailQuery.data.totalReturn)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:annualizedReturn')}>
                                    <AnnualizedReturnValue
                                        value={evaluationDetailQuery.data.annualizedReturn}
                                        metricsJson={evaluationDetailQuery.data.metricsJson}
                                    />
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:maximumDrawdownRate')}>
                                    {formatRatioPercent(evaluationDetailQuery.data.maxDrawdownRate)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:winRate')}>
                                    {formatRatioPercent(evaluationDetailQuery.data.winRate)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:profitLossRatio')}>
                                    {formatNumber(evaluationDetailQuery.data.profitLossRatio)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:tradeCount2')}>
                                    {evaluationDetailQuery.data.tradeCount ?? '-'}
                                </Descriptions.Item>
                                <Descriptions.Item label="Sharpe">
                                    {formatNumber(evaluationDetailQuery.data.sharpeRatio)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:evaluatedAt')}>
                                    {formatDateTime(evaluationDetailQuery.data.evaluatedAt)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:metricsJson')} span="filled">
                                    <Collapse
                                        items={[
                                            {
                                                key: 'metrics',
                                                label: t('pages:metricsJson'),
                                                children: (
                                                    <Typography.Paragraph
                                                        style={{
                                                            marginBottom: 0,
                                                            overflowWrap: 'anywhere',
                                                        }}
                                                    >
                                                        {evaluationDetailQuery.data.metricsJson ||
                                                            '{}'}
                                                    </Typography.Paragraph>
                                                ),
                                            },
                                        ]}
                                    />
                                </Descriptions.Item>
                            </Descriptions>
                        ) : null}
                        <ResearchValidityDetails
                            value={
                                evaluationDetailQuery.data?.researchValidity ??
                                selectedRow.researchValidity
                            }
                        />
                        <Card title={t('pages:actions2')} size="small">
                            <Space wrap>
                                <Button
                                    type="primary"
                                    loading={evaluateMutation.isPending}
                                    disabled={!canWrite || Boolean(pendingEvaluation)}
                                    onClick={handleEvaluate}
                                >
                                    {t('pages:runEvaluation')}
                                </Button>
                                <Button onClick={() => evaluationDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}
                                </Button>
                            </Space>
                        </Card>
                    </Space>
                )}
            </Drawer>
        </>
    );
}
