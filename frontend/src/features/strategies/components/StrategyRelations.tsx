import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Alert, Button, Card, Descriptions, Space, Table, Typography} from 'antd';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {apiClient} from '@/api/client';
import {validateResultPage} from '@/api/result-page';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {publishesApi} from '@/features/publishes/api/publishes';
import {
    defaultPublishesListFilters,
    type BacktestPublishListItem,
} from '@/features/publishes/types/publishes';
import {runsApi} from '@/features/runs/api/runs';
import {defaultStrategyRunListFilters} from '@/features/runs/types/runs';
import {backtestsApi} from '@/features/backtests/backtestsApi';
import {useAuthStore} from '@/store/auth-store';

/** 只读取冻结快照明确保存的数据集身份，不从当前配置推断。 */
function frozenDataset(snapshot: string | null) {
    try {
        const value = JSON.parse(snapshot ?? '{}');
        return typeof value.datasetId === 'string' ? value.datasetId : null;
    } catch {
        return null;
    }
}
type SimIdentity = {
    paperRunId: string;
    publishId: string;
    strategyVersionId: string | null;
    status: string;
};
function ErrorState({error, retry}: {error: unknown; retry: () => void}) {
    const {t} = useTranslation('pages');
    return (
        <Alert
            type="error"
            message={formatApiError(error as AppApiError)}
            action={<Button onClick={retry}>{t('retry')}</Button>}
        />
    );
}
function PublicationChain({publication}: {publication: BacktestPublishListItem}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [page, setPage] = useState(1);
    const run = useQuery({
        queryKey: ['strategy-publication-run', owner, publication.backtestRunId],
        queryFn: () => backtestsApi.getRun(publication.backtestRunId),
        retry: false,
        gcTime: 0,
    });
    const sim = useQuery({
        queryKey: ['strategy-publication-sim', owner, publication.publishRecordId, page],
        retry: false,
        gcTime: 0,
        queryFn: async ({signal}) => {
            const request = {limit: 5, offset: (page - 1) * 5};
            const {data} = await apiClient.get(
                `/publishes/${encodeURIComponent(publication.publishRecordId)}/sim-runs/page`,
                {params: request, signal},
            );
            return validateResultPage<SimIdentity>(data, request, (value) => {
                const row = value as SimIdentity;
                return row?.publishId === publication.publishRecordId &&
                    typeof row.paperRunId === 'string' &&
                    row.paperRunId &&
                    ['CREATED', 'RUNNING', 'STOPPED', 'FAILED'].includes(row.status)
                    ? row.paperRunId
                    : null;
            });
        },
    });
    return (
        <Space direction="vertical" style={{width: '100%'}}>
            <Typography.Text>{t('strategyRelations.frozen')}</Typography.Text>
            {run.error ? (
                <ErrorState error={run.error} retry={() => void run.refetch()} />
            ) : (
                run.data && (
                    <Descriptions size="small" column={1}>
                        <Descriptions.Item label={t('researchConfigurationId')}>
                            <Link
                                to={`/research?researchConfigId=${encodeURIComponent(run.data.researchConfigId)}`}
                            >
                                {run.data.researchConfigId}
                            </Link>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('backtestConfigurationId')}>
                            <Link
                                to={`/backtests?backtestConfigId=${encodeURIComponent(run.data.backtestConfigId)}`}
                            >
                                {run.data.backtestConfigId}
                            </Link>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('backtestRunId')}>
                            <Link
                                to={`/backtests?backtestConfigId=${encodeURIComponent(run.data.backtestConfigId)}&backtestRunId=${encodeURIComponent(run.data.backtestRunId)}`}
                            >
                                {run.data.backtestRunId} · {run.data.status}
                            </Link>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('versionId')}>
                            {run.data.strategyVersionId ?? t('strategyRelations.missing')}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('datasetId')}>
                            {frozenDataset(run.data.datasetSnapshotJson) ??
                                t('strategyRelations.missing')}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('strategyRelations.evaluation')}>
                            {publication.evaluationSummaryJson ?? t('strategyRelations.missing')}
                        </Descriptions.Item>
                    </Descriptions>
                )
            )}
            <Typography.Text strong>{t('strategyRelations.sim')}</Typography.Text>
            {sim.error ? (
                <ErrorState error={sim.error} retry={() => void sim.refetch()} />
            ) : (
                <Table<SimIdentity>
                    size="small"
                    rowKey="paperRunId"
                    loading={sim.isFetching}
                    dataSource={sim.data?.items ?? []}
                    columns={[
                        {
                            title: t('paperRunId'),
                            dataIndex: 'paperRunId',
                            render: (id: string) => (
                                <Link
                                    to={`/paper-trading/runs?paperRunId=${encodeURIComponent(id)}`}
                                >
                                    {id}
                                </Link>
                            ),
                        },
                        {title: t('status'), dataIndex: 'status'},
                        {
                            title: t('versionId'),
                            dataIndex: 'strategyVersionId',
                            render: (id: string | null) => id ?? t('strategyRelations.missing'),
                        },
                    ]}
                    pagination={{
                        current: page,
                        pageSize: 5,
                        total: sim.data?.total ?? 0,
                        showSizeChanger: false,
                        onChange: setPage,
                    }}
                    scroll={{x: 650}}
                />
            )}
        </Space>
    );
}
/** 按指定版本查正式发布；只展开一个发布的冻结链与SIM，避免全量扫描和逐行扇出。 */
export function VersionPublicationRelations({versionId}: {versionId: string}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [page, setPage] = useState(1);
    const [expanded, setExpanded] = useState<string | null>(null);
    const results = useQuery({
        queryKey: ['strategy-version-publications', owner, versionId, page],
        queryFn: ({signal}) =>
            publishesApi.page(
                {
                    ...defaultPublishesListFilters,
                    strategyVersionId: versionId,
                    limit: 5,
                    offset: (page - 1) * 5,
                },
                signal,
            ),
        retry: false,
        gcTime: 0,
    });
    return (
        <Card size="small" title={t('strategyRelations.publications')}>
            {results.error ? (
                <ErrorState error={results.error} retry={() => void results.refetch()} />
            ) : (
                <Table<BacktestPublishListItem>
                    size="small"
                    rowKey="publishRecordId"
                    loading={results.isFetching}
                    dataSource={results.data?.items ?? []}
                    columns={[
                        {
                            title: t('publishId'),
                            dataIndex: 'publishRecordId',
                            render: (id: string) => (
                                <Link to={`/publishes?publishId=${encodeURIComponent(id)}`}>
                                    {id}
                                </Link>
                            ),
                        },
                        {
                            title: t('name'),
                            dataIndex: 'publishName',
                            render: (name: string | null) => name ?? '—',
                        },
                        {title: t('status'), dataIndex: 'publishStatus'},
                        {title: t('versionId'), dataIndex: 'strategyVersionId'},
                    ]}
                    expandable={{
                        expandedRowKeys: expanded ? [expanded] : [],
                        onExpand: (open, row) => setExpanded(open ? row.publishRecordId : null),
                        expandedRowRender: (row) =>
                            expanded === row.publishRecordId ? (
                                <PublicationChain publication={row} />
                            ) : null,
                    }}
                    pagination={{
                        current: page,
                        pageSize: 5,
                        total: results.data?.total ?? 0,
                        showSizeChanger: false,
                        onChange: (next) => {
                            setPage(next);
                            setExpanded(null);
                        },
                    }}
                    scroll={{x: 700}}
                />
            )}
        </Card>
    );
}
/** 定义运行和版本发布分开读取；定义enabled不代表任何SIM或运行状态。 */
export function StrategyExecutionRelations({strategyId}: {strategyId: string}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [page, setPage] = useState(1);
    const results = useQuery({
        queryKey: ['strategy-execution-relations', owner, strategyId, page],
        queryFn: ({signal}) =>
            runsApi.page(
                {...defaultStrategyRunListFilters, strategyId, limit: 5, offset: (page - 1) * 5},
                signal,
            ),
        retry: false,
        gcTime: 0,
    });
    return (
        <Card size="small" title={t('strategyRelations.executions')}>
            <Typography.Paragraph>{t('strategyRelations.separate')}</Typography.Paragraph>
            {results.error ? (
                <ErrorState error={results.error} retry={() => void results.refetch()} />
            ) : (
                <Table
                    size="small"
                    rowKey="strategyRunId"
                    loading={results.isFetching}
                    dataSource={results.data?.items ?? []}
                    columns={[
                        {
                            title: t('runId'),
                            dataIndex: 'strategyRunId',
                            render: (id: string) => (
                                <Link to={`/runs?runId=${encodeURIComponent(id)}`}>{id}</Link>
                            ),
                        },
                        {title: t('status'), dataIndex: 'status'},
                        {title: t('account'), dataIndex: 'accountId'},
                    ]}
                    pagination={{
                        current: page,
                        pageSize: 5,
                        total: results.data?.total ?? 0,
                        showSizeChanger: false,
                        onChange: setPage,
                    }}
                    scroll={{x: 600}}
                />
            )}
        </Card>
    );
}
