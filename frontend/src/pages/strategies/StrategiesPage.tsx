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
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {
    BOOLEAN_FILTER_OPTIONS,
    EXCHANGE_OPTIONS,
    STRATEGY_TYPE_OPTIONS,
    TRADE_ENV_OPTIONS,
} from '@/constants/filter-options';
import {
    useStrategyDetailQuery,
    useStrategyVersionsQuery,
    useCreateStrategyVersionMutation,
    useStrategyListQuery,
    useUpdateStrategyStatusMutation,
} from '@/features/strategies/hooks/useStrategyListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultStrategyListFilters,
    type StrategyDefinitionListItem,
    type StrategyListFilters,
    type StrategyVersionCreateRequest,
    type StrategyVersionItem,
} from '@/features/strategies/types/strategies';
import {containsIgnoreCase, formatDateTime, matchesBooleanFilter, normalizeOptionalText} from '@/utils/formatters';

type StrategyRow = StrategyDefinitionListItem;

export function StrategiesPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const [form] = useLocalizedForm<StrategyListFilters>();
    const [versionForm] = useLocalizedForm<StrategyVersionCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<StrategyListFilters>(defaultStrategyListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedStrategyCode, setSelectedStrategyCode] = useState<string | null>(null);
    const strategiesQuery = useStrategyListQuery(searchVersion);
    const strategyDetailQuery = useStrategyDetailQuery(selectedStrategyCode);
    const strategyVersionsQuery = useStrategyVersionsQuery(selectedStrategyCode);
    const updateStatusMutation = useUpdateStrategyStatusMutation();
    const createVersionMutation = useCreateStrategyVersionMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = (strategiesQuery.data ?? []).filter((item) => (
        containsIgnoreCase(item.strategyCode, submittedFilters.strategyCode)
        && containsIgnoreCase(item.strategyType, submittedFilters.strategyType)
        && containsIgnoreCase(item.exchangeCode, submittedFilters.exchangeCode)
        && containsIgnoreCase(item.tradeEnv, submittedFilters.tradeEnv)
        && matchesBooleanFilter(item.enabled, submittedFilters.enabled)
    ));

    const strategyColumns: ColumnsType<StrategyRow> = [
        {
            title: t('pages:strategyCode'),
            dataIndex: 'strategyCode',
            key: 'strategyCode',
            width: 180,
        },
        {
            title: t('pages:strategyName'),
            dataIndex: 'strategyName',
            key: 'strategyName',
            width: 220,
        },
        {
            title: t('pages:strategyId'),
            dataIndex: 'strategyId',
            key: 'strategyId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:type'),
            dataIndex: 'strategyType',
            key: 'strategyType',
            width: 140,
        },
        {
            title: t('pages:exchange'),
            dataIndex: 'exchangeCode',
            key: 'exchangeCode',
            width: 120,
        },
        {
            title: t('pages:environment'),
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 120,
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
                <Button type="link" onClick={() => setSelectedStrategyCode(record.strategyCode)}>
                    {t('pages:viewDetails')}</Button>
            ),
        },
    ];

    const versionColumns: ColumnsType<StrategyVersionItem> = [
        {
            title: t('pages:versionId'),
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:versionNumber'),
            dataIndex: 'version',
            key: 'version',
            width: 90,
        },
        {
            title: t('pages:versionName'),
            dataIndex: 'versionName',
            key: 'versionName',
            width: 180,
        },
        {
            title: t('pages:status'),
            dataIndex: 'status',
            key: 'status',
            width: 110,
            render: (value: string) => <Tag color={value === 'ACTIVE' ? 'success' : 'blue'}>{value}</Tag>,
        },
        {
            title: 'Checksum',
            dataIndex: 'checksum',
            key: 'checksum',
            width: 220,
            render: (value: string) => <Typography.Text copyable ellipsis>{value}</Typography.Text>,
        },
        {
            title: t('pages:createdAt'),
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
    ];

    const handleSearch = (values: StrategyListFilters) => {
        setSubmittedFilters({
            strategyCode: normalizeOptionalText(values.strategyCode),
            strategyType: normalizeOptionalText(values.strategyType),
            exchangeCode: normalizeOptionalText(values.exchangeCode),
            tradeEnv: normalizeOptionalText(values.tradeEnv),
            enabled: values.enabled ?? 'all',
        });
        setSearchVersion((value) => value + 1);
    };

    const handleReset = () => {
        form.resetFields();
        setSubmittedFilters(defaultStrategyListFilters);
        setSearchVersion(0);
    };

    const handleStatusUpdate = (enabled: boolean) => {
        if (!strategyDetailQuery.data) {
            return;
        }

        updateStatusMutation.mutate(
            {
                strategyCode: strategyDetailQuery.data.strategyCode,
                request: {enabled},
            },
            {
                onSuccess: () => {
                    message.success(enabled ? t('pages:strategyEnabled') : t('pages:strategyDisabled'));
                    strategyDetailQuery.refetch();
                },
                onError: (error) => {
                    showApiError(error as AppApiError, message);
                },
            },
        );
    };

    const handleCreateVersion = (values: StrategyVersionCreateRequest) => {
        if (!selectedStrategyCode) {
            return;
        }
        createVersionMutation.mutate(
            {
                strategyCode: selectedStrategyCode,
                request: {
                    versionName: values.versionName,
                    status: values.status || 'DRAFT',
                    paramSnapshotJson: values.paramSnapshotJson || '{}',
                    configSnapshotJson: values.configSnapshotJson || undefined,
                    sourceSnapshotJson: values.sourceSnapshotJson || '{}',
                },
            },
            {
                onSuccess: () => {
                    message.success(t('pages:strategyVersionCreated'));
                    versionForm.resetFields();
                    strategyVersionsQuery.refetch();
                },
                onError: (error) => {
                    showApiError(error as AppApiError, message);
                },
            },
        );
    };

    return (
        <>
            <NqPageScaffold>
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:strategyDefinitions')}
                        description={t('pages:searchInspectEnableAndDisableStrategyDefinitionsStrategyStatusAccountEnvironmentAndVersionSnapshotsR')}
                        badge="Strategies"
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
                        initialValues={defaultStrategyListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:strategyCode')} name="strategyCode">
                                    <Input placeholder={t('pages:forExampleAlphaGridBtc')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:strategyType')} name="strategyType">
                                    <Select allowClear showSearch placeholder={t('pages:allTypes')} options={STRATEGY_TYPE_OPTIONS}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:exchange')} name="exchangeCode">
                                    <Select allowClear placeholder={t('pages:allExchanges')} options={EXCHANGE_OPTIONS}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:tradingEnvironment')} name="tradeEnv">
                                    <Select allowClear placeholder={t('pages:allEnvironments')} options={TRADE_ENV_OPTIONS}/>
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
                        <Empty description={t('pages:searchToLoadStrategies')}/>
                    ) : strategiesQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryStrategies')}
                            description={formatApiError(strategiesQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    {t('pages:retry')}</Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="strategyId"
                            columns={strategyColumns}
                            dataSource={visibleItems}
                            loading={strategiesQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1520}}
                            locale={{
                                emptyText: t('pages:noStrategiesMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </NqPageScaffold>
            <Drawer
                open={Boolean(selectedStrategyCode)}
                width={680}
                title={t('pages:strategyDetails')}
                onClose={() => setSelectedStrategyCode(null)}
                destroyOnClose
            >
                {strategyDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingStrategyDetails')}/>
                ) : strategyDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadStrategyDetails')}
                        description={formatApiError(strategyDetailQuery.error as AppApiError)}
                    />
                ) : strategyDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item
                                label={t('pages:strategyCode')}>{strategyDetailQuery.data.strategyCode}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:strategyName')}>{strategyDetailQuery.data.strategyName}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyId')}>{strategyDetailQuery.data.strategyId}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:strategyType')}>{strategyDetailQuery.data.strategyType}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:exchange')}>{strategyDetailQuery.data.exchangeCode}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:account')}>{strategyDetailQuery.data.accountId ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:tradingEnvironment')}>{strategyDetailQuery.data.tradeEnv}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:status')}>
                                <Tag color="blue">{strategyDetailQuery.data.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:enabledStatus')}>
                                <Tag color={strategyDetailQuery.data.enabled ? 'success' : 'default'}>
                                    {strategyDetailQuery.data.enabled ? t('pages:enabled') : t('pages:disabled')}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:version')}>{strategyDetailQuery.data.version}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:createdAt')}>{formatDateTime(strategyDetailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item
                                label={t('pages:updatedAt')}>{formatDateTime(strategyDetailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:configurationSnapshot')} span={2}>
                                <Typography.Paragraph style={{marginBottom: 0}}>
                                    {strategyDetailQuery.data.configSnapshot || '-'}
                                </Typography.Paragraph>
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title={t('pages:actions2')} size="small">
                            <Space wrap>
                                <Button
                                    type="primary"
                                    disabled={strategyDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(true)}
                                >
                                    {t('pages:enableStrategy')}</Button>
                                <Button
                                    danger
                                    disabled={!strategyDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(false)}
                                >
                                    {t('pages:disableStrategy')}</Button>
                                <Button onClick={() => strategyDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}</Button>
                            </Space>
                        </Card>
                        <Card
                            title={t('pages:strategyVersions')}
                            size="small"
                            extra={<Button onClick={() => strategyVersionsQuery.refetch()}>{t('pages:refreshVersions')}</Button>}
                        >
                            {strategyVersionsQuery.error ? (
                                <Alert
                                    type="error"
                                    showIcon
                                    message={t('pages:failedToQueryStrategyVersions')}
                                    description={formatApiError(strategyVersionsQuery.error as AppApiError)}
                                />
                            ) : (
                                <Table
                                    rowKey="strategyVersionId"
                                    size="small"
                                    columns={versionColumns}
                                    dataSource={strategyVersionsQuery.data ?? []}
                                    loading={strategyVersionsQuery.isFetching}
                                    pagination={{pageSize: 5, showSizeChanger: false}}
                                    scroll={{x: 1000}}
                                    locale={{emptyText: t('pages:thisStrategyHasNoVersions')}}
                                />
                            )}
                        </Card>
                        <Card title={t('pages:createStrategyVersion')} size="small">
                            <Form
                                form={versionForm}
                                layout="vertical"
                                initialValues={{
                                    status: 'DRAFT',
                                    paramSnapshotJson: '{}',
                                    sourceSnapshotJson: '{}',
                                }}
                                onFinish={handleCreateVersion}
                            >
                                <Row gutter={[16, 0]}>
                                    <Col xs={24} md={12}>
                                        <Form.Item
                                            label={t('pages:versionName')}
                                            name="versionName"
                                            rules={[{required: true, message: t('pages:enterAVersionName')}]}
                                        >
                                            <Input placeholder={t('pages:forExampleFreezeBaseline')}/>
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24} md={12}>
                                        <Form.Item label={t('pages:versionStatus')} name="status">
                                            <Select
                                                options={[
                                                    {label: 'DRAFT', value: 'DRAFT'},
                                                    {label: 'ACTIVE', value: 'ACTIVE'},
                                                    {label: 'ARCHIVED', value: 'ARCHIVED'},
                                                ]}
                                            />
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24}>
                                        <Form.Item label={t('pages:parameterSnapshotJson')} name="paramSnapshotJson">
                                            <Input.TextArea rows={3} placeholder='{"threshold":1}'/>
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24}>
                                        <Form.Item label={t('pages:configurationSnapshotJson')} name="configSnapshotJson">
                                            <Input.TextArea rows={3} placeholder={t('pages:leaveEmptyToUseTheCurrentStrategyConfigurationSnapshot')}/>
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24}>
                                        <Form.Item label={t('pages:sourceSnapshotJson')} name="sourceSnapshotJson">
                                            <Input.TextArea rows={3} placeholder='{"source":"manual"}'/>
                                        </Form.Item>
                                    </Col>
                                </Row>
                                <Space>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={createVersionMutation.isPending}
                                    >
                                        {t('pages:createVersion')}</Button>
                                    <Button onClick={() => versionForm.resetFields()}>
                                        {t('pages:clear')}</Button>
                                </Space>
                            </Form>
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
        </>
    );
}
