import {useAuthStore} from '@/store/auth-store';
import {CreationRecoveryPanel} from '@/features/research/components/CreationRecoveryPanel';
import {MarketdataDatasetPicker} from '@/features/marketdata/components/MarketdataDatasetPicker';
import {useDatasetDetailQuery} from '@/features/marketdata/hooks/useDatasetQueries';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {confirmTimeWithoutSubmittingForm, endAfterStartRule} from '@/utils/time-range';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    App,
    Button,
    Card,
    Col,
    Collapse,
    DatePicker,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    InputNumber,
    Row,
    Space,
    Table,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {BacktestRunActions} from '@/features/backtests/BacktestRunActions';
import {ExecutionSpecInput} from '@/features/backtests/ExecutionSpecInput';
import {FrozenFeeSourceSummary} from '@/features/backtests/FrozenFeeSourceSummary';
import {executionObject, feeSelectionError} from '@/features/backtests/fee-source';
import {ResearchConfigPicker} from '@/features/research/components/ResearchConfigPicker';
import {ConfigurationArchiveAction} from '@/features/research/components/ConfigurationArchiveAction';
import {useResearchSelectionDetailQuery} from '@/features/research/hooks/useResearchListQuery';
import {
    useStrategyByIdQuery,
    useStrategyDetailQuery,
    useStrategyVersionDetailQuery,
} from '@/features/strategies/hooks/useStrategyListQuery';
import {StrategyDefinitionPicker} from '@/features/strategies/components/StrategyDefinitionPicker';
import {boundVersionStrategyCode} from '@/features/backtests/binding-identity';
import {StrategyVersionPicker} from '@/features/strategies/components/StrategyVersionPicker';

import {formatApiError, showApiError} from '@/api/errors';
import {evaluationsApi} from '@/api/evaluations';
import {evaluationsQueryKeys} from '@/api/query-keys';
import {ResearchValidityDetails} from '@/pages/evaluations/EvaluationsPage';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {
    useBindBacktestDatasetMutation,
    useBindBacktestStrategyVersionMutation,
    useBacktestDetailQuery,
    useBacktestRunDetailQuery,
    useBacktestsListQuery,
    useCreateBacktestRunMutation,
    useCreateBacktestMutation,
    useArchiveBacktestMutation,
} from '@/features/backtests/useBacktestsListQuery';
import type {AppApiError} from '@/types/api';
import type {BacktestConfigCreateRequest} from '@/features/backtests/backtestsTypes';
import {
    type BacktestConfigListItem,
    type BacktestsListFilters,
    defaultBacktestsListFilters,
} from '@/features/backtests/backtestsTypes';
import {
    formatDateTimeMilliseconds,
    formatDateTime,
    formatNumber,
    normalizeOptionalText,
} from '@/utils/formatters';
import dayjs from 'dayjs';

type BacktestRow = BacktestConfigListItem;
type DateFormValue =
    | string
    | null
    | undefined
    | {
          toISOString?: () => string;
          toDate?: () => Date;
      };

