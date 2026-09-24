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
    Space,
    Table,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {formatApiError, showApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
import {
    useCreateResearchMutation,
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
import {containsIgnoreCase, formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type ResearchRow = ResearchConfigListItem;

export function ResearchPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const [queryForm] = useLocalizedForm<ResearchListFilters>();
    const [createForm] = useLocalizedForm<ResearchConfigCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<ResearchListFilters>(defaultResearchListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedConfigId, setSelectedConfigId] = useState<string | null>(null);
    const [createOpen, setCreateOpen] = useState(false);
    const researchQuery = useResearchListQuery(submittedFilters.sourceStrategyId, searchVersion);
    const researchDetailQuery = useResearchDetailQuery(selectedConfigId);
    const createResearchMutation = useCreateResearchMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = (researchQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.researchConfigId, submittedFilters.researchConfigId)
        && containsIgnoreCase(item.name, submittedFilters.name)
    ));

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
                <Button type="link" onClick={() => setSelectedConfigId(record.researchConfigId)}>
                    {t('pages:viewDetails')}</Button>
            ),
        },
    ];

    const handleSearch = (values: ResearchListFilters) => {
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
        setSearchVersion(0);
    };

    const handleCreate = (values: ResearchConfigCreateRequest) => {
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
                onSuccess: () => {
                    message.success(t('pages:researchConfigurationCreated'));
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

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <PageHero
                        title={t('pages:researchConfigurations')}
                        description={t('pages:viewResearchConfigurationsLinkedSourceStrategiesAndParameterDefinitionsOrCreateAConfiguration')}
                        badge="Research"
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
                        initialValues={defaultResearchListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:sourceStrategyId')} name="sourceStrategyId">
                                    <Input placeholder={t('pages:filterBySourceStrategyId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:researchConfigurationId')} name="researchConfigId">
                                    <Input placeholder={t('pages:filterByResearchConfigurationId')}/>
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
                            {t('pages:createResearchConfiguration')}</Button>
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
                        <Empty description={t('pages:searchToLoadResearchConfigurations')}/>
                    ) : researchQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryResearchConfigurations')}
                            description={formatApiError(researchQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    {t('pages:retry')}</Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="researchConfigId"
                            columns={researchColumns}
                            dataSource={visibleItems}
                            loading={researchQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
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
                onClose={() => setSelectedConfigId(null)}
                destroyOnClose
            >
                {researchDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingResearchConfigurationDetails')}/>
                ) : researchDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadResearchConfigurationDetails')}
                        description={formatApiError(researchDetailQuery.error as AppApiError)}
                    />
                ) : researchDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item
                                label={t('pages:researchConfigurationId')}>{researchDetailQuery.data.researchConfigId}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:sourceStrategyId')}>{researchDetailQuery.data.sourceStrategyId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:name')}>{researchDetailQuery.data.name}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:description')}>{researchDetailQuery.data.description || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:createdAt')}>{formatDateTime(researchDetailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:updatedAt')}>{formatDateTime(researchDetailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:parameterSchema')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {researchDetailQuery.data.parameterSchema || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:parameterDefaults')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {researchDetailQuery.data.parameterDefaults || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:datasetSpecification')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {researchDetailQuery.data.datasetSpec || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title={t('pages:actions2')} size="small">
                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                <Alert type="info" showIcon
                                       message={t('pages:researchConfigurationDetailsAreReadOnlyUseTheActionsAreaToCreateAConfiguration')}/>
                                <Button onClick={() => researchDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}</Button>
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
                <Form form={createForm} layout="vertical" onFinish={handleCreate}>
                    <Form.Item label={t('pages:sourceStrategyId')} name="sourceStrategyId"
                               rules={[{required: true, message: t('pages:enterSourcestrategyid')}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label={t('pages:name')} name="name" rules={[{required: true, message: t('pages:enterAName')}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label={t('pages:description')} name="description">
                        <Input.TextArea rows={3}/>
                    </Form.Item>
                    <Form.Item label={t('pages:parameterSchema')} name="parameterSchema"
                               rules={[{required: true, message: t('pages:enterParameterschema')}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Form.Item label={t('pages:parameterDefaults')} name="parameterDefaults"
                               rules={[{required: true, message: t('pages:enterParameterdefaults')}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Form.Item label={t('pages:datasetSpecification')} name="datasetSpec"
                               rules={[{required: true, message: t('pages:enterDatasetspec')}]}>
                        <Input.TextArea rows={4}/>
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={createResearchMutation.isPending}>
                            {t('pages:create')}</Button>
                        <Button onClick={() => setCreateOpen(false)}>
                            {t('pages:cancel')}</Button>
                    </Space>
                </Form>
            </Drawer>
        </>
    );
}
