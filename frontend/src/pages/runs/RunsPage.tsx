import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    Button,
    Card,
    Col,
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
import {useEffect, useState} from 'react';
import {useSearchParams} from 'react-router-dom';

import {formatApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {RUN_STATUS_OPTIONS, RUN_TRIGGER_TYPE_OPTIONS} from '@/constants/filter-options';
import {useRunListQuery} from '@/features/runs/hooks/useRunListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultStrategyRunListFilters,
    type StrategyRunListFilters,
    type StrategyRunSummaryItem,
} from '@/features/runs/types/runs';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';

import {StrategyRunDetails} from '@/features/runs/components/StrategyRunDetails';

type RunRow = StrategyRunSummaryItem;

export function RunsPage() {
    useTranslation('pages');
    const [form] = useLocalizedForm<StrategyRunListFilters>();
    const [submittedFilters, setSubmittedFilters] = useState<StrategyRunListFilters>(
        defaultStrategyRunListFilters,
    );
    const [searchVersion, setSearchVersion] = useState(1);
    const [page, setPage] = useState(1);
    const [params, setParams] = useSearchParams();
    const [selectedRunId, setSelectedRunId] = useState<string | null>(params.get('runId'));
    useEffect(() => {
        setSelectedRunId(params.get('runId'));
    }, [params]);
    const runsQuery = useRunListQuery(
        {
            ...submittedFilters,
            limit: 10,
            offset: (page - 1) * 10,
        },
        searchVersion,
    );
    const urlStrategy = params.get('strategyId') ?? '';
    const urlSchedule = params.get('scheduleId') ?? '';
    // 来源计划直接带入正式范围；详情runId变化不能重置用户已提交的筛选。
    useEffect(() => {
        if (!urlStrategy && !urlSchedule) return;
        const next = {
            ...defaultStrategyRunListFilters,
            strategyId: urlStrategy,
            scheduleId: urlSchedule,
        };
        form.setFieldsValue(next);
        setSubmittedFilters(next);
        setPage(1);
        setSearchVersion((value) => value + 1);
    }, [urlStrategy, urlSchedule, form]);
    const hasSearched = searchVersion > 0;

    const visibleItems = runsQuery.data?.items ?? [];

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
            render: (value: string | null) =>
                value ? <Typography.Text copyable>{value}</Typography.Text> : '-',
        },
        {
            title: t('pages:triggerType'),
            dataIndex: 'triggerType',
            key: 'triggerType',
            width: 140,
            render: (value: string) =>
                RUN_TRIGGER_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? value,
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
                <Button
                    type="link"
                    onClick={() => {
                        const next = new URLSearchParams(params);
                        next.set('runId', record.strategyRunId);
                        setParams(next);
                    }}
                >
                    {t('pages:viewDetails')}
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
        setPage(1);
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        form.resetFields();
        setSubmittedFilters(defaultStrategyRunListFilters);
        setPage(1);
        setSearchVersion(1);
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:runRecords')}
                        description={t(
                            'pages:viewStrategyRunsTriggersExecutionStatusAndRiskSummariesDetailsAreReadOnlyAndCanBeRefreshed',
                        )}
                        badge={t('pages:runs')}
                    />
                </Card>
                <Card
                    className="page-section workspace-filter-panel"
                    bordered={false}
                    title={t('pages:filters')}
                    extra={
                        <Space wrap>
                            <Button type="primary" onClick={() => form.submit()}>
                                {t('pages:search')}
                            </Button>
                            <Button onClick={handleReset}>{t('pages:reset')}</Button>
                            <Button
                                disabled={!hasSearched}
                                loading={runsQuery.isFetching}
                                onClick={() => void runsQuery.refetch()}
                            >
                                {t('pages:refresh')}
                            </Button>
                        </Space>
                    }
                >
                    <Typography.Paragraph type="secondary">
                        {t('pages:runScopeHint')}
                    </Typography.Paragraph>
                    <Form
                        form={form}
                        layout="vertical"
                        initialValues={defaultStrategyRunListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:strategyId')}
                                    name="strategyId"
                                    dependencies={['scheduleId']}
                                    rules={[
                                        ({getFieldValue}) => ({
                                            validator(_, value) {
                                                return Boolean(String(value ?? '').trim()) !==
                                                    Boolean(
                                                        String(
                                                            getFieldValue('scheduleId') ?? '',
                                                        ).trim(),
                                                    )
                                                    ? Promise.resolve()
                                                    : Promise.reject(
                                                          new Error(t('pages:runScopeRequired')),
                                                      );
                                            },
                                        }),
                                    ]}
                                >
                                    <Input maxLength={128} placeholder={t('pages:optional')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:scheduleId')} name="scheduleId">
                                    <Input maxLength={128} placeholder={t('pages:optional')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:runStatus')}
                                    name="status"
                                    getValueProps={(value) => ({value: value || undefined})}
                                >
                                    <Select
                                        allowClear
                                        placeholder={t('pages:allStatuses')}
                                        options={RUN_STATUS_OPTIONS}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:triggerType')}
                                    name="triggerType"
                                    getValueProps={(value) => ({value: value || undefined})}
                                >
                                    <Select
                                        allowClear
                                        placeholder={t('pages:allTriggerTypes')}
                                        options={RUN_TRIGGER_TYPE_OPTIONS}
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
                                {runsQuery.data?.total ?? '—'} {t('pages:records')}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {!hasSearched ? (
                        <Empty description={t('pages:enterFiltersAndSearch')} />
                    ) : runsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryRuns')}
                            description={formatApiError(runsQuery.error as AppApiError)}
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
                            rowKey="strategyRunId"
                            columns={runColumns}
                            dataSource={visibleItems}
                            loading={runsQuery.isFetching}
                            pagination={{
                                current: page,
                                pageSize: 10,
                                total: runsQuery.data?.total ?? 0,
                                showSizeChanger: false,
                                onChange: setPage,
                            }}
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
                width={1120}
                title={t('pages:runDetails')}
                onClose={() => {
                    setSelectedRunId(null);
                    const next = new URLSearchParams(params);
                    next.delete('runId');
                    setParams(next);
                }}
                destroyOnClose
            >
                {selectedRunId ? (
                    <StrategyRunDetails key={selectedRunId} runId={selectedRunId} />
                ) : null}
            </Drawer>
        </>
    );
}
