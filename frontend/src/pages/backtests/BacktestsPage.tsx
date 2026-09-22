import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    App,
    Button,
    Card,
    Col,
    DatePicker,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    InputNumber,
    Row,
    Select,
    Space,
    Table,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';

import {formatApiError, showApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {PageHero} from '@/components/page/PageHero';
import {
    useBindBacktestDatasetMutation,
    useBindBacktestStrategyVersionMutation,
    useBacktestDetailQuery,
    useBacktestRunDetailQuery,
    useBacktestsListQuery,
    useCreateBacktestRunMutation,
    useCreateBacktestMutation,
} from '@/hooks/useBacktestsListQuery';
import type {AppApiError} from '@/types/api';
import type {BacktestConfigCreateRequest} from '@/types/backtests';
import {
    type BacktestConfigListItem,
    type BacktestsListFilters,
    defaultBacktestsListFilters,
} from '@/types/backtests';
import {containsIgnoreCase, formatDateTime, formatNumber, normalizeOptionalText} from '@/utils/formatters';

type BacktestRow = BacktestConfigListItem;
type DateFormValue = string | null | undefined | {
    toISOString?: () => string;
    toDate?: () => Date;
};

type BacktestConfigCreateFormValues = Omit<BacktestConfigCreateRequest, 'startTime' | 'endTime'> & {
    startTime?: DateFormValue;
    endTime?: DateFormValue;
};

function toIsoDateTime(value: DateFormValue): string {
    if (!value) {
        return '';
    }
    if (typeof value === 'string') {
        return value;
    }
    if (typeof value.toISOString === 'function') {
        return value.toISOString();
    }
    if (typeof value.toDate === 'function') {
        return value.toDate().toISOString();
    }
    return String(value);
}

export function BacktestsPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const navigate = useNavigate();
    const [queryForm] = useLocalizedForm<BacktestsListFilters>();
    const [createForm] = useLocalizedForm<BacktestConfigCreateFormValues>();
    const [bindDatasetForm] = useLocalizedForm<{datasetId: string}>();
    const [bindStrategyVersionForm] = useLocalizedForm<{strategyVersionId: string}>();
    const [submittedFilters, setSubmittedFilters] = useState<BacktestsListFilters>(defaultBacktestsListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedConfigId, setSelectedConfigId] = useState<string | null>(null);
    const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
    const [createOpen, setCreateOpen] = useState(false);
    const backtestsQuery = useBacktestsListQuery(submittedFilters.researchConfigId, searchVersion);
    const backtestDetailQuery = useBacktestDetailQuery(selectedConfigId);
    const createBacktestMutation = useCreateBacktestMutation();
    const createBacktestRunMutation = useCreateBacktestRunMutation();
    const backtestRunDetailQuery = useBacktestRunDetailQuery(selectedRunId);
    const bindDatasetMutation = useBindBacktestDatasetMutation(selectedConfigId);
    const bindStrategyVersionMutation = useBindBacktestStrategyVersionMutation(selectedConfigId);
    const datasetsQuery = useQuery({
        queryKey: ['marketdata-datasets'],
        queryFn: marketdataApi.listDatasets,
        enabled: Boolean(selectedConfigId),
    });
    const hasSearched = searchVersion > 0;

    const visibleItems = (backtestsQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.backtestConfigId, submittedFilters.backtestConfigId)
        && containsIgnoreCase(item.name, submittedFilters.name)
    ));

    const backtestColumns: ColumnsType<BacktestRow> = [
        {
            title: t('pages:backtestConfigurationId'),
            dataIndex: 'backtestConfigId',
            key: 'backtestConfigId',
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
            title: t('pages:name'),
            dataIndex: 'name',
            key: 'name',
            width: 180,
        },
        {
            title: t('pages:description'),
            dataIndex: 'description',
            key: 'description',
            width: 240,
            render: (value: string) => value || '-',
        },
        {
            title: t('pages:startTime'),
            dataIndex: 'startTime',
            key: 'startTime',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: t('pages:endTime'),
            dataIndex: 'endTime',
            key: 'endTime',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: t('pages:initialCapital'),
            dataIndex: 'initialCapital',
            key: 'initialCapital',
            width: 140,
            render: (value: number | null) => formatNumber(value, 2),
        },
        {
            title: t('pages:strategyVersionId'),
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 220,
            render: (value: string | null) => value ? <Typography.Text copyable>{value}</Typography.Text> : t('pages:notBound'),
        },
        {
            title: t('pages:datasetId'),
            dataIndex: 'datasetId',
            key: 'datasetId',
            width: 220,
            render: (value: string | null) => value ? <Typography.Text copyable>{value}</Typography.Text> : t('pages:notBound'),
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
            width: 180,
            render: (_, record) => (
                <Space size={0}>
                    <Button type="link" onClick={() => setSelectedConfigId(record.backtestConfigId)}>
                        {t('pages:viewDetails')}</Button>
                    <Button type="link" onClick={() => navigate(`/backtests/${record.backtestConfigId}`)}>
                        {t('pages:visualization')}</Button>
                </Space>
            ),
        },
    ];

    const datasetOptions = (datasetsQuery.data ?? []).map((dataset) => ({
        label: `${dataset.datasetName} / ${dataset.exchangeCode} ${dataset.symbol} ${dataset.interval} / ${dataset.qualityStatus}`,
        value: dataset.datasetId,
    }));

    const handleSearch = (values: BacktestsListFilters) => {
        setSubmittedFilters({
            researchConfigId: normalizeOptionalText(values.researchConfigId),
            backtestConfigId: normalizeOptionalText(values.backtestConfigId),
            name: normalizeOptionalText(values.name),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        queryForm.resetFields();
        setSubmittedFilters(defaultBacktestsListFilters);
        setSearchVersion(0);
    };

    const handleCreate = (values: BacktestConfigCreateFormValues) => {
        createBacktestMutation.mutate(
            {
                ...values,
                researchConfigId: normalizeOptionalText(values.researchConfigId),
                name: normalizeOptionalText(values.name),
                description: normalizeOptionalText(values.description),
                startTime: toIsoDateTime(values.startTime),
                endTime: toIsoDateTime(values.endTime),
                executionSpec: normalizeOptionalText(values.executionSpec),
                evaluationSpec: normalizeOptionalText(values.evaluationSpec),
            },
            {
                onSuccess: () => {
                    message.success(t('pages:backtestConfigurationCreated'));
                    setCreateOpen(false);
                    createForm.resetFields();
                    setSearchVersion((value) => (value === 0 ? 1 : value + 1));
                },
                onError: (error) => {
                    showApiError(error as AppApiError, message);
                },
            },
        );
    };

    const handleBindDataset = (values: {datasetId: string}) => {
        if (!selectedConfigId) {
            return;
        }
        bindDatasetMutation.mutate(values, {
            onSuccess: () => {
                message.success(t('pages:datasetBoundToTheBacktestConfiguration'));
                bindDatasetForm.resetFields();
            },
            onError: (error) => {
                showApiError(error as AppApiError, message);
            },
        });
    };

    const handleBindStrategyVersion = (values: {strategyVersionId: string}) => {
        if (!selectedConfigId) {
            return;
        }
        bindStrategyVersionMutation.mutate(
            {strategyVersionId: normalizeOptionalText(values.strategyVersionId)},
            {
                onSuccess: () => {
                    message.success(t('pages:strategyVersionBoundToTheBacktestConfiguration'));
                    bindStrategyVersionForm.resetFields();
                },
                onError: (error) => {
                    showApiError(error as AppApiError, message);
                },
            },
        );
    };

    const handleCreateRun = () => {
        if (!selectedConfigId) {
            return;
        }
        createBacktestRunMutation.mutate(selectedConfigId, {
            onSuccess: (run) => {
                message.success(t('pages:backtestRunCreatedWithTheCurrentConfigurationSnapshotFrozen'));
                setSelectedRunId(run.backtestRunId);
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
                    <PageHero
                        title={t('pages:backtestConfigurations')}
                        description={t('pages:viewBacktestConfigurationsDatasetBindingsAndStrategyVersionSnapshotsOrCreateAConfiguration')}
                        badge="Backtests"
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
                        initialValues={defaultBacktestsListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:researchConfigurationId')} name="researchConfigId">
                                    <Input placeholder={t('pages:filterByResearchConfigurationId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:backtestConfigurationId')} name="backtestConfigId">
                                    <Input placeholder={t('pages:filterByBacktestConfigurationId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:name')} name="name">
                                    <Input placeholder={t('pages:filterByConfigurationName')}/>
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title={t('pages:actions2')}
                    extra={(
                        <Button type="primary" onClick={() => setCreateOpen(true)}>
                            {t('pages:createBacktestConfiguration')}</Button>
                    )}
                >
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:createAConfigurationFromTheActionsAreaTheDetailsDrawerIsReadOnly')}
                    />
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title={t('pages:results')}
                    extra={hasSearched ?
                        <Typography.Text type="secondary">{t('pages:total')}{visibleItems.length} {t('pages:records')}</Typography.Text> : null}
                >
                    {!hasSearched ? (
                        <Empty description={t('pages:searchToLoadBacktestConfigurations')}/>
                    ) : backtestsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryBacktestConfigurations')}
                            description={formatApiError(backtestsQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    {t('pages:retry')}</Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="backtestConfigId"
                            columns={backtestColumns}
                            dataSource={visibleItems}
                            loading={backtestsQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 2040}}
                            locale={{
                                emptyText: t('pages:noBacktestConfigurationsMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedConfigId)}
                width={760}
                title={t('pages:backtestConfigurationDetails')}
                onClose={() => {
                    setSelectedConfigId(null);
                    setSelectedRunId(null);
                }}
                destroyOnClose
            >
                {backtestDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingBacktestConfigurationDetails')}/>
                ) : backtestDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadBacktestConfigurationDetails')}
                        description={formatApiError(backtestDetailQuery.error as AppApiError)}
                    />
                ) : backtestDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item
                                label={t('pages:backtestConfigurationId')}>{backtestDetailQuery.data.backtestConfigId}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:researchConfigurationId')}>{backtestDetailQuery.data.researchConfigId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:name')}>{backtestDetailQuery.data.name}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:description')}>{backtestDetailQuery.data.description || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:startTime')}>{formatDateTime(backtestDetailQuery.data.startTime)}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:endTime')}>{formatDateTime(backtestDetailQuery.data.endTime)}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:initialCapital')}>{formatNumber(backtestDetailQuery.data.initialCapital, 2)}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:createdAt')}>{formatDateTime(backtestDetailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:updatedAt')}>{formatDateTime(backtestDetailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:executionParameters')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.executionSpec || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:evaluationParameters')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.evaluationSpec || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyVersionId')} span={2}>
                                <Typography.Text copyable={Boolean(backtestDetailQuery.data.strategyVersionId)}>
                                    {backtestDetailQuery.data.strategyVersionId || t('pages:notBound')}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyVersionSnapshot')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.strategyVersionSnapshotJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:parameterSnapshot')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.paramSnapshotJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:datasetId')} span={2}>
                                <Typography.Text copyable={Boolean(backtestDetailQuery.data.datasetId)}>
                                    {backtestDetailQuery.data.datasetId || t('pages:notBound')}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:datasetSnapshot')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.datasetSnapshotJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:configurationSnapshotJson')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.configSnapshotJson || '{}'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:configurationSnapshot')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {backtestDetailQuery.data.configSnapshot || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title={t('pages:actions2')} size="small">
                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                <Alert type="info" showIcon
                                       message={t('pages:bindingAStrategyVersionOrMarketDataDatasetDoesNotStartABacktestOrChangeStrategyLogic')}/>
                                <Form
                                    form={bindStrategyVersionForm}
                                    layout="inline"
                                    onFinish={handleBindStrategyVersion}
                                >
                                    <Form.Item
                                        label={t('pages:strategyVersion')}
                                        name="strategyVersionId"
                                        rules={[{required: true, message: t('pages:enterAStrategyVersionId')}]}
                                    >
                                        <Input style={{width: 360}} placeholder={t('pages:forExampleSv')}/>
                                    </Form.Item>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={bindStrategyVersionMutation.isPending}
                                    >
                                        {t('pages:bindStrategyVersion')}</Button>
                                </Form>
                                <Form form={bindDatasetForm} layout="inline" onFinish={handleBindDataset}>
                                    <Form.Item
                                        label={t('pages:dataset')}
                                        name="datasetId"
                                        rules={[{required: true, message: t('pages:selectADataset')}]}
                                    >
                                        <Select
                                            showSearch
                                            style={{width: 420}}
                                            placeholder={t('pages:selectAMarketDataDataset')}
                                            loading={datasetsQuery.isLoading || datasetsQuery.isFetching}
                                            options={datasetOptions}
                                            optionFilterProp="label"
                                        />
                                    </Form.Item>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={bindDatasetMutation.isPending}
                                        disabled={datasetOptions.length === 0}
                                    >
                                        {t('pages:bindDataset')}</Button>
                                </Form>
                                {datasetsQuery.error ? (
                                    <Alert
                                        type="error"
                                        showIcon
                                        message={t('pages:failedToLoadDatasets')}
                                        description={formatApiError(datasetsQuery.error as AppApiError)}
                                    />
                                ) : datasetOptions.length === 0 ? (
                                    <Alert type="warning" showIcon message={t('pages:noDatasetsAreAvailableCreateOneOnTheMarketDataPageFirst')}/>
                                ) : null}
                                <Button onClick={() => backtestDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}</Button>
                                <Button
                                    onClick={handleCreateRun}
                                    loading={createBacktestRunMutation.isPending}
                                >
                                    {t('pages:createBacktestRun')}</Button>
                            </Space>
                        </Card>
                        <Card title={t('pages:backtestRunDetails')} size="small">
                            {!selectedRunId ? (
                                <Empty description={t('pages:theFrozenRunSnapshotAppearsAfterTheBacktestRunIsCreated')}/>
                            ) : backtestRunDetailQuery.isLoading ? (
                                <Alert type="info" showIcon message={t('pages:loadingBacktestRunDetails')}/>
                            ) : backtestRunDetailQuery.error ? (
                                <Alert
                                    type="error"
                                    showIcon
                                    message={t('pages:failedToLoadBacktestRunDetails')}
                                    description={formatApiError(backtestRunDetailQuery.error as AppApiError)}
                                />
                            ) : backtestRunDetailQuery.data ? (
                                <Descriptions bordered column={2} size="small">
                                    <Descriptions.Item label={t('pages:backtestRunId')} span={2}>
                                        <Typography.Text copyable>
                                            {backtestRunDetailQuery.data.backtestRunId}
                                        </Typography.Text>
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('pages:runStatus')}>{backtestRunDetailQuery.data.status}</Descriptions.Item>
                                    <Descriptions.Item
                                        label={t('pages:requestedAt')}>{formatDateTime(backtestRunDetailQuery.data.requestedAt)}</Descriptions.Item>
                                    <Descriptions.Item label={t('pages:strategyVersionSnapshot')} span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {backtestRunDetailQuery.data.strategyVersionSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('pages:datasetSnapshot')} span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {backtestRunDetailQuery.data.datasetSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('pages:parameterSnapshot')} span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {backtestRunDetailQuery.data.paramSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('pages:configurationSnapshot')} span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {backtestRunDetailQuery.data.configSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                </Descriptions>
                            ) : null}
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
            <Drawer
                open={createOpen}
                width={720}
                title={t('pages:createBacktestConfiguration')}
                onClose={() => setCreateOpen(false)}
                destroyOnClose
            >
                <Form form={createForm} layout="vertical" onFinish={handleCreate}>
                    <Form.Item label={t('pages:researchConfigurationId')} name="researchConfigId"
                               rules={[{required: true, message: t('pages:enterResearchconfigid')}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label={t('pages:name')} name="name" rules={[{required: true, message: t('pages:enterAName')}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label={t('pages:description')} name="description">
                        <Input.TextArea rows={3}/>
                    </Form.Item>
                    <Form.Item label={t('pages:startTime')} name="startTime"
                               rules={[{required: true, message: t('pages:selectAStartTime')}]}>
                        <DatePicker showTime style={{width: '100%'}}/>
                    </Form.Item>
                    <Form.Item label={t('pages:endTime')} name="endTime"
                               rules={[{required: true, message: t('pages:selectAnEndTime')}]}>
                        <DatePicker showTime style={{width: '100%'}}/>
                    </Form.Item>
                    <Form.Item label={t('pages:initialCapital')} name="initialCapital"
                               rules={[{required: true, message: t('pages:enterInitialCapital')}]}>
                        <InputNumber style={{width: '100%'}} min={0.0001}/>
                    </Form.Item>
                    <Form.Item label={t('pages:executionParameters')} name="executionSpec"
                               rules={[{required: true, message: t('pages:enterExecutionspec')}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Form.Item label={t('pages:evaluationParameters')} name="evaluationSpec"
                               rules={[{required: true, message: t('pages:enterEvaluationspec')}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={createBacktestMutation.isPending}>
                            {t('pages:create')}</Button>
                        <Button onClick={() => setCreateOpen(false)}>
                            {t('pages:cancel')}</Button>
                    </Space>
                </Form>
            </Drawer>
        </>
    );
}
