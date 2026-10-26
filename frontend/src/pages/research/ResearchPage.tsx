import {useDatasetDetailQuery} from '@/features/marketdata/hooks/useDatasetQueries';
import {validDatasetOperationId} from '@/features/marketdata/dataset-operations';
import {canWriteResearch} from '@/utils/distribution';
import {ObjectConfigurationSummary} from '@/components/configuration/ObjectConfigurationSummary';
import {ObjectConfigurationInput} from '@/components/configuration/ObjectConfigurationInput';
import {configurationObject} from '@/components/configuration/object-configuration';
import {configurationSchema} from '@/components/configuration/object-configuration';
import {businessColumns} from '@/components/layout/business-columns';
import {useAuthStore} from '@/store/auth-store';
import {CreationRecoveryPanel} from '@/features/research/components/CreationRecoveryPanel';
import {MarketdataDatasetPicker} from '@/features/marketdata/components/MarketdataDatasetPicker';
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
    Space,
    Table,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useRef, useState} from 'react';
import {StrategyDefinitionPicker} from '@/features/strategies/components/StrategyDefinitionPicker';
import {Link, useSearchParams} from 'react-router-dom';

import {formatApiError, showApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {
    useCreateResearchMutation,
    useArchiveResearchMutation,
    useResearchDetailQuery,
    useResearchListQuery,
} from '@/features/research/hooks/useResearchListQuery';
import type {AppApiError} from '@/types/api';
import type {ResearchConfigCreateRequest} from '@/features/research/types/research';
import {
    defaultResearchListFilters,
    type ResearchConfigListItem,
    type ResearchListFilters,
} from '@/features/research/types/research';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';
import {ConfigurationArchiveAction} from '@/features/research/components/ConfigurationArchiveAction';

type ResearchRow = ResearchConfigListItem;

export function ResearchPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const canWrite = useAuthStore(
        (state) =>
            canWriteResearch(state.currentUser?.roles),
    );
    const [params, setParams] = useSearchParams();
    // 列表选择与创建结果都保留正式对象深链；地址变化时不复用旧的局部详情身份。
    const selectedConfigId = params.get('researchConfigId');
    const selectConfig = (researchConfigId: string | null) => {
        const next = new URLSearchParams(params);
        if (researchConfigId) next.set('researchConfigId', researchConfigId);
        else next.delete('researchConfigId');
        next.delete('create');
        setParams(next);
    };
    const [queryForm] = useLocalizedForm<ResearchListFilters>();
    const [createForm] = useLocalizedForm<ResearchConfigCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<ResearchListFilters>(
        defaultResearchListFilters,
    );
    const [searchVersion, setSearchVersion] = useState(1);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [createOpen, setCreateOpen] = useState(params.get('create') === 'true');
    const [createAttempted, setCreateAttempted] = useState(false);
    const createDatasetSpec = Form.useWatch('datasetSpec', createForm);
    const researchQuery = useResearchListQuery(
        {...submittedFilters, limit: pageSize, offset: (currentPage - 1) * pageSize},
        searchVersion,
    );
    const researchDetailQuery = useResearchDetailQuery(selectedConfigId);
    const createResearchMutation = useCreateResearchMutation();
    const archiveResearchMutation = useArchiveResearchMutation();
    const createRequestInFlight = useRef(false);
    const linkedDatasetId = params.get('datasetId');
    const [createDatasetId, setCreateDatasetId] = useState<string | undefined>(() => linkedDatasetId && validDatasetOperationId(linkedDatasetId) ? linkedDatasetId : undefined);
    const linkedDataset = useDatasetDetailQuery(linkedDatasetId && validDatasetOperationId(linkedDatasetId) ? linkedDatasetId : undefined, createOpen);
    const appliedDataset = useRef<string | null>(null);
    useEffect(() => {
        const dataset = linkedDataset.data;
        if (!dataset || appliedDataset.current === dataset.datasetId) return;
        appliedDataset.current = dataset.datasetId;
        createForm.setFieldValue('datasetSpec', JSON.stringify({datasetId: dataset.datasetId, exchangeCode: dataset.exchangeCode,
            marketType: dataset.marketType, symbol: dataset.symbol, interval: dataset.interval}));
    }, [linkedDataset.data, createForm]);
    const hasSearched = searchVersion > 0;

    const visibleItems = researchQuery.data?.items ?? [];

    const researchColumns: ColumnsType<ResearchRow> = [
        {
            title: t('pages:researchConfigurationId'),
            dataIndex: 'researchConfigId',
            key: 'researchConfigId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:sourceStrategyId'),
            dataIndex: 'sourceStrategyId',
            key: 'sourceStrategyId',
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
            width: 260,
            render: (value: string) => value || '-',
        },
        {
            title: t('pages:createdAt'),
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
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
                <Button type="link" onClick={() => selectConfig(record.researchConfigId)}>
                    {t('pages:viewDetails')}
                </Button>
            ),
        },
    ];

    const handleSearch = (values: ResearchListFilters) => {
        setCurrentPage(1);
        setSubmittedFilters({
            sourceStrategyId: normalizeOptionalText(values.sourceStrategyId),
            researchConfigId: normalizeOptionalText(values.researchConfigId),
            name: normalizeOptionalText(values.name),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        queryForm.resetFields();
        setSubmittedFilters(defaultResearchListFilters);
        setSearchVersion(1);
        setCurrentPage(1);
    };

    const handleCreate = (values: ResearchConfigCreateRequest) => {
        // loading要等React更新后才拦截点击；同步占用防止同一帧双击创建两个正式对象。
        if (createRequestInFlight.current) return;
        createRequestInFlight.current = true;
        createResearchMutation.mutate(
            {
                ...values,
                sourceStrategyId: normalizeOptionalText(values.sourceStrategyId),
                name: normalizeOptionalText(values.name),
                description: normalizeOptionalText(values.description),
                parameterSchema: normalizeOptionalText(values.parameterSchema),
                parameterDefaults: normalizeOptionalText(values.parameterDefaults),
                datasetSpec: normalizeOptionalText(values.datasetSpec),
            },
            {
                onSuccess: (created) => {
                    message.success(t('pages:researchConfigurationCreated'));
                    setCreateOpen(false);
                    createForm.resetFields();
                    setCreateDatasetId(undefined);
                    setCreateAttempted(false);
                    selectConfig(created.researchConfigId);
                },
                onError: (error) => {
                    showApiError(error as AppApiError, message);
                },
                onSettled: () => {
                    createRequestInFlight.current = false;
                },
            },
        );
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                {!canWrite && (
                    <Alert type="info" showIcon message={t('pages:researchReadOnlyRole')} />
                )}
                <CreationRecoveryPanel kinds={['RESEARCH']} />
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:researchConfigurations')}
                        description={t(
                            'pages:viewResearchConfigurationsLinkedSourceStrategiesAndParameterDefinitionsOrCreateAConfiguration',
                        )}
                        badge="Research"
                        extra={
                            <Button
                                type="primary"
                                disabled={!canWrite}
                                onClick={() => {
                                    setCreateAttempted(false);
                                    setCreateOpen(true);
                                }}
                            >
                                {t('pages:createResearchConfiguration')}
                            </Button>
                        }
                    />
                </Card>
                <Card
                    className="page-section workspace-filter-panel"
                    bordered={false}
                    title={t('pages:filters')}
                    extra={
                        <Space>
                            <Button type="primary" onClick={() => queryForm.submit()}>
                                {t('pages:search')}
                            </Button>
                            <Button onClick={handleReset}>{t('pages:reset')}</Button>
                        </Space>
                    }
                >
                    <Form
                        name="research-queryForm"
                        form={queryForm}
                        layout="vertical"
                        initialValues={defaultResearchListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item
                                    label={t('pages:sourceStrategyId')}
                                    name="sourceStrategyId"
                                >
                                    <Input placeholder={t('pages:filterBySourceStrategyId')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item
                                    label={t('pages:researchConfigurationId')}
                                    name="researchConfigId"
                                >
                                    <Input
                                        placeholder={t('pages:filterByResearchConfigurationId')}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:name')} name="name">
                                    <Input placeholder={t('pages:filterByConfigurationName')} />
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
                                {researchQuery.data?.total ?? 0} {t('pages:records')}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {!hasSearched ? (
                        <Empty description={t('product:loaded')} />
                    ) : researchQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryResearchConfigurations')}
                            description={formatApiError(researchQuery.error as AppApiError)}
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
                            className="research-config-list-table"
                            rowKey="researchConfigId"
                            columns={businessColumns(researchColumns)}
                            dataSource={visibleItems}
                            loading={researchQuery.isFetching}
                            pagination={{
                                current: currentPage,
                                pageSize,
                                total: researchQuery.data?.total ?? 0,
                                showSizeChanger: true,
                                responsive: true,
                                showLessItems: true,
                                pageSizeOptions: [10, 20, 50, 100],
                                onChange: (page, size) => {
                                    setCurrentPage(size === pageSize ? page : 1);
                                    setPageSize(size);
                                },
                            }}
                            scroll={{x: 1520}}
                            locale={{
                                emptyText: t('pages:noResearchConfigurationsMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedConfigId)}
                width={760}
                title={t('pages:researchConfigurationDetails')}
                onClose={() => selectConfig(null)}
                destroyOnClose
            >
                {researchDetailQuery.isLoading ? (
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:loadingResearchConfigurationDetails')}
                    />
                ) : researchDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadResearchConfigurationDetails')}
                        description={formatApiError(researchDetailQuery.error as AppApiError)}
                    />
                ) : researchDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        {researchDetailQuery.data.status !== 'ARCHIVED' && (
                            <Link
                                to={`/backtests?researchConfigId=${encodeURIComponent(researchDetailQuery.data.researchConfigId)}&create=true`}
                            >
                                {t('pages:workflowCreateBacktest')}
                            </Link>
                        )}
                        <Descriptions
                            className="research-object-details"
                            bordered
                            column={{xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2}}
                            size="small"
                        >
                            <Descriptions.Item label={t('pages:researchConfigurationId')}>
                                {researchDetailQuery.data.researchConfigId}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:sourceStrategyId')}>
                                {researchDetailQuery.data.sourceStrategyId}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:name')}>
                                {researchDetailQuery.data.name}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:description')}>
                                {researchDetailQuery.data.description || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:createdAt')}>
                                {formatDateTime(researchDetailQuery.data.createdAt)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:updatedAt')}>
                                {formatDateTime(researchDetailQuery.data.updatedAt)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:parameterSchema')} span={2}>
                                <ObjectConfigurationSummary value={researchDetailQuery.data.parameterSchema} />
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:parameterDefaults')} span={2}>
                                <ObjectConfigurationSummary value={researchDetailQuery.data.parameterDefaults} />
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:datasetSpecification')} span={2}>
                                <ObjectConfigurationSummary value={researchDetailQuery.data.datasetSpec} />
                            </Descriptions.Item>
                        </Descriptions>
                        {/* 研究来源按创建时事实展示，不能用当前定义回填历史快照。 */}
                        <Collapse
                            items={[
                                {
                                    key: 'source-strategy-snapshot',
                                    label: t('pages:researchSourceSnapshot'),
                                    children: (
                                        <Typography.Paragraph
                                            copyable={Boolean(
                                                researchDetailQuery.data.strategySnapshot,
                                            )}
                                            style={{
                                                marginBottom: 0,
                                                whiteSpace: 'pre-wrap',
                                                overflowWrap: 'anywhere',
                                            }}
                                        >
                                            {researchDetailQuery.data.strategySnapshot || '—'}
                                        </Typography.Paragraph>
                                    ),
                                },
                            ]}
                        />
                        <Card title={t('pages:actions2')} size="small">
                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                <Alert
                                    type="info"
                                    showIcon
                                    message={t('pages:configurationArchiveMeaning')}
                                />
                                <Button onClick={() => researchDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}
                                </Button>
                                <ConfigurationArchiveAction
                                    key={researchDetailQuery.data.researchConfigId}
                                    label={t('pages:archiveResearchConfig')}
                                    configurationId={researchDetailQuery.data.researchConfigId}
                                    configuration={researchDetailQuery.data}
                                    pending={archiveResearchMutation.isPending}
                                    onArchive={(reason) =>
                                        archiveResearchMutation
                                            .mutateAsync({
                                                configId:
                                                    researchDetailQuery.data!.researchConfigId,
                                                reason,
                                            })
                                            .then(() => setCurrentPage(1))
                                    }
                                />
                            </Space>
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
            <Drawer
                open={createOpen}
                width={720}
                title={t('pages:createResearchConfiguration')}
                onClose={() => setCreateOpen(false)}
                destroyOnClose
            >
                <Form
                    disabled={!canWrite}
                    name="research-createForm"
                    form={createForm}
                    layout="vertical"
                    onFinish={handleCreate}
                    onFinishFailed={() => setCreateAttempted(true)}
                    initialValues={{
                        sourceStrategyId: params.get('sourceStrategyId') ?? '',
                        parameterSchema: '{}',
                        parameterDefaults: '{}',
                    }}
                >
                    {linkedDatasetId && <Alert showIcon style={{marginBottom: 16}}
                        type={linkedDataset.isError || !validDatasetOperationId(linkedDatasetId) ? 'error' : 'info'}
                        message={t('pages:researchDataset.createResearch')}
                        description={linkedDataset.isError ? formatApiError(linkedDataset.error as AppApiError) : t('pages:researchDataset.freezeHelp')}
                    />}
                    <Form.Item
                        label={t('pages:sourceStrategySelection')}
                        name="sourceStrategyId"
                        rules={[{required: true, message: t('pages:strategyPickerPlaceholder')}]}
                    >
                        <StrategyDefinitionPicker active={createOpen} />
                    </Form.Item>
                    <Form.Item
                        label={t('pages:name')}
                        name="name"
                        rules={[{required: true, message: t('pages:enterAName')}]}
                    >
                        <Input />
                    </Form.Item>
                    <Form.Item label={t('pages:description')} name="description">
                        <Input.TextArea rows={3} />
                    </Form.Item>
                    {/* 必填错误必须留在可见选择器旁，不能只显示在折叠的高级JSON字段中。 */}
                    <Form.Item
                        label={t('pages:dataset')}
                        required
                        validateStatus={createAttempted && !createDatasetSpec ? 'error' : undefined}
                        help={
                            createAttempted && !createDatasetSpec
                                ? t('pages:selectADataset')
                                : undefined
                        }
                    >
                        <MarketdataDatasetPicker
                            aria-label={t('pages:selectADataset')}
                            active={createOpen}
                            value={createDatasetId}
                            onChange={(id) => {
                                setCreateDatasetId(id);
                                if (!id) createForm.setFieldValue('datasetSpec', undefined);
                            }}
                            onSelect={(dataset) =>
                                createForm.setFieldValue(
                                    'datasetSpec',
                                    JSON.stringify({
                                        datasetId: dataset.datasetId,
                                        exchangeCode: dataset.exchangeCode,
                                        marketType: dataset.marketType,
                                        symbol: dataset.symbol,
                                        interval: dataset.interval,
                                    }),
                                )
                            }
                        />
                    </Form.Item>
                    <Alert type="info" showIcon message={t('product:configResearchHelp')} style={{marginBottom: 16}} />
                    <Form.Item name="parameterDefaults" label={t('product:configResearchDefaults')} rules={[{validator: async (_, value?: string) => {try {configurationObject(value);} catch {throw new Error(t('product:configIncomplete'));}}}]}>
                        <ObjectConfigurationInput label={t('product:configResearchDefaults')} disabled={!canWrite} />
                    </Form.Item>
                    <Button style={{marginBottom: 16}} disabled={!canWrite} onClick={() => {
                        try {createForm.setFieldValue('parameterSchema', configurationSchema(createForm.getFieldValue('parameterDefaults')));}
                        catch {message.error(t('product:configIncomplete'));}
                    }}>{t('product:configGenerateSchema')}</Button>
                    <Collapse
                        items={[
                            {
                                key: 'advanced',
                                forceRender: true,
                                label: t('pages:researchAdvanced'),
                                children: (
                                    <>
                                        <Form.Item
                                            label={t('pages:parameterSchema')}
                                            name="parameterSchema"
                                            rules={[{validator: async (_, value?: string) => {try {configurationObject(value);} catch {throw new Error(t('product:configIncomplete'));}}},
                                                {
                                                    required: true,
                                                    message: t('pages:enterParameterschema'),
                                                },
                                            ]}
                                        >
                                            <ObjectConfigurationInput label={t('pages:parameterSchema')} disabled={!canWrite} />
                                        </Form.Item>
                                        <Form.Item
                                            label={t('pages:datasetSpecification')}
                                            name="datasetSpec"
                                            rules={[{validator: async (_, value?: string) => {try {configurationObject(value);} catch {throw new Error(t('product:configIncomplete'));}}},
                                                {
                                                    required: true,
                                                    message: t('pages:enterDatasetspec'),
                                                },
                                            ]}
                                        >
                                            <ObjectConfigurationInput label={t('pages:datasetSpecification')} disabled={!canWrite} />
                                        </Form.Item>
                                    </>
                                ),
                            },
                        ]}
                    />
                    <Space>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={createResearchMutation.isPending}
                        >
                            {t('pages:create')}
                        </Button>
                        <Button onClick={() => setCreateOpen(false)}>{t('pages:cancel')}</Button>
                    </Space>
                </Form>
            </Drawer>
        </>
    );
}
