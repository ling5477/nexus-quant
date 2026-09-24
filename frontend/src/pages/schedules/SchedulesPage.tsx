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
import {BOOLEAN_FILTER_OPTIONS, SCHEDULE_STATUS_OPTIONS, SCHEDULE_TYPE_OPTIONS} from '@/constants/filter-options';
import {
    useScheduleDetailQuery,
    useScheduleListQuery,
    useUpdateScheduleStatusMutation,
} from '@/features/schedules/hooks/useScheduleListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultStrategyScheduleListFilters,
    type StrategyScheduleListFilters,
    type StrategyScheduleListItem,
} from '@/features/schedules/types/schedules';
import {containsIgnoreCase, formatDateTime, matchesBooleanFilter, normalizeOptionalText} from '@/utils/formatters';

type ScheduleRow = StrategyScheduleListItem;

export function SchedulesPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const [form] = useLocalizedForm<StrategyScheduleListFilters>();
    const [submittedFilters, setSubmittedFilters] = useState<StrategyScheduleListFilters>(defaultStrategyScheduleListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedScheduleId, setSelectedScheduleId] = useState<string | null>(null);
    const schedulesQuery = useScheduleListQuery(submittedFilters.strategyId, searchVersion);
    const scheduleDetailQuery = useScheduleDetailQuery(selectedScheduleId);
    const updateStatusMutation = useUpdateScheduleStatusMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = (schedulesQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.scheduleType, submittedFilters.scheduleType)
        && containsIgnoreCase(item.status, submittedFilters.status)
        && matchesBooleanFilter(item.enabled, submittedFilters.enabled)
    ));

    const scheduleColumns: ColumnsType<ScheduleRow> = [
        {
            title: t('pages:scheduleId'),
            dataIndex: 'scheduleJobId',
            key: 'scheduleJobId',
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
            title: t('pages:scheduleType'),
            dataIndex: 'scheduleType',
            key: 'scheduleType',
            width: 140,
        },
        {
            title: 'Cron',
            dataIndex: 'cronExpr',
            key: 'cronExpr',
            width: 180,
            render: (value: string) => value || '-',
        },
        {
            title: t('pages:status'),
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: string) => <Tag color="blue">{value}</Tag>,
        },
        {
            title: t('pages:enable'),
            dataIndex: 'enabled',
            key: 'enabled',
            width: 100,
            render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? t('pages:yes') : t('pages:no')}</Tag>,
        },
        {
            title: t('pages:lastTriggered'),
            dataIndex: 'lastTriggeredAt',
            key: 'lastTriggeredAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: t('pages:updatedAt'),
            dataIndex: 'updatedAt',
            key: 'updatedAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: t('pages:actions'),
            key: 'action',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button type="link" onClick={() => setSelectedScheduleId(record.scheduleJobId)}>
                    {t('pages:viewDetails')}</Button>
            ),
        },
    ];

    const handleSearch = (values: StrategyScheduleListFilters) => {
        setSubmittedFilters({
            strategyId: normalizeOptionalText(values.strategyId),
            scheduleType: normalizeOptionalText(values.scheduleType),
            status: normalizeOptionalText(values.status),
            enabled: values.enabled ?? 'all',
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        form.resetFields();
        setSubmittedFilters(defaultStrategyScheduleListFilters);
        setSearchVersion(0);
    };

    const handleStatusUpdate = (enabled: boolean) => {
        if (!scheduleDetailQuery.data) {
            return;
        }

        updateStatusMutation.mutate(
            {
                scheduleId: scheduleDetailQuery.data.scheduleJobId,
                request: {enabled},
            },
            {
                onSuccess: () => {
                    message.success(enabled ? t('pages:scheduleEnabled') : t('pages:scheduleDisabled'));
                    scheduleDetailQuery.refetch();
                },
                onError: (error) => {
                    showApiError(error as AppApiError, message);
                },
            },
        );
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:schedules')}
                        description={t('pages:viewSchedulesRunStatusAndEnableOrDisableActionsByStrategyDuringTheFreezeOnlyExistingSchedulingCapabi')}
                        badge="Schedules"
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
                        initialValues={defaultStrategyScheduleListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:strategyId')}
                                    name="strategyId"
                                    rules={[{required: true, message: t('pages:enterStrategyid')}]}
                                >
                                    <Input placeholder={t('pages:requiredForExampleStrategy001')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:scheduleType')} name="scheduleType">
                                    <Select allowClear placeholder={t('pages:allTypes')} options={SCHEDULE_TYPE_OPTIONS}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:status')} name="status">
                                    <Select allowClear placeholder={t('pages:allStatuses')} options={SCHEDULE_STATUS_OPTIONS}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:enabledStatus')} name="enabled">
                                    <Select
                                        options={BOOLEAN_FILTER_OPTIONS}
                                    />
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
                        <Empty description={t('pages:enterAStrategyIdAndSearch')}/>
                    ) : schedulesQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQuerySchedules')}
                            description={formatApiError(schedulesQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    {t('pages:retry')}</Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="scheduleJobId"
                            columns={scheduleColumns}
                            dataSource={visibleItems}
                            loading={schedulesQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1540}}
                            locale={{
                                emptyText: t('pages:noSchedulesMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedScheduleId)}
                width={720}
                title={t('pages:scheduleDetails')}
                onClose={() => setSelectedScheduleId(null)}
                destroyOnClose
            >
                {scheduleDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingScheduleDetails')}/>
                ) : scheduleDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadScheduleDetails')}
                        description={formatApiError(scheduleDetailQuery.error as AppApiError)}
                    />
                ) : scheduleDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item
                                label={t('pages:scheduleId')}>{scheduleDetailQuery.data.scheduleJobId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyId')}>{scheduleDetailQuery.data.strategyId}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:scheduleType')}>{scheduleDetailQuery.data.scheduleType}</Descriptions.Item>
                            <Descriptions.Item
                                label="Cron">{scheduleDetailQuery.data.cronExpr || '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:timeZone')}>{scheduleDetailQuery.data.timezone}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:status')}>
                                <Tag color="blue">{scheduleDetailQuery.data.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:enabledStatus')}>
                                <Tag color={scheduleDetailQuery.data.enabled ? 'success' : 'default'}>
                                    {scheduleDetailQuery.data.enabled ? t('pages:enabled') : t('pages:disabled')}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:lastTriggered')}>{formatDateTime(scheduleDetailQuery.data.lastTriggeredAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:exchange')}>{scheduleDetailQuery.data.exchangeCode}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:account')}>{scheduleDetailQuery.data.accountId ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:tradingEnvironment')}>{scheduleDetailQuery.data.tradeEnv}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:createdAt')}>{formatDateTime(scheduleDetailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:updatedAt')}>{formatDateTime(scheduleDetailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:windowConfiguration')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {scheduleDetailQuery.data.windowConfig || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:deduplicationScope')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {scheduleDetailQuery.data.dedupScope || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title={t('pages:actions2')} size="small">
                            <Space wrap>
                                <Button
                                    type="primary"
                                    disabled={scheduleDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(true)}
                                >
                                    {t('pages:enableSchedule')}</Button>
                                <Button
                                    danger
                                    disabled={!scheduleDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(false)}
                                >
                                    {t('pages:disableSchedule')}</Button>
                                <Button onClick={() => scheduleDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}</Button>
                            </Space>
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
        </>
    );
}
