import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
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
import {RUN_STATUS_OPTIONS, RUN_TRIGGER_TYPE_OPTIONS} from '@/constants/filter-options';
import {useRunDetailQuery, useRunListQuery} from '@/features/runs/hooks/useRunListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultStrategyRunListFilters,
    type StrategyRunDetailItem,
    type StrategyRunListFilters,
    type StrategyRunSummaryItem,
} from '@/features/runs/types/runs';
import {containsIgnoreCase, formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type RunRow = StrategyRunSummaryItem;

export function RunsPage() {
    useTranslation('pages');
    const [form] = useLocalizedForm<StrategyRunListFilters>();
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
            title: t('pages:runId'),
            dataIndex: 'strategyRunId',
            key: 'strategyRunId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:strategyId'),
            dataIndex: 'strategyId',
            key: 'strategyId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:scheduleId'),
            dataIndex: 'scheduleJobId',
            key: 'scheduleJobId',
            width: 220,
            render: (value: string | null) => value ? <Typography.Text copyable>{value}</Typography.Text> : '-',
        },
        {
            title: t('pages:triggerType'),
            dataIndex: 'triggerType',
            key: 'triggerType',
            width: 140,
        },
        {
            title: t('pages:status'),
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: string) => <Tag color="blue">{value}</Tag>,
        },
        {
            title: t('pages:startTime'),
            dataIndex: 'startedAt',
            key: 'startedAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: t('pages:endTime'),
            dataIndex: 'finishedAt',
            key: 'finishedAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: t('pages:actions'),
            key: 'action',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button type="link" onClick={() => setSelectedRunId(record.strategyRunId)}>
                    {t('pages:viewDetails')}</Button>
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
                <Descriptions.Item label={t('pages:runId')}>{detail.strategyRunId}</Descriptions.Item>
                <Descriptions.Item label={t('pages:strategyId')}>{detail.strategyId}</Descriptions.Item>
                <Descriptions.Item label={t('pages:scheduleId')}>{detail.scheduleJobId || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('pages:requestId')}>{detail.requestId || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('pages:triggerType')}>{detail.triggerType}</Descriptions.Item>
                <Descriptions.Item label={t('pages:status')}>
                    <Tag color="blue">{detail.status}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:exchange')}>{detail.exchangeCode}</Descriptions.Item>
                <Descriptions.Item label={t('pages:account')}>{detail.accountId ?? '-'}</Descriptions.Item>
                <Descriptions.Item label={t('pages:tradingEnvironment')}>{detail.tradeEnv}</Descriptions.Item>
                <Descriptions.Item label={t('pages:startTime')}>{formatDateTime(detail.startedAt)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:endTime')}>{formatDateTime(detail.finishedAt)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:errorDiagnostics')}>{detail.errorMessage || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('pages:orderCount')}>{detail.orders.length}</Descriptions.Item>
                <Descriptions.Item label={t('pages:tradeCount2')}>{detail.trades.length}</Descriptions.Item>
                <Descriptions.Item label={t('pages:ledgerSummary')} span={2}>
                    <Typography.Paragraph style={{marginBottom: 0}}>
                        {detail.ledgerSummary || '-'}
                    </Typography.Paragraph>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:riskSummary')} span={2}>
                    <Typography.Paragraph style={{marginBottom: 0}}>
                        {detail.riskSummary || '-'}
                    </Typography.Paragraph>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:eventSummary')} span={2}>
                    <Typography.Paragraph style={{marginBottom: 0}}>
                        {detail.eventSummary || '-'}
                    </Typography.Paragraph>
                </Descriptions.Item>
            </Descriptions>
            <Card title={t('pages:actions2')} size="small">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:runDetailsAreReadOnlyUnderTheCurrentBackendContractNoWriteApiIsAvailableHere')}
                    />
                    <Space>
                        <Button type="primary" disabled>
                            {t('pages:noWriteActionsAvailable')}</Button>
                        <Button onClick={() => runDetailQuery.refetch()}>
                            {t('pages:refreshDetails')}</Button>
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
                        title={t('pages:runRecords')}
                        description={t('pages:viewStrategyRunsTriggersExecutionStatusAndRiskSummariesDetailsAreReadOnlyAndCanBeRefreshed')}
                        badge={t('pages:runs')}
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
                        initialValues={defaultStrategyRunListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:strategyId')} name="strategyId">
                                    <Input placeholder={t('pages:optional')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:scheduleId')} name="scheduleId">
                                    <Input placeholder={t('pages:optional')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:runStatus')} name="status">
                                    <Select allowClear placeholder={t('pages:allStatuses')} options={RUN_STATUS_OPTIONS}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:triggerType')} name="triggerType">
                                    <Select allowClear placeholder={t('pages:allTriggerTypes')} options={RUN_TRIGGER_TYPE_OPTIONS}/>
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
                        <Empty description={t('pages:enterFiltersAndSearch')}/>
                    ) : runsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryRuns')}
                            description={formatApiError(runsQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    {t('pages:retry')}</Button>
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
                                emptyText: t('pages:noRunsMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedRunId)}
                width={760}
                title={t('pages:runDetails')}
                onClose={() => setSelectedRunId(null)}
                destroyOnClose
            >
                {runDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingRunDetails')}/>
                ) : runDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadRunDetails')}
                        description={formatApiError(runDetailQuery.error as AppApiError)}
                    />
                ) : runDetailQuery.data ? renderRunDetail(runDetailQuery.data) : null}
            </Drawer>
        </>
    );
}