type BacktestConfigCreateFormValues = Omit<BacktestConfigCreateRequest, 'startTime' | 'endTime'> & {
    coverageDatasetId?: string;
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
    const canWrite = useAuthStore(
        (state) =>
            state.currentUser?.roles.some((role) => ['ADMIN', 'OPERATOR'].includes(role)) ?? false,
    );
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const [queryForm] = useLocalizedForm<BacktestsListFilters>();
    const [createForm] = useLocalizedForm<BacktestConfigCreateFormValues>();
    const [bindDatasetForm] = useLocalizedForm<{datasetId: string}>();
    const [bindStrategyVersionForm] = useLocalizedForm<{strategyVersionId: string}>();
    const [submittedFilters, setSubmittedFilters] = useState<BacktestsListFilters>(
        defaultBacktestsListFilters,
    );
    const [searchVersion, setSearchVersion] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    // 详情、运行与创建身份统一由 URL 拥有，关闭、刷新和浏览器返回不能分叉。
    const selectedConfigId = params.get('backtestConfigId');
    useEffect(() => {
        // 版本绑定草稿只属于当前配置，切换对象不能沿用上一份待提交身份。
        bindStrategyVersionForm.resetFields();
    }, [selectedConfigId, bindStrategyVersionForm]);
    const selectedRunId = params.get('backtestRunId');
    const createOpen = params.get('create') === 'true';
    const setCreateOpen = (open: boolean) => {
        const next = new URLSearchParams(params);
        if (open) next.set('create', 'true');
        else next.delete('create');
        setParams(next);
    };
    const backtestsQuery = useBacktestsListQuery(
        {...submittedFilters, limit: pageSize, offset: (currentPage - 1) * pageSize},
        searchVersion,
    );
    const backtestDetailQuery = useBacktestDetailQuery(selectedConfigId);
    const createBacktestMutation = useCreateBacktestMutation();
    const createBacktestRunMutation = useCreateBacktestRunMutation();
    const archiveBacktestMutation = useArchiveBacktestMutation();
    const configRequestInFlight = useRef(false);
    const runRequestInFlight = useRef(false);
    const backtestRunDetailQuery = useBacktestRunDetailQuery(selectedRunId);
    // URL 与缓存各自可携带有效 ID，但只有归属当前配置的运行能展示或执行。
    const loadedRun = backtestRunDetailQuery.data;
    const selectedRun =
        !backtestRunDetailQuery.isError &&
        loadedRun?.backtestConfigId === selectedConfigId &&
        loadedRun?.backtestRunId === selectedRunId
            ? loadedRun
            : null;
    const runIdentityMismatch = Boolean(
        loadedRun && !backtestRunDetailQuery.isError && !selectedRun,
    );
    const runEvaluationQuery = useQuery({
        queryKey: evaluationsQueryKeys.forRun(selectedRunId ?? ''),
        queryFn: () => evaluationsApi.forRun(selectedRunId!),
        enabled: Boolean(selectedRunId) && selectedRun?.evaluationStatus === 'SUCCEEDED',
        retry: false,
    });
    const bindDatasetMutation = useBindBacktestDatasetMutation(selectedConfigId);
    const bindStrategyVersionMutation = useBindBacktestStrategyVersionMutation(selectedConfigId);
    const associatedResearch = useResearchSelectionDetailQuery(
        backtestDetailQuery.data?.researchConfigId ?? null,
        Boolean(selectedConfigId),
    );
    const selectedResearch = associatedResearch.data;
    const sourceStrategyQuery = useStrategyByIdQuery(
        selectedResearch?.sourceStrategyId ?? null,
        Boolean(selectedConfigId),
    );
    const boundStrategyCode = boundVersionStrategyCode(
        backtestDetailQuery.data?.strategyVersionId,
        backtestDetailQuery.data?.strategyVersionSnapshotJson,
    );
    const boundStrategy = useStrategyDetailQuery(boundStrategyCode);
    // 只记录用户当前配置的选择；后台刷新默认值不能覆盖用户正在编辑的绑定草稿。
    const [versionParentDraft, setVersionParentDraft] = useState<{
        configId: string | null;
        strategyId: string | undefined;
    } | null>(null);
    const versionParentId =
        versionParentDraft?.configId === selectedConfigId
            ? versionParentDraft?.strategyId
            : boundStrategyCode
              ? boundStrategy.data?.strategyId
              : sourceStrategyQuery.data?.strategyId;
    const versionParent = useStrategyByIdQuery(versionParentId ?? null, Boolean(selectedConfigId));
    const boundVersion = useStrategyVersionDetailQuery(
        boundStrategyCode,
        backtestDetailQuery.data?.strategyVersionId,
        Boolean(selectedConfigId),
    );
    const createDatasetId = Form.useWatch('coverageDatasetId', createForm);
    const createDatasetQuery = useDatasetDetailQuery(createDatasetId, createOpen);
    const createDataset = createDatasetQuery.data;
    const boundDatasetQuery = useDatasetDetailQuery(
        backtestDetailQuery.data?.datasetId,
        Boolean(selectedConfigId),
    );
    const bindDatasetId = Form.useWatch('datasetId', bindDatasetForm);
    const hasSearched = searchVersion > 0;

    const visibleItems = backtestsQuery.data?.items ?? [];

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
            render: (value: string) => formatDateTimeMilliseconds(value),
        },
        {
            title: t('pages:endTime'),
            dataIndex: 'endTime',
            key: 'endTime',
            width: 180,
            render: (value: string) => formatDateTimeMilliseconds(value),
        },
        {
            title: t('pages:initialCapital'),
            dataIndex: 'initialCapital',
            key: 'initialCapital',
            width: 140,
            render: (value: number | string | null) => formatNumber(value, 2),
        },
        {
            title: t('pages:strategyVersionId'),
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 220,
            render: (value: string | null) =>
                value ? <Typography.Text copyable>{value}</Typography.Text> : t('pages:notBound'),
        },
        {
            title: t('pages:datasetId'),
            dataIndex: 'datasetId',
            key: 'datasetId',
            width: 220,
            render: (value: string | null) =>
                value ? <Typography.Text copyable>{value}</Typography.Text> : t('pages:notBound'),
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
                    <Button
                        type="link"
                        onClick={() => {
                            const runId =
                                params.get('backtestConfigId') === record.backtestConfigId
                                    ? params.get('backtestRunId')
                                    : null;
                            setParams(
                                runId
                                    ? {
                                          backtestConfigId: record.backtestConfigId,
                                          backtestRunId: runId,
                                      }
                                    : {backtestConfigId: record.backtestConfigId},
                            );
                        }}
                    >
                        {t('pages:viewDetails')}
                    </Button>
                    <Button
                        type="link"
                        onClick={() => navigate(`/backtests/${record.backtestConfigId}`)}
                    >
                        {t('pages:visualization')}
                    </Button>
                </Space>
            ),
        },
    ];

    const handleSearch = (values: BacktestsListFilters) => {
        setCurrentPage(1);
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
        setCurrentPage(1);
    };

    const handleCreate = (values: BacktestConfigCreateFormValues) => {
        // 正式配置和运行都由服务端分配新身份；同一帧重复点击必须在发送前同步拦截。
        if (configRequestInFlight.current) return;
        // 辅助选择仅带入时间/规则，正式数据集绑定仍是独立操作；不把辅助字段发送给API。
        const requestValues = {...values};
        delete requestValues.coverageDatasetId;
        const request = {
            ...requestValues,
            researchConfigId: normalizeOptionalText(values.researchConfigId),
            name: normalizeOptionalText(values.name),
            description: normalizeOptionalText(values.description),
            startTime: toIsoDateTime(values.startTime),
            endTime: toIsoDateTime(values.endTime),
            executionSpec: normalizeOptionalText(values.executionSpec),
            evaluationSpec: normalizeOptionalText(values.evaluationSpec),
        };
        // 输入转换失败不占用发送锁，修正表单后仍可提交。
        configRequestInFlight.current = true;
        createBacktestMutation.mutate(request, {
            onSuccess: (created) => {
                message.success(t('pages:backtestConfigurationCreated'));
                setCreateOpen(false);
                createForm.resetFields();
                setParams({backtestConfigId: created.backtestConfigId}, {replace: true});
            },
            onError: (error) => {
                showApiError(error as AppApiError, message);
            },
            onSettled: () => {
                configRequestInFlight.current = false;
            },
        });
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
        if (!selectedConfigId || runRequestInFlight.current) {
            return;
        }
        runRequestInFlight.current = true;
        createBacktestRunMutation.mutate(selectedConfigId, {
            onSuccess: (run) => {
                message.success(
                    t('pages:backtestRunCreatedWithTheCurrentConfigurationSnapshotFrozen'),
                );
                setParams(
                    {backtestConfigId: selectedConfigId, backtestRunId: run.backtestRunId},
                    {replace: true},
                );
            },
            onError: (error) => {
                showApiError(error as AppApiError, message);
            },
            onSettled: () => {
                runRequestInFlight.current = false;
            },
        });
    };

    return (
        <>
            <NqPageScaffold>
                {!canWrite && (
                    <Alert type="info" showIcon message={t('pages:researchReadOnlyRole')} />
                )}
                <CreationRecoveryPanel kinds={['BACKTEST', 'BACKTEST_RUN']} />
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:backtestConfigurations')}
                        description={t(
                            'pages:viewBacktestConfigurationsDatasetBindingsAndStrategyVersionSnapshotsOrCreateAConfiguration',
                        )}
                        badge="Backtests"
                        extra={
                            <Button
                                disabled={!canWrite}
                                type="primary"
                                onClick={() => setCreateOpen(true)}
                            >
                                {t('pages:createBacktestConfiguration')}
                            </Button>
                        }
                    />
                </Card>
                <Card
                    className="page-section"
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
                        name="backtest-queryForm"
                        form={queryForm}
                        layout="vertical"
                        initialValues={defaultBacktestsListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
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
                                <Form.Item
                                    label={t('pages:backtestConfigurationId')}
                                    name="backtestConfigId"
                                >
                                    <Input
                                        placeholder={t('pages:filterByBacktestConfigurationId')}
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
                    className="page-section"
                    bordered={false}
                    title={t('pages:results')}
                    extra={
                        hasSearched ? (
                            <Typography.Text type="secondary">
                                {t('pages:total')}
                                {backtestsQuery.data?.total ?? 0} {t('pages:records')}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {!hasSearched ? (
                        <Empty description={t('pages:workflowManualSearch')} />
                    ) : backtestsQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryBacktestConfigurations')}
                            description={formatApiError(backtestsQuery.error as AppApiError)}
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
                            className="backtest-config-list-table"
                            rowKey="backtestConfigId"
                            columns={backtestColumns}
                            dataSource={visibleItems}
                            loading={backtestsQuery.isFetching}
                            pagination={{
                                current: currentPage,
                                pageSize,
                                total: backtestsQuery.data?.total ?? 0,
                                showSizeChanger: true,
                                responsive: true,
                                showLessItems: true,
                                pageSizeOptions: [10, 20, 50, 100],
                                onChange: (page, size) => {
                                    setCurrentPage(size === pageSize ? page : 1);
                                    setPageSize(size);
                                },
                            }}
                            scroll={{x: 2040}}
                            locale={{
                                emptyText: t('pages:noBacktestConfigurationsMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </NqPageScaffold>
            <Drawer
                open={Boolean(selectedConfigId)}
                width={760}
                title={t('pages:backtestConfigurationDetails')}
                onClose={() => {
                    const next = new URLSearchParams(params);
                    next.delete('backtestConfigId');
                    next.delete('backtestRunId');
                    setParams(next);
                }}
                destroyOnClose
            >
                {backtestDetailQuery.isLoading ? (
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:loadingBacktestConfigurationDetails')}
                    />
                ) : backtestDetailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadBacktestConfigurationDetails')}
                        description={formatApiError(backtestDetailQuery.error as AppApiError)}
                    />
                ) : backtestDetailQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        {associatedResearch.isError && (
                            <Alert
                                type="error"
                                showIcon
                                message={t('pages:researchPickerSelectedUnavailable')}
                                description={formatApiError(
                                    associatedResearch.error as AppApiError,
                                )}
                                action={
                                    <Button onClick={() => void associatedResearch.refetch()}>
                                        {t('pages:retry')}
                                    </Button>
                                }
                            />
                        )}
                        {sourceStrategyQuery.isError && (
                            <Alert
                                type="error"
                                showIcon
                                message={t('pages:failedToLoadStrategyDetails')}
                                description={formatApiError(
                                    sourceStrategyQuery.error as AppApiError,
                                )}
                                action={
                                    <Button onClick={() => void sourceStrategyQuery.refetch()}>
                                        {t('pages:retry')}
                                    </Button>
                                }
                            />
                        )}
                        {runIdentityMismatch && (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:workflowRunConfigMismatch')}
                            />
                        )}
                        {selectedRun && (
                            <BacktestRunActions
                                key={selectedRun.backtestRunId}
                                run={selectedRun}
                                onChanged={() => {
                                    void backtestRunDetailQuery.refetch();
                                }}
                            />
                        )}
                        <Descriptions
                            className="research-object-details"
                            bordered
                            column={{xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2}}
                            size="small"
                        >
                            <Descriptions.Item label={t('pages:name')} span={2}>
                                {backtestDetailQuery.data.name}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:startTime')}>
                                {formatDateTimeMilliseconds(backtestDetailQuery.data.startTime)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:endTime')}>
                                {formatDateTimeMilliseconds(backtestDetailQuery.data.endTime)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:initialCapital')}>
                                {formatNumber(backtestDetailQuery.data.initialCapital, 2)} USDT
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyVersion')}>
                                {boundVersion.data?.versionName ??
                                    backtestDetailQuery.data.strategyVersionId ??
                                    t('pages:notBound')}
                                {boundStrategyCode && (
                                    <Typography.Text type="secondary">
                                        {' '}
                                        · {boundStrategyCode}
                                    </Typography.Text>
                                )}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:dataset')} span={2}>
                                {boundDatasetQuery.data?.datasetName ??
                                    backtestDetailQuery.data.datasetId ??
                                    t('pages:notBound')}
                            </Descriptions.Item>
                        </Descriptions>
                        {boundVersion.isError && (
                            <Alert
                                type="error"
                                showIcon
                                message={t('pages:versionPickerSelectedUnavailable')}
                                description={formatApiError(boundVersion.error as AppApiError)}
                                action={
                                    <Button onClick={() => void boundVersion.refetch()}>
                                        {t('pages:retry')}
                                    </Button>
                                }
                            />
                        )}
                        <Collapse
                            items={[
                                {
                                    key: 'snapshots',
                                    label: t('pages:frozenIdentityDetails'),
                                    children: (
                                        <Descriptions
                                            className="research-fact-details"
                                            layout="vertical"
                                            bordered
                                            column={{xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2}}
                                            size="small"
                                        >
                                            <Descriptions.Item
                                                label={t('pages:backtestConfigurationId')}
                                            >
                                                {backtestDetailQuery.data.backtestConfigId}
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:researchConfigurationId')}
                                            >
                                                {backtestDetailQuery.data.researchConfigId}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('pages:name')}>
                                                {backtestDetailQuery.data.name}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('pages:description')}>
                                                {backtestDetailQuery.data.description || '-'}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('pages:startTime')}>
                                                {formatDateTimeMilliseconds(
                                                    backtestDetailQuery.data.startTime,
                                                )}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('pages:endTime')}>
                                                {formatDateTimeMilliseconds(
                                                    backtestDetailQuery.data.endTime,
                                                )}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('pages:initialCapital')}>
                                                {formatNumber(
                                                    backtestDetailQuery.data.initialCapital,
                                                    2,
                                                )}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('pages:createdAt')}>
                                                {formatDateTime(backtestDetailQuery.data.createdAt)}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('pages:updatedAt')}>
                                                {formatDateTime(backtestDetailQuery.data.updatedAt)}
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:executionParameters')}
                                                span={2}
                                            >
                                                <Typography.Paragraph style={{marginBottom: 0}}>
                                                    {backtestDetailQuery.data.executionSpec || '-'}
                                                </Typography.Paragraph>
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:evaluationParameters')}
                                                span={2}
                                            >
                                                <Typography.Paragraph style={{marginBottom: 0}}>
                                                    {backtestDetailQuery.data.evaluationSpec || '-'}
                                                </Typography.Paragraph>
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:strategyVersionId')}
                                                span={2}
                                            >
                                                <Typography.Text
                                                    copyable={Boolean(
                                                        backtestDetailQuery.data.strategyVersionId,
                                                    )}
                                                >
                                                    {backtestDetailQuery.data.strategyVersionId ||
                                                        t('pages:notBound')}
                                                </Typography.Text>
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:strategyVersionSnapshot')}
                                                span={2}
                                            >
                                                <Typography.Paragraph style={{marginBottom: 0}}>
                                                    {backtestDetailQuery.data
                                                        .strategyVersionSnapshotJson || '{}'}
                                                </Typography.Paragraph>
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:parameterSnapshot')}
                                                span={2}
                                            >
                                                <Typography.Paragraph style={{marginBottom: 0}}>
                                                    {backtestDetailQuery.data.paramSnapshotJson ||
                                                        '{}'}
                                                </Typography.Paragraph>
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:datasetId')}
                                                span={2}
                                            >
                                                <Typography.Text
                                                    copyable={Boolean(
                                                        backtestDetailQuery.data.datasetId,
                                                    )}
                                                >
                                                    {backtestDetailQuery.data.datasetId ||
                                                        t('pages:notBound')}
                                                </Typography.Text>
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:datasetSnapshot')}
                                                span={2}
                                            >
                                                <Typography.Paragraph style={{marginBottom: 0}}>
                                                    {backtestDetailQuery.data.datasetSnapshotJson ||
                                                        '{}'}
                                                </Typography.Paragraph>
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:configurationSnapshotJson')}
                                                span={2}
                                            >
                                                <Typography.Paragraph style={{marginBottom: 0}}>
                                                    {backtestDetailQuery.data.configSnapshotJson ||
                                                        '{}'}
                                                </Typography.Paragraph>
                                            </Descriptions.Item>
                                            <Descriptions.Item
                                                label={t('pages:configurationSnapshot')}
                                                span={2}
                                            >
                                                <Typography.Paragraph style={{marginBottom: 0}}>
                                                    {backtestDetailQuery.data.configSnapshot || '-'}
                                                </Typography.Paragraph>
                                            </Descriptions.Item>
                                        </Descriptions>
                                    ),
                                },
                            ]}
                        />
                        <Card title={t('pages:actions2')} size="small">
                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                <Alert
                                    type="info"
                                    showIcon
                                    message={t(
                                        'pages:bindingAStrategyVersionOrMarketDataDatasetDoesNotStartABacktestOrChangeStrategyLogic',
                                    )}
                                />
                                <Form
                                    disabled={!canWrite}
                                    name="backtest-bindStrategyVersionForm"
                                    form={bindStrategyVersionForm}
                                    className="backtest-binding-form"
                                    layout="inline"
                                    onFinish={handleBindStrategyVersion}
                                >
                                    <Form.Item label={t('pages:versionParentStrategy')}>
                                        <StrategyDefinitionPicker
                                            purpose="version"
                                            active={Boolean(selectedConfigId)}
                                            value={versionParentId}
                                            disabled={
                                                backtestDetailQuery.data.status === 'ARCHIVED'
                                            }
                                            onChange={(strategyId) => {
                                                setVersionParentDraft({
                                                    configId: selectedConfigId,
                                                    strategyId,
                                                });
                                                bindStrategyVersionForm.resetFields();
                                            }}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        label={t('pages:strategyVersion')}
                                        name="strategyVersionId"
                                        rules={[
                                            {
                                                required: true,
                                                message: t('pages:enterAStrategyVersionId'),
                                            },
                                        ]}
                                    >
                                        <StrategyVersionPicker
                                            key={`${selectedConfigId}:${versionParent.data?.strategyCode ?? ''}`}
                                            strategyCode={versionParent.data?.strategyCode}
                                            disabled={
                                                backtestDetailQuery.data.status === 'ARCHIVED'
                                            }
                                            active={Boolean(selectedConfigId)}
                                        />
                                    </Form.Item>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={bindStrategyVersionMutation.isPending}
                                        disabled={
                                            !canWrite ||
                                            backtestDetailQuery.data.status === 'ARCHIVED'
                                        }
                                    >
                                        {t('pages:bindStrategyVersion')}
                                    </Button>
                                </Form>
                                <Form
                                    disabled={!canWrite}
                                    name="backtest-bindDatasetForm"
                                    form={bindDatasetForm}
                                    className="backtest-binding-form"
                                    layout="inline"
                                    onFinish={handleBindDataset}
                                >
                                    <Form.Item
                                        label={t('pages:dataset')}
                                        name="datasetId"
                                        rules={[
                                            {required: true, message: t('pages:selectADataset')},
                                        ]}
                                    >
                                        <MarketdataDatasetPicker
                                            active={Boolean(selectedConfigId)}
                                        />
                                    </Form.Item>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={bindDatasetMutation.isPending}
                                        disabled={
                                            !canWrite ||
                                            !bindDatasetId ||
                                            backtestDetailQuery.data.status === 'ARCHIVED'
                                        }
                                    >
                                        {t('pages:bindDataset')}
                                    </Button>
                                </Form>
                                {boundDatasetQuery.isError && (
                                    <Alert
                                        type="error"
                                        showIcon
                                        message={t('pages:failedToLoadDatasets')}
                                        description={formatApiError(
                                            boundDatasetQuery.error as AppApiError,
                                        )}
                                        action={
                                            <Button
                                                onClick={() => void boundDatasetQuery.refetch()}
                                            >
                                                {t('pages:retry')}
                                            </Button>
                                        }
                                    />
                                )}
                                <Button onClick={() => backtestDetailQuery.refetch()}>
                                    {t('pages:refreshDetails')}
                                </Button>
                                <Button
                                    onClick={handleCreateRun}
                                    loading={createBacktestRunMutation.isPending}
                                    disabled={
                                        !canWrite || backtestDetailQuery.data.status === 'ARCHIVED'
                                    }
                                >
                                    {t('pages:createBacktestRun')}
                                </Button>
                                <ConfigurationArchiveAction
                                    key={backtestDetailQuery.data.backtestConfigId}
                                    label={t('pages:archiveBacktestConfig')}
                                    configurationId={backtestDetailQuery.data.backtestConfigId}
                                    configuration={backtestDetailQuery.data}
                                    pending={archiveBacktestMutation.isPending}
                                    onArchive={(reason) =>
                                        archiveBacktestMutation
                                            .mutateAsync({
                                                configId:
                                                    backtestDetailQuery.data!.backtestConfigId,
                                                reason,
                                            })
                                            .then(() => setCurrentPage(1))
                                    }
                                />
                            </Space>
                        </Card>
                        <Card title={t('pages:backtestRunDetails')} size="small">
                            {!selectedRunId ? (
                                <Empty
                                    description={t(
                                        'pages:theFrozenRunSnapshotAppearsAfterTheBacktestRunIsCreated',
                                    )}
                                />
                            ) : backtestRunDetailQuery.isLoading ? (
                                <Alert
                                    type="info"
                                    showIcon
                                    message={t('pages:loadingBacktestRunDetails')}
                                />
                            ) : backtestRunDetailQuery.error ? (
                                <Alert
                                    type="error"
                                    showIcon
                                    message={t('pages:failedToLoadBacktestRunDetails')}
                                    description={formatApiError(
                                        backtestRunDetailQuery.error as AppApiError,
                                    )}
                                />
                            ) : selectedRun ? (
                                <Descriptions
                                    className="research-object-details"
                                    bordered
                                    column={{xs: 1, sm: 1, md: 2}}
                                    size="small"
                                >
                                    <Descriptions.Item label={t('pages:backtestRunId')} span={2}>
                                        <Typography.Text copyable>
                                            {selectedRun.backtestRunId}
                                        </Typography.Text>
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('pages:runStatus')}>
                                        {selectedRun.status}
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('pages:requestedAt')}>
                                        {formatDateTime(selectedRun.requestedAt)}
                                    </Descriptions.Item>
                                    <Descriptions.Item
                                        label={t('pages:strategyVersionSnapshot')}
                                        span={2}
                                    >
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {selectedRun.strategyVersionSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('pages:datasetSnapshot')} span={2}>
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {selectedRun.datasetSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item
                                        label={t('pages:parameterSnapshot')}
                                        span={2}
                                    >
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {selectedRun.paramSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                    <Descriptions.Item
                                        label={t('pages:configurationSnapshot')}
                                        span={2}
                                    >
                                        <Typography.Paragraph style={{marginBottom: 0}}>
                                            {selectedRun.configSnapshotJson || '{}'}
                                        </Typography.Paragraph>
                                    </Descriptions.Item>
                                </Descriptions>
                            ) : null}
                        </Card>
                        {selectedRun && (
                            <FrozenFeeSourceSummary
                                configuration={selectedRun.configSnapshotJson}
                            />
                        )}
                        {selectedRun &&
                            (runEvaluationQuery.isFetching ? (
                                <Alert type="info" message={t('pages:loadingEvaluationDetails')} />
                            ) : runEvaluationQuery.error ? (
                                <Alert
                                    type="warning"
                                    message={t('pages:researchNotAvailable')}
                                    description={formatApiError(
                                        runEvaluationQuery.error as AppApiError,
                                    )}
                                />
                            ) : (
                                <ResearchValidityDetails
                                    value={runEvaluationQuery.data?.researchValidity}
                                />
                            ))}
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
                <Form
                    disabled={!canWrite}
                    name="backtest-createForm"
                    form={createForm}
                    layout="vertical"
                    onFinish={handleCreate}
                    initialValues={{
                        researchConfigId: params.get('researchConfigId') ?? '',
                        evaluationSpec: '{}',
                    }}
                >
                    <Form.Item
                        label={t('pages:researchConfigurationSelection')}
                        name="researchConfigId"
                        rules={[{required: true, message: t('pages:enterResearchconfigid')}]}
                    >
                        <ResearchConfigPicker active={createOpen} />
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
                    <Form.Item
                        name="coverageDatasetId"
                        label={t('pages:useDatasetCoverage')}
                        extra={t('pages:useDatasetCoverageHelp')}
                    >
                        <MarketdataDatasetPicker
                            aria-label={t('pages:useDatasetCoverage')}
                            active={createOpen}
                            onSelect={(d) => {
                                createForm.setFieldsValue({
                                    startTime: dayjs(d.startTime),
                                    endTime: dayjs(d.endTime),
                                });
                                try {
                                    const metadata = JSON.parse(d.requestJson) as {
                                        provider?: string;
                                        rule?: {
                                            lotSize?: string;
                                            tickSize?: string;
                                            minimumSize?: string;
                                        };
                                        ruleSha256?: string;
                                    };
                                    if (
                                        metadata.provider === 'public-capture' &&
                                        metadata.rule &&
                                        metadata.ruleSha256
                                    ) {
                                        const prior = executionObject(
                                            createForm.getFieldValue('executionSpec') || '{}',
                                        );
                                        createForm.setFieldValue(
                                            'executionSpec',
                                            JSON.stringify({
                                                ...prior,
                                                quantityStep: metadata.rule.lotSize,
                                                priceTick: metadata.rule.tickSize,
                                                minimumQuantity: metadata.rule.minimumSize,
                                                ruleSha256: metadata.ruleSha256,
                                                rulePolicy: 'CURRENTLY_OBSERVED_PUBLIC_RULES',
                                                costSource:
                                                    prior.costSource ?? 'EXPERIMENT_ASSUMPTION',
                                                minimumNotionalSource: 'EXPERIMENT_ASSUMPTION',
                                            }),
                                        );
                                    }
                                } catch {
                                    message.warning(t('pages:datasetRulesUnavailable'));
                                }
                            }}
                        />
                    </Form.Item>
                    <Form.Item
                        label={t('pages:startTime')}
                        name="startTime"
                        rules={[{required: true, message: t('pages:selectAStartTime')}]}
                    >
                        <DatePicker
                            onKeyDown={confirmTimeWithoutSubmittingForm}
                            showTime
                            format="YYYY-MM-DD HH:mm:ss.SSS"
                            style={{width: '100%'}}
                        />
                    </Form.Item>
                    <Form.Item
                        label={t('pages:endTime')}
                        name="endTime"
                        dependencies={['startTime']}
                        rules={[
                            {required: true, message: t('pages:selectAnEndTime')},
                            endAfterStartRule('startTime'),
                        ]}
                    >
                        <DatePicker
                            onKeyDown={confirmTimeWithoutSubmittingForm}
                            showTime
                            format="YYYY-MM-DD HH:mm:ss.SSS"
                            style={{width: '100%'}}
                        />
                    </Form.Item>
                    <Form.Item
                        label={t('pages:initialCapital')}
                        name="initialCapital"
                        rules={[{required: true, message: t('pages:enterInitialCapital')}]}
                    >
                        <InputNumber style={{width: '100%'}} min={0.0001} />
                    </Form.Item>
                    <Form.Item
                        label={t('pages:executionParameters')}
                        name="executionSpec"
                        dependencies={['coverageDatasetId']}
                        rules={[
                            {required: true, message: t('pages:enterExecutionspec')},
                            {
                                validator: (_, value) => {
                                    const symbol =
                                        createDataset &&
                                        ['OKX', 'BINANCE'].includes(createDataset.exchangeCode) &&
                                        createDataset.marketType === 'SPOT'
                                            ? createDataset.symbol
                                            : undefined;
                                    const error = feeSelectionError(
                                        value,
                                        symbol,
                                        createDataset?.exchangeCode,
                                    );
                                    return error
                                        ? Promise.reject(new Error(t('pages:' + error)))
                                        : Promise.resolve();
                                },
                            },
                        ]}
                    >
                        <ExecutionSpecInput market={createDataset} />
                    </Form.Item>
                    <Collapse
                        items={[
                            {
                                key: 'evaluation',
                                forceRender: true,
                                label: t('pages:evaluationParameters'),
                                children: (
                                    <Form.Item
                                        label={t('pages:evaluationParameters')}
                                        name="evaluationSpec"
                                        rules={[
                                            {
                                                required: true,
                                                message: t('pages:enterEvaluationspec'),
                                            },
                                        ]}
                                    >
                                        <Input.TextArea rows={4} />
                                    </Form.Item>
                                ),
                            },
                        ]}
                    />
                    <Space>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={createBacktestMutation.isPending}
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
