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
    useTranslation('pages');
    const {message} = App.useApp();
    const [queryForm] = useLocalizedForm<PublishesListFilters>();
    const [publishForm] = useLocalizedForm<{ displayName?: string; strategyVersionId?: string }>();
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
            title: t('pages:publishRecordId'),
            dataIndex: 'publishRecordId',
            key: 'publishRecordId',
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
            title: t('pages:researchConfigurationId'),
            dataIndex: 'researchConfigId',
            key: 'researchConfigId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:backtestConfigurationId'),
            dataIndex: 'backtestConfigId',
            key: 'backtestConfigId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:sourceStrategyId'),
            dataIndex: 'sourceStrategyId',
            key: 'sourceStrategyId',
            width: 180,
        },
        {
            title: t('pages:strategyVersionId'),
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 220,
            render: (value: string | null) => value ? <Typography.Text copyable>{value}</Typography.Text> : '-',
        },
        {
            title: t('pages:publishStatus'),
            dataIndex: 'publishStatus',
            key: 'publishStatus',
            width: 120,
            render: (value: string | null) => value ? <Tag color="blue">{value}</Tag> : '-',
        },
        {
            title: t('pages:publishedAt'),
            dataIndex: 'publishedAt',
            key: 'publishedAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: t('pages:publishName'),
            dataIndex: 'publishName',
            key: 'publishName',
            width: 180,
            render: (value: string | null) => value || '-',
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
                    message.success(t('pages:publishActionCompletedWithTheLatestResult'));
                    publishDetailQuery.refetch();
                    setSearchVersion((value) => value + 1);
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
                    <PageHero
                        title={t('pages:publishResults')}
                        description={t('pages:viewBacktestPublishRecordsStrategyVersionBindingsAndFailureDetailsPublishAnExistingRunFromItsDetails')}
                        badge="Publishes"
                    />
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title={t('pages:filters')}
                    extra={(
                        <Space>
                            <Button type="primary" onClick={() => queryForm.submit()}>
                                {t('pages:search')}</Button>
                            <Button onClick={handleReset}>
                                {t('pages:reset')}</Button>
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
                                <Form.Item label={t('pages:sourceStrategyId')} name="sourceStrategyId">
                                    <Input placeholder={t('pages:filterBySourceStrategyId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:strategyVersionId')} name="strategyVersionId">
                                    <Input placeholder={t('pages:filterByStrategyVersionId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:publishStatus')} name="publishStatus">
                                    <Select allowClear placeholder={t('pages:allStatuses')} options={PUBLISH_STATUS_OPTIONS}/>
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
                        <Empty description={t('pages:searchToLoadPublishResults')}/>
                    ) : publishesQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryPublishResults')}
                            description={formatApiError(publishesQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    {t('pages:retry')}</Button>
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
                                emptyText: t('pages:noPublishResultsMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedRow)}
                width={760}
                title={t('pages:publishDetails')}
                onClose={() => setSelectedRow(null)}
                destroyOnClose
            >
                {!selectedRow ? null : (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label={t('pages:backtestRunId')}>{selectedRow.backtestRunId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:researchConfigurationId')}>{selectedRow.researchConfigId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:backtestConfigurationId')}>{selectedRow.backtestConfigId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:sourceStrategyId')}>{selectedRow.sourceStrategyId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyVersionId')}>{selectedRow.strategyVersionId || '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:publishStatus')}>{selectedRow.publishStatus || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:publishedAt')}>{formatDateTime(selectedRow.publishedAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:publishName')}>{selectedRow.publishName || '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:failureCode')}>{selectedRow.failureCode || '-'}</Descriptions.Item>
                        </Descriptions>
                        {publishDetailQuery.isLoading ? (
                            <Alert type="info" showIcon message={t('pages:loadingPublishDetails')}/>
                        ) : publishDetailQuery.error ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:fullPublishDetailsAreNotAvailable')}
                                description={formatApiError(publishDetailQuery.error as AppApiError)}
                            />
                        ) : publishDetailQuery.data ? (
                            <Descriptions bordered column={2} size="small">
                                <Descriptions.Item
                                    label={t('pages:publishRecordId')}>{publishDetailQuery.data.publishRecordId}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:targetStrategyDefinitionId')}>{publishDetailQuery.data.targetStrategyDefinitionId || '-'}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:strategyVersionId')}>{publishDetailQuery.data.strategyVersionId || '-'}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:publishStatus')}>{publishDetailQuery.data.publishStatus}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:publishedAt')}>{formatDateTime(publishDetailQuery.data.publishedAt)}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:publishName')}>{publishDetailQuery.data.publishName || '-'}</Descriptions.Item>
                                <Descriptions.Item
                                    label={t('pages:failureDiagnostics')}>{publishDetailQuery.data.failureMessage || '-'}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:versionSnapshot')} span={2}>
                                    <Typography.Paragraph style={{marginBottom: 0}}>
                                        {publishDetailQuery.data.versionSnapshotJson || '{}'}
                                    </Typography.Paragraph>
                                </Descriptions.Item>
                            </Descriptions>
                        ) : null}
                        <Card title={t('pages:actions2')} size="small">
                            <Form form={publishForm} layout="vertical" onFinish={handlePublish}>
                                <Form.Item label={t('pages:publishName')} name="displayName">
                                    <Input placeholder={t('pages:optionalUsesTheDefaultNameIfEmpty')}/>
                                </Form.Item>
                                <Form.Item label={t('pages:strategyVersionId')} name="strategyVersionId">
                                    <Input placeholder={t('pages:optionalFreezesTheVersionSnapshotInThePublishRecordWhenSupplied')}/>
                                </Form.Item>
                                <Space wrap>
                                    <Button type="primary" htmlType="submit" loading={publishMutation.isPending}>
                                        {t('pages:publish')}</Button>
                                    <Button onClick={() => publishDetailQuery.refetch()}>
                                        {t('pages:refreshDetails')}</Button>
                                </Space>
                            </Form>
                        </Card>
                    </Space>
                )}
            </Drawer>
        </>
    );
}
