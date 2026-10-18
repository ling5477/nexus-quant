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
import {useAuthStore} from '@/store/auth-store';
import {ResearchResultRecovery, usePendingResultAction} from '@/features/ResearchResultRecovery';
import {submitResultAction} from '@/features/research-result-recovery';
import {useSearchParams} from 'react-router-dom';

import {useBacktestRunDetailQuery} from '@/features/backtests/useBacktestsListQuery';
import {formatApiError, showApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {PUBLISH_STATUS_OPTIONS} from '@/constants/filter-options';
import {
    usePublishDetailQuery,
    usePublishMutation,
    usePublishesListQuery,
} from '@/features/publishes/hooks/usePublishesListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultPublishesListFilters,
    type BacktestPublishListItem,
    type PublishesListFilters,
} from '@/features/publishes/types/publishes';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type PublishRow = BacktestPublishListItem;

export function PublishesPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const [queryForm] = useLocalizedForm<PublishesListFilters>();
    const [publishForm] = useLocalizedForm<{displayName?: string}>();
    const [submittedFilters, setSubmittedFilters] = useState<PublishesListFilters>(
        defaultPublishesListFilters,
    );
    const [searchVersion, setSearchVersion] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [params, setParams] = useSearchParams();
    const selectedId = params.get('publishId');
    const invalidId = selectedId !== null && (!selectedId.trim() || selectedId.length > 128);
    const publishesQuery = usePublishesListQuery(
        {...submittedFilters, limit: pageSize, offset: (currentPage - 1) * pageSize},
        searchVersion,
    );
    const publishDetailQuery = usePublishDetailQuery(invalidId ? null : selectedId);
    const selectedRow =
        !publishDetailQuery.isError && publishDetailQuery.data?.publishRecordId === selectedId
            ? publishDetailQuery.data
            : null;
    const sourceRunQuery = useBacktestRunDetailQuery(selectedRow?.backtestRunId ?? null);
    // 失败发布可能尚无版本字段；只从精确关联的原运行恢复，不能手填或使用当前配置。
    const sourceRun =
        !sourceRunQuery.isError &&
        sourceRunQuery.data?.backtestRunId === selectedRow?.backtestRunId &&
        sourceRunQuery.data?.backtestConfigId === selectedRow?.backtestConfigId
            ? sourceRunQuery.data
            : null;
    const versionMatches =
        Boolean(sourceRun) &&
        (selectedRow?.strategyVersionId == null ||
            selectedRow.strategyVersionId === sourceRun?.strategyVersionId);
    const sourceReady = Boolean(
        sourceRun &&
            versionMatches &&
            sourceRun.status === 'SUCCEEDED' &&
            sourceRun.evaluationStatus === 'SUCCEEDED',
    );
    // 发布引用冻结评估；不得按运行查询最新报告替代历史身份。
    let frozenEvaluationId: string | null = null;
    try {
        const summary: unknown = JSON.parse(selectedRow?.evaluationSummaryJson ?? 'null');
        if (summary && typeof summary === 'object' && !Array.isArray(summary)) {
            const id = (summary as Record<string, unknown>).evalReportId;
            if (typeof id === 'string' && id.trim() && id.length <= 128) frozenEvaluationId = id;
        }
    } catch {
        // 旧快照损坏或没有身份时保留缺失，不猜测或重新生成评估。
    }
    const publishMutation = usePublishMutation();
    const pendingPublication = usePendingResultAction('PUBLISH');
    const currentUser = useAuthStore((state) => state.currentUser);
    const roles = currentUser?.roles ?? [];
    const canWrite = roles.some((role) =>
        ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
    );
    const hasSearched = searchVersion > 0;

    const visibleItems = publishesQuery.data?.items ?? [];

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
            render: (value: string | null) =>
                value ? <Typography.Text copyable>{value}</Typography.Text> : '-',
        },
        {
            title: t('pages:publishStatus'),
            dataIndex: 'publishStatus',
            key: 'publishStatus',
            width: 120,
            render: (value: string | null) => (value ? <Tag color="blue">{value}</Tag> : '-'),
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
                <Button
                    type="link"
                    onClick={() => {
                        const next = new URLSearchParams(params);
                        next.set('publishId', record.publishRecordId);
                        setParams(next);
                    }}
                >
                    {t('pages:viewDetails')}
                </Button>
            ),
        },
    ];

    const handleSearch = (values: PublishesListFilters) => {
        setCurrentPage(1);
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
        setCurrentPage(1);
    };

    const handlePublish = async (values: {displayName?: string}) => {
        if (
            !selectedRow ||
            !canWrite ||
            pendingPublication ||
            !sourceReady ||
            selectedRow.publishStatus === 'SUCCEEDED'
        )
            return;
        const ownerAtSubmit = currentUser?.userId;
        // 空版本仅代表原运行自身未绑定，不补造历史身份。
        const strategyVersionId = sourceRun?.strategyVersionId ?? undefined;
        try {
            const result = await submitResultAction(
                'PUBLISH',
                selectedRow.backtestRunId,
                () =>
                    publishMutation.mutateAsync({
                        runId: selectedRow.backtestRunId,
                        request: {
                            displayName: normalizeOptionalText(values.displayName),
                            strategyVersionId,
                        },
                    }),
                strategyVersionId,
            );
            if (useAuthStore.getState().currentUser?.userId !== ownerAtSubmit) return;
            if (result.publishStatus === 'SUCCEEDED')
                message.success(t('pages:publishActionCompletedWithTheLatestResult'));
            else message.warning(t('pages:resultRecovery.failed'));
            publishDetailQuery.refetch();
            setSearchVersion((value) => value + 1);
        } catch (error) {
            if (useAuthStore.getState().currentUser?.userId !== ownerAtSubmit) return;
            showApiError(error as AppApiError, message);
        }
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <ResearchResultRecovery kind="PUBLISH" />
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:publishResults')}
                        description={t(
                            'pages:viewBacktestPublishRecordsStrategyVersionBindingsAndFailureDetailsPublishAnExistingRunFromItsDetails',
                        )}
                        badge="Publishes"
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
                        form={queryForm}
                        layout="vertical"
                        initialValues={defaultPublishesListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:researchConfigurationId')}
                                    name="researchConfigId"
                                >
                                    <Input
                                        placeholder={t('pages:filterByResearchConfigurationId')}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:backtestConfigurationId')}
                                    name="backtestConfigId"
                                >
                                    <Input
                                        placeholder={t('pages:filterByBacktestConfigurationId')}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:sourceStrategyId')}
                                    name="sourceStrategyId"
                                >
                                    <Input placeholder={t('pages:filterBySourceStrategyId')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item
                                    label={t('pages:strategyVersionId')}
                                    name="strategyVersionId"
                                >
                                    <Input placeholder={t('pages:filterByStrategyVersionId')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                {/* 初态和清除后显示全部状态提示，实际过滤仍由原表单提交归一化。 */}
                                <Form.Item
                                    label={t('pages:publishStatus')}
                                    name="publishStatus"
                                    getValueProps={(value?: string) => ({
                                        value: value || undefined,
                                    })}
                                >
                                    <Select
                                        allowClear
                                        placeholder={t('pages:allStatuses')}
                                        options={PUBLISH_STATUS_OPTIONS}
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
                    extra={
                        hasSearched ? (
                            <Typography.Text type="secondary">
                                {t('pages:total')}
                                {publishesQuery.data?.total ?? 0} {t('pages:records')}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {!hasSearched ? (
                        <Empty description={t('pages:searchToLoadPublishResults')} />
                    ) : publishesQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryPublishResults')}
                            description={formatApiError(publishesQuery.error as AppApiError)}
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
                            className="publish-result-list-table"
                            rowKey="publishRecordId"
                            columns={publishColumns}
                            dataSource={visibleItems}
                            loading={publishesQuery.isFetching}
                            pagination={{
                                current: currentPage,
                                pageSize,
                                total: publishesQuery.data?.total ?? 0,
                                showSizeChanger: true,
                                responsive: true,
                                showLessItems: true,
                                pageSizeOptions: [10, 20, 50, 100],
                                onChange: (page, size) => {
                                    setCurrentPage(size === pageSize ? page : 1);
                                    setPageSize(size);
                                },
                            }}
                            scroll={{x: 1700}}
                            locale={{
                                emptyText: t('pages:noPublishResultsMatchTheseFilters'),
                            }}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={selectedId !== null}
                width={760}
                title={t('pages:publishDetails')}
                onClose={() => {
                    const next = new URLSearchParams(params);
                    next.delete('publishId');
                    setParams(next);
                }}
                destroyOnHidden
            >
                {invalidId && (
                    <Alert type="error" message={t('pages:fullPublishDetailsAreNotAvailable')} />
                )}
                {selectedId && !invalidId && publishDetailQuery.isPending && (
                    <Alert type="info" showIcon message={t('pages:loadingPublishDetails')} />
                )}
                {selectedId && !invalidId && publishDetailQuery.isError && (
                    <Alert
                        type="error"
                        showIcon
                        message={formatApiError(publishDetailQuery.error as AppApiError)}
                        action={
                            <Button onClick={() => void publishDetailQuery.refetch()}>
                                {t('pages:retry')}
                            </Button>
                        }
                    />
                )}
                {!selectedRow ? null : (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions
                            className="research-fact-details"
                            layout="vertical"
                            bordered
                            column={{xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2}}
                            size="small"
                        >
                            <Descriptions.Item label={t('pages:backtestRunId')}>
                                {selectedRow.backtestRunId}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:researchConfigurationId')}>
                                {selectedRow.researchConfigId}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:backtestConfigurationId')}>
                                {selectedRow.backtestConfigId}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:evaluationReportId')}>
                                {frozenEvaluationId ? (
                                    <Typography.Link
                                        href={`/evaluations?evaluationId=${encodeURIComponent(frozenEvaluationId)}`}
                                    >
                                        {frozenEvaluationId}
                                    </Typography.Link>
                                ) : (
                                    '-'
                                )}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:sourceStrategyId')}>
                                {selectedRow.sourceStrategyId}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyVersionId')}>
                                {selectedRow.strategyVersionId || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:publishStatus')}>
                                {selectedRow.publishStatus || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:publishedAt')}>
                                {formatDateTime(selectedRow.publishedAt)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:publishName')}>
                                {selectedRow.publishName || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:failureCode')}>
                                {selectedRow.failureCode || '-'}
                            </Descriptions.Item>
                        </Descriptions>
                        {publishDetailQuery.isLoading ? (
                            <Alert
                                type="info"
                                showIcon
                                message={t('pages:loadingPublishDetails')}
                            />
                        ) : publishDetailQuery.error ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:fullPublishDetailsAreNotAvailable')}
                                description={formatApiError(
                                    publishDetailQuery.error as AppApiError,
                                )}
                            />
                        ) : publishDetailQuery.data ? (
                            <Descriptions
                                className="research-fact-details"
                                layout="vertical"
                                bordered
                                column={{xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2}}
                                size="small"
                            >
                                <Descriptions.Item label={t('pages:publishRecordId')}>
                                    {publishDetailQuery.data.publishRecordId}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:targetStrategyDefinitionId')}>
                                    {publishDetailQuery.data.targetStrategyDefinitionId || '-'}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:strategyVersionId')}>
                                    {publishDetailQuery.data.strategyVersionId || '-'}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:publishStatus')}>
                                    {publishDetailQuery.data.publishStatus}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:publishedAt')}>
                                    {formatDateTime(publishDetailQuery.data.publishedAt)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:publishName')}>
                                    {publishDetailQuery.data.publishName || '-'}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:failureDiagnostics')} span={2}>
                                    {publishDetailQuery.data.failureMessage || '-'}
                                </Descriptions.Item>
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
                                    <Input
                                        placeholder={t('pages:optionalUsesTheDefaultNameIfEmpty')}
                                    />
                                </Form.Item>
                                <Form.Item label={t('pages:strategyVersionId')}>
                                    <Typography.Text
                                        copyable={Boolean(sourceRun?.strategyVersionId)}
                                    >
                                        {sourceRun?.strategyVersionId ??
                                            (sourceRun ? t('pages:notBound') : '—')}
                                    </Typography.Text>
                                    <Typography.Paragraph type="secondary">
                                        {t('pages:publishVersionFromRun')}
                                    </Typography.Paragraph>
                                </Form.Item>
                                {sourceRunQuery.isError && (
                                    <Alert
                                        type="error"
                                        showIcon
                                        message={formatApiError(
                                            sourceRunQuery.error as AppApiError,
                                        )}
                                        action={
                                            <Button onClick={() => sourceRunQuery.refetch()}>
                                                {t('pages:retry')}
                                            </Button>
                                        }
                                    />
                                )}
                                {selectedRow.publishStatus !== 'SUCCEEDED' &&
                                    !sourceReady &&
                                    !sourceRunQuery.isError && (
                                        <Alert
                                            type="info"
                                            showIcon
                                            message={t('pages:publishRunNotReady')}
                                        />
                                    )}
                                <Space wrap>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={publishMutation.isPending}
                                        disabled={
                                            !canWrite ||
                                            Boolean(pendingPublication) ||
                                            !sourceReady ||
                                            selectedRow.publishStatus === 'SUCCEEDED'
                                        }
                                    >
                                        {t('pages:publish')}
                                    </Button>
                                    <Button
                                        onClick={() => {
                                            void publishDetailQuery.refetch();
                                            void sourceRunQuery.refetch();
                                        }}
                                    >
                                        {t('pages:refreshDetails')}
                                    </Button>
                                </Space>
                            </Form>
                        </Card>
                    </Space>
                )}
            </Drawer>
        </>
    );
}
