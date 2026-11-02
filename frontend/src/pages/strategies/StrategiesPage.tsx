import {canWriteResearch, isPublicDemo} from '@/utils/distribution';
import {ObjectConfigurationSummary} from '@/components/configuration/ObjectConfigurationSummary';
import {ObjectConfigurationInput} from '@/components/configuration/ObjectConfigurationInput';
import {configurationObject} from '@/components/configuration/object-configuration';
import {businessColumns} from '@/components/layout/business-columns';
import {useAuthStore} from '@/store/auth-store';
import {StrategyExecutionRelations} from '@/features/strategies/components/StrategyRelations';
import {CreationRecoveryPanel} from '@/features/research/components/CreationRecoveryPanel';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    App,
    Button,
    Card,
    Col,
    Steps,
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
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Link, useSearchParams} from 'react-router-dom';
import {strategiesApi} from '@/features/strategies/api/strategies';
import {strategyQueryKeys} from '@/api/query-keys';
import {SpotTargetVersionForm} from '@/features/strategies/components/SpotTargetVersionForm';
import {StrategyVersionList} from '@/features/strategies/components/StrategyVersionList';
import {useAccountContextStore} from '@/store/account-context-store';

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
    useStrategyVersionDetailQuery,
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
    type StrategyVersionListItem,
    type StrategyDefinitionCreateRequest,
} from '@/features/strategies/types/strategies';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type StrategyRow = StrategyDefinitionListItem;

export function StrategiesPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const canWrite = useAuthStore(
        (state) =>
            canWriteResearch(state.currentUser?.roles),
    );
    const [form] = useLocalizedForm<StrategyListFilters>();
    const [versionForm] = useLocalizedForm<StrategyVersionCreateRequest>();
    const [createForm] = useLocalizedForm<StrategyDefinitionCreateRequest>();
    const [createOpen, setCreateOpen] = useState(false);
    const createStrategyType = Form.useWatch('strategyType', createForm);
    const [params, setParams] = useSearchParams();
    // 详情身份由地址唯一拥有；刷新和前进/后退应恢复同一对象，关闭时清除旧版本定位。
    const selectedStrategyCode = params.get('strategyCode');
    const selectStrategy = (strategyCode: string | null) => {
        const next = new URLSearchParams(params);
        if (strategyCode) next.set('strategyCode', strategyCode);
        else next.delete('strategyCode');
        next.delete('versionId');
        setParams(next);
    };
    const context = useAccountContextStore();
    const cache = useQueryClient();
    const createRequestInFlight = useRef(false);
    const versionRequestInFlight = useRef(false);
    const canCreate =
        canWrite &&
        context.tradeEnv === 'SIM' &&
        Boolean(context.legacyAccountId) &&
        Boolean(context.exchangeCode);
    const createMutation = useMutation({
        mutationFn: (values: StrategyDefinitionCreateRequest) => {
            if (!canCreate) throw new Error(t('pages:workflowAccountRequired'));
            return strategiesApi.create({
                ...values,
                configSnapshot: configurationObject(values.configSnapshot),
                accountId: context.legacyAccountId!,
                exchangeCode: context.exchangeCode!,
                tradeEnv: 'SIM',
            });
        },
        onSuccess: async (created) => {
            await cache.invalidateQueries({queryKey: strategyQueryKeys.all});
            selectStrategy(created.strategyCode);
            setCreateOpen(false);
            createForm.resetFields();
            message.success(t('pages:workflowStrategyCreated'));
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });
    const [submittedFilters, setSubmittedFilters] = useState<StrategyListFilters>(
        defaultStrategyListFilters,
    );
    const [searchVersion, setSearchVersion] = useState(1);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const strategiesQuery = useStrategyListQuery(
        {...submittedFilters, limit: pageSize, offset: (currentPage - 1) * pageSize},
        searchVersion,
    );
    const strategyDetailQuery = useStrategyDetailQuery(selectedStrategyCode);
    const [versionListRevision, setVersionListRevision] = useState(0);
    const locatedVersionId = params.get('versionId');
    const locatedVersion = useStrategyVersionDetailQuery(selectedStrategyCode, locatedVersionId);
    const updateStatusMutation = useUpdateStrategyStatusMutation();
    const createVersionMutation = useCreateStrategyVersionMutation();
    const hasSearched = searchVersion > 0;

    const visibleItems = strategiesQuery.data?.items ?? [];

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
            render: (value: string) => STRATEGY_TYPE_OPTIONS.find(option => option.value === value)?.label ?? value,
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
            render: (value: boolean) => (
                <Tag color={value ? 'success' : 'default'}>
                    {value ? t('pages:yes') : t('pages:no')}
                </Tag>
            ),
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
                <Button type="link" onClick={() => selectStrategy(record.strategyCode)}>
                    {t('pages:viewDetails')}
                </Button>
            ),
        },
    ];

    const versionColumns: ColumnsType<StrategyVersionListItem> = [
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
            render: (value: string) => (
                <Tag color={value === 'ACTIVE' ? 'success' : 'blue'}>{value}</Tag>
            ),
        },
        {
            title: t('pages:checksum'),
            dataIndex: 'checksum',
            key: 'checksum',
            width: 220,
            render: (value: string) => (
                <Typography.Text copyable ellipsis>
                    {value}
                </Typography.Text>
            ),
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
        setCurrentPage(1);
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
        setSearchVersion(1);
        setCurrentPage(1);
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
                    message.success(
                        enabled ? t('pages:strategyEnabled') : t('pages:strategyDisabled'),
                    );
                    strategyDetailQuery.refetch();
                },
                onError: (error) => {
                    showApiError(error as AppApiError, message);
                },
            },
        );
    };

    const handleCreateVersion = async (values: StrategyVersionCreateRequest): Promise<boolean> => {
        // 版本创建不幂等；不能让同一帧的重复提交先于异步loading进入服务端。
        if (!selectedStrategyCode || versionRequestInFlight.current) {
            return false;
        }
        versionRequestInFlight.current = true;
        try {
            await createVersionMutation.mutateAsync({
                strategyCode: selectedStrategyCode,
                request: {
                    versionName: values.versionName,
                    status: values.status || 'DRAFT',
                    paramSnapshotJson: values.paramSnapshotJson || '{}',
                    configSnapshotJson: values.configSnapshotJson || undefined,
                    sourceSnapshotJson: values.sourceSnapshotJson || '{}',
                },
            });
            message.success(t('pages:strategyVersionCreated'));
            versionForm.resetFields();
            // 新版本按倒序出现在第一页；重建目录状态，避免旧筛选将刚创建的版本挡住。
            setVersionListRevision((value) => value + 1);
            return true;
        } catch (error) {
            showApiError(error as AppApiError, message);
            return false;
        } finally {
            versionRequestInFlight.current = false;
        }
    };

    return (
        <>
            <NqPageScaffold>
                {!canWrite && (
                    <Alert type="info" showIcon message={t('pages:researchReadOnlyRole')} />
                )}
                <CreationRecoveryPanel kinds={['STRATEGY', 'VERSION']} />
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:strategyDefinitions')}
                        description={t(
                            'pages:searchInspectEnableAndDisableStrategyDefinitionsStrategyStatusAccountEnvironmentAndVersionSnapshotsR',
                        )}
                        badge="Strategies"
                        extra={
                        <Button
                            disabled={!canWrite}
                            type="primary"
                            onClick={() => setCreateOpen(true)}
                        >
                            {t('pages:workflowCreateStrategy')}
                        </Button>
                        }
                    />
                    <Typography.Text type="secondary">{t('product:loaded')}</Typography.Text>
                </Card>
                <Card
                    className="page-section workspace-filter-panel"
                    bordered={false}
                    title={t('pages:filters')}
                    extra={
                        <Space>
                            <Button type="primary" onClick={() => form.submit()}>
                                {t('pages:search')}
                            </Button>
                            <Button onClick={handleReset}>{t('pages:reset')}</Button>
                        </Space>
                    }
                >
                    <Form
                        name="strategy-form"
                        form={form}
                        layout="vertical"
                        initialValues={defaultStrategyListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:strategyCode')} name="strategyCode">
                                    <Input placeholder={t('pages:forExampleAlphaGridBtc')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                {/* 空字符串仍表示无筛选，传给Select时映射为空值以显示完整提示。 */}
                                <Form.Item
                                    label={t('pages:strategyType')}
                                    tooltip={t('product:strategyTypeHelp')}
                                    name="strategyType"
                                    getValueProps={(value?: string) => ({
                                        value: value || undefined,
                                    })}
                                >
                                    <Select
                                        allowClear
                                        showSearch
                                        placeholder={t('pages:allTypes')}
                                        options={STRATEGY_TYPE_OPTIONS.filter(({value}) => !isPublicDemo || value !== 'SPOT_CHANNEL_BREAKOUT_V1').map(({value, label}) => ({value, label}))}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:exchange')}
                                    name="exchangeCode"
                                    getValueProps={(value?: string) => ({
                                        value: value || undefined,
                                    })}
                                >
                                    <Select
                                        allowClear
                                        placeholder={t('pages:allExchanges')}
                                        options={EXCHANGE_OPTIONS}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:tradingEnvironment')}
                                    name="tradeEnv"
                                    getValueProps={(value?: string) => ({
                                        value: value || undefined,
                                    })}
                                >
                                    <Select
                                        allowClear
                                        placeholder={t('pages:allEnvironments')}
                                        options={TRADE_ENV_OPTIONS}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:enabledStatus')} name="enabled">
                                    <Select options={BOOLEAN_FILTER_OPTIONS} />
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
                                {strategiesQuery.data?.total ?? 0} {t('pages:records')}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {!hasSearched ? (
                        <Empty description={t('pages:searchToLoadStrategies')} />
                    ) : strategiesQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryStrategies')}
                            description={formatApiError(strategiesQuery.error as AppApiError)}
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
                            className="strategy-definition-list-table"
                            rowKey="strategyId"
                            columns={businessColumns(strategyColumns)}
                            dataSource={visibleItems}
                            loading={strategiesQuery.isFetching}
                            pagination={{
                                current: currentPage,
                                pageSize,
                                total: strategiesQuery.data?.total ?? 0,
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
                onClose={() => selectStrategy(null)}
                destroyOnClose
            >
                {strategyDetailQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingStrategyDetails')} />
                ) : strategyDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadStrategyDetails')}
                        description={formatApiError(strategyDetailQuery.error as AppApiError)}
                    />
                ) : strategyDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Link
                            to={`/research?sourceStrategyId=${encodeURIComponent(strategyDetailQuery.data.strategyId)}&create=true`}
                        >
                            {t('pages:workflowCreateResearch')}
                        </Link>
                        {/* 窄屏每行展示一个完整字段，避免对象身份和状态被双列布局挤出抽屉。 */}
                        <Descriptions
                            className="research-object-details"
                            bordered
                            column={{xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2}}
                            size="small"
                        >
                            <Descriptions.Item label={t('pages:strategyCode')}>
                                {strategyDetailQuery.data.strategyCode}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyName')}>
                                {strategyDetailQuery.data.strategyName}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyId')}>
                                {strategyDetailQuery.data.strategyId}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyType')}>
                                {strategyDetailQuery.data.strategyType}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:exchange')}>
                                {strategyDetailQuery.data.exchangeCode}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:account')}>
                                {strategyDetailQuery.data.accountId ?? '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:tradingEnvironment')}>
                                {strategyDetailQuery.data.tradeEnv}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:status')}>
                                <Tag color="blue">{strategyDetailQuery.data.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:enabledStatus')}>
                                <Tag
                                    color={strategyDetailQuery.data.enabled ? 'success' : 'default'}
                                >
                                    {strategyDetailQuery.data.enabled
                                        ? t('pages:enabled')
                                        : t('pages:disabled')}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:version')}>
                                {strategyDetailQuery.data.version}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:createdAt')}>
                                {formatDateTime(strategyDetailQuery.data.createdAt)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:updatedAt')}>
                                {formatDateTime(strategyDetailQuery.data.updatedAt)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:configurationSnapshot')} span={2}>
                                <ObjectConfigurationSummary value={strategyDetailQuery.data.configSnapshot} />
                            </Descriptions.Item>
                        </Descriptions>
                        <Card title={t('pages:actions2')} size="small">
                            <Space wrap>
                                {!isPublicDemo && <Button
                                    type="primary"
                                    disabled={!canWrite || strategyDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(true)}
                                >
                                    {t('pages:enableStrategy')}
                                </Button>}
                                {!isPublicDemo && <Button
                                    danger
                                    disabled={!canWrite || !strategyDetailQuery.data.enabled}
                                    loading={updateStatusMutation.isPending}
                                    onClick={() => handleStatusUpdate(false)}
                                >
                                    {t('pages:disableStrategy')}
                                </Button>}
                                <Button onClick={() => strategyDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}
                                </Button>
                            </Space>
                        </Card>
                        {!isPublicDemo && <StrategyExecutionRelations
                            key={strategyDetailQuery.data.strategyId}
                            strategyId={strategyDetailQuery.data.strategyId}
                        />}
                        <Card title={t('pages:strategyVersions')} size="small">
                            {locatedVersionId &&
                                (locatedVersion.error ? (
                                    <Alert
                                        type="error"
                                        message={formatApiError(
                                            locatedVersion.error as AppApiError,
                                        )}
                                        action={
                                            <Button onClick={() => void locatedVersion.refetch()}>
                                                {t('pages:retry')}
                                            </Button>
                                        }
                                    />
                                ) : (
                                    <Descriptions
                                        size="small"
                                        column={1}
                                        title={t('objectSearch.kinds.VERSION')}
                                    >
                                        <Descriptions.Item label={t('pages:versionName')}>
                                            {locatedVersion.data?.versionName ?? t('pages:loading')}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:versionStatus')}>
                                            {locatedVersion.data?.status ?? '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:versionId')}>
                                            {locatedVersionId}
                                        </Descriptions.Item>
                                    </Descriptions>
                                ))}
                            <StrategyVersionList
                                key={`${selectedStrategyCode}:${versionListRevision}`}
                                strategyCode={selectedStrategyCode!}
                                columns={versionColumns}
                                locatedVersionId={locatedVersionId}
                            />
                        </Card>
                        <Card title={t('pages:createStrategyVersion')} size="small">
                            {(strategyDetailQuery.data.strategyType === 'SPOT_SMA_TARGET_V1' || strategyDetailQuery.data.strategyType === 'SPOT_CHANNEL_BREAKOUT_V1') ? (
                                <SpotTargetVersionForm
                                    key={strategyDetailQuery.data.strategyType}
                                    strategyType={strategyDetailQuery.data.strategyType as 'SPOT_SMA_TARGET_V1' | 'SPOT_CHANNEL_BREAKOUT_V1'}
                                    onCreate={handleCreateVersion}
                                    busy={createVersionMutation.isPending}
                                    disabled={!canWrite}
                                />
                            ) : (
                                <Form
                                    disabled={!canWrite}
                                    name="strategy-versionForm"
                                    form={versionForm}
                                    layout="vertical"
                                    initialValues={{
                                        status: 'DRAFT',
                                        paramSnapshotJson: '{}',
                                        sourceSnapshotJson: '{}',
                                    }}
                                    onFinish={handleCreateVersion}
                                >
                                    <Alert type="warning" showIcon message={t('product:configLegacyTypeHelp')} style={{marginBottom: 16}} />
                                    <Row gutter={[16, 0]}>
                                        <Col xs={24} md={12}>
                                            <Form.Item
                                                label={t('pages:versionName')}
                                                name="versionName"
                                                rules={[
                                                    {
                                                        required: true,
                                                        message: t('pages:enterAVersionName'),
                                                    },
                                                ]}
                                            >
                                                <Input
                                                    placeholder={t(
                                                        'pages:forExampleFreezeBaseline',
                                                    )}
                                                />
                                            </Form.Item>
                                        </Col>
                                        <Col xs={24} md={12}>
                                            <Form.Item
                                                label={t('pages:versionStatus')}
                                                name="status"
                                            >
                                                <Select
                                                    options={[
                                                        {label: t('pages:versionStateDRAFT'), value: 'DRAFT'},
                                                        {label: t('pages:versionStateACTIVE'), value: 'ACTIVE'},
                                                        {label: t('pages:versionStateARCHIVED'), value: 'ARCHIVED'},
                                                    ]}
                                                />
                                            </Form.Item>
                                        </Col>
                                        <Col xs={24}>
                                            <Form.Item
                                                label={t('product:configVersionParameters')}
                                                name="paramSnapshotJson"
                                                rules={[{validator: async (_, value?: string) => {try {configurationObject(value);} catch {throw new Error(t('product:configIncomplete'));}}}]}
                                            >
                                                <ObjectConfigurationInput label={t('pages:parameterSnapshotJson')} disabled={!canWrite} />
                                            </Form.Item>
                                        </Col>
                                        <Col xs={24}>
                                            <Form.Item
                                                label={t('product:configVersionConfiguration')}
                                                name="configSnapshotJson"
                                                rules={[{validator: async (_, value?: string) => {try {configurationObject(value);} catch {throw new Error(t('product:configIncomplete'));}}}]}
                                            >
                                                <ObjectConfigurationInput label={t('pages:configurationSnapshotJson')} disabled={!canWrite} />
                                            </Form.Item>
                                        </Col>
                                        <Col xs={24}>
                                            <Form.Item
                                                label={t('product:configVersionSource')}
                                                name="sourceSnapshotJson"
                                                rules={[{validator: async (_, value?: string) => {try {configurationObject(value);} catch {throw new Error(t('product:configIncomplete'));}}}]}
                                            >
                                                <ObjectConfigurationInput label={t('pages:sourceSnapshotJson')} disabled={!canWrite} />
                                            </Form.Item>
                                        </Col>
                                    </Row>
                                    <Space>
                                        <Button
                                            type="primary"
                                            htmlType="submit"
                                            loading={createVersionMutation.isPending}
                                        >
                                            {t('pages:createVersion')}
                                        </Button>
                                        <Button onClick={() => versionForm.resetFields()}>
                                            {t('pages:clear')}
                                        </Button>
                                    </Space>
                                </Form>
                            )}
                        </Card>
                    </Space>
                ) : null}
            </Drawer>
            <Drawer
                open={createOpen}
                width={640}
                title={t('pages:workflowCreateStrategy')}
                onClose={() => setCreateOpen(false)}
                destroyOnClose
            >
                {!canCreate && (
                    <Alert type="warning" showIcon message={t('pages:workflowAccountRequired')} />
                )}
                <Form
                    disabled={!canWrite}
                    name="strategy-create"
                    form={createForm}
                    layout="vertical"
                    initialValues={{configSnapshot: '{}', strategyType: 'SPOT_SMA_TARGET_V1'}}
                    onFinish={(values) => {
                        // 编码唯一性由后端裁决；这里先阻止一次用户动作被双击发送两遍。
                        if (createRequestInFlight.current) return;
                        createRequestInFlight.current = true;
                        createMutation.mutate(values, {
                            onSettled: () => {
                                createRequestInFlight.current = false;
                            },
                        });
                    }}
                >
                    <Steps size="small" current={0} style={{marginBottom: 16}} items={[
                        {title: t('product:configDefinitionStep')},
                        {title: t('product:configVersionStep')},
                    ]} />
                    <Typography.Paragraph type="secondary">{t('product:configDefinitionHelp')}</Typography.Paragraph>
                    <Form.Item
                        name="strategyCode"
                        label={t('pages:strategyCode')}
                        rules={[{required: true}]}
                    >
                        <Input />
                    </Form.Item>
                    <Form.Item
                        name="strategyName"
                        label={t('pages:strategyName')}
                        rules={[{required: true}]}
                    >
                        <Input />
                    </Form.Item>
                    <Form.Item
                        name="strategyType"
                        label={t('pages:strategyType')}
                        tooltip={t('product:strategyTypeHelp')}
                        rules={[{required: true}]}
                    >
                        {/* 创建与筛选复用同一份真实类型，避免相同value重复出现。 */}
                        <Select options={STRATEGY_TYPE_OPTIONS.filter(({value}) => !isPublicDemo || value !== 'SPOT_CHANNEL_BREAKOUT_V1').map(({value, label}) => ({value, label}))} />
                    </Form.Item>
                    <Alert type={['SPOT_SMA_TARGET_V1', 'SPOT_CHANNEL_BREAKOUT_V1'].includes(createStrategyType ?? '') ? 'info' : 'warning'} showIcon style={{marginBottom: 16}}
                        message={t(createStrategyType === 'SPOT_CHANNEL_BREAKOUT_V1' ? 'pages:channelNext' : createStrategyType === 'SPOT_SMA_TARGET_V1' ? 'product:configSmaNext' : 'product:configLegacyTypeHelp')} />
                    <Form.Item name="configSnapshot" label={t('product:configExtraDefinition')} extra={t('product:configDefinitionExtraHelp')}
                        rules={[{validator: async (_, value?: string) => {try {configurationObject(value);} catch {throw new Error(t('product:configIncomplete'));}}}]}>
                        <ObjectConfigurationInput label={t('product:configExtraDefinition')} disabled={!canWrite} />
                    </Form.Item>
                    <Typography.Paragraph>
                        {context.exchangeCode ?? '—'} / SIM / {t('pages:account')}:{' '}
                        {context.legacyAccountId ?? '—'}
                    </Typography.Paragraph>
                    <Button
                        htmlType="submit"
                        type="primary"
                        disabled={!canCreate}
                        loading={createMutation.isPending}
                    >
                        {t('pages:workflowCreateStrategy')}
                    </Button>
                </Form>
            </Drawer>
        </>
    );
}
