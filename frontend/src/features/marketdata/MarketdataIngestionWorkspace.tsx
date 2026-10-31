import {useEffect, useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {
    Alert,
    Button,
    Card,
    Col,
    Collapse,
    DatePicker,
    Descriptions,
    Drawer,
    Form,
    Grid,
    List,
    Pagination,
    Row,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import dayjs, {type Dayjs} from 'dayjs';
import {Link, useSearchParams} from 'react-router-dom';
import {isPublicDemo} from '@/utils/distribution';
import {datasetFromJobLink, isResearchJob, researchSymbols, researchWindow, researchPreset, researchPresetDays, researchWindowBudget} from './research-dataset';
import type {ColumnsType} from 'antd/es/table';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {instrumentQueryKeys, marketdataQueryKeys} from '@/api/query-keys';
import {instrumentsApi} from '@/features/instruments/api/instruments';
import {isActiveSpot} from '@/features/preferences/components/WatchlistEditor';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {MarketdataIngestionJob, MarketdataIngestionRun} from '@/types/marketdata';
import {formatDateTime} from '@/utils/formatters';
import {confirmTimeWithoutSubmittingForm, endAfterStartRule} from '@/utils/time-range';
import {EXCHANGE_OPTIONS, INTERVAL_OPTIONS} from '@/constants/filter-options';
import {
    ingestionChanged,
    ingestionOperations,
    ingestionReads,
    ingestionSymbols,
    readIngestion,
    validIngestionId,
} from './ingestion-operations';
import './MarketdataIngestionWorkspace.css';
import {ingestionResultNotice} from './ingestion-result-presentation';

interface Values {
    exchangeCode: string;
    symbol: string;
    interval: string;
    startTime: Dayjs;
    endTime: Dayjs;
}
const queryRoot = (owner?: number) => ['marketdata-ingestion-workspace', owner] as const;

/** 本地保存的是原操作身份，不是服务端运行权威；换用户或重入页面都不自动提交。 */
function useOriginal(owner?: number) {
    const [state, setState] = useState(() => ({owner, value: readIngestion(owner)}));
    useEffect(() => {
        const refresh = () => setState({owner, value: readIngestion(owner)});
        refresh();
        window.addEventListener(ingestionChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(ingestionChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    return state.owner === owner ? state.value : readIngestion(owner);
}

/** 任务目录、原操作恢复和实际运行分开；公开行情不随顶部账户切换成交易资金事实。 */
export function MarketdataIngestionWorkspace({active}: {active: boolean}) {
    const {t} = useTranslation('pages');
    const cache = useQueryClient();
    const user = useAuthStore((state) => state.currentUser);
    const owner = user?.userId;
    const allowed =
        user?.roles.some((role) =>
            ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
        ) ?? false;
    const stored = useOriginal(owner);
    const pending = stored.kind === 'saved' ? stored.pending : null;
    const confirmed = pending?.phase === 'CONFIRMED' ? pending.result : undefined;
    const [open, setOpen] = useState(false);
    const [researchMode, setResearchMode] = useState(false);
    const [timePopup, setTimePopup] = useState<'start' | 'end' | null>(null);
    const [form] = useLocalizedForm<Values>();
    const exchange = Form.useWatch('exchangeCode', form) ?? 'BINANCE';
    const researchSymbol = Form.useWatch('symbol', form);
    const researchStart = Form.useWatch('startTime', form);
    const researchEnd = Form.useWatch('endTime', form);
    let researchBudget = null;
    try {
        if (researchMode && researchSymbol && researchStart && researchEnd)
            researchBudget = researchWindowBudget(researchWindow(researchSymbol, researchStart.format('YYYY-MM-DD'), researchEnd.format('YYYY-MM-DD')));
    } catch { /* 未通过日期校验时不展示预计覆盖，表单仍给出具体错误。 */ }
    const [params, setParams] = useSearchParams();
    const jobId = params.get('ingestionJobId');
    const runId = params.get('ingestionRunId');
    const jobPageText = params.get('ingestionJobPage') ?? '1';
    const runPageText = params.get('ingestionRunPage') ?? '1';
    const jobPage = /^[1-9]$|^10$/.test(jobPageText) ? Number(jobPageText) : null;
    const runPage = /^[1-9]$|^1\d$|^20$/.test(runPageText) ? Number(runPageText) : null;
    const invalid =
        (jobId != null && !validIngestionId(jobId)) ||
        (runId != null && (!jobId || !validIngestionId(runId))) ||
        !jobPage ||
        !runPage;
    const mobile = !Grid.useBreakpoint().md;
    const ready = active && Boolean(owner && owner > 0);
    const jobs = useQuery({
        queryKey: [...queryRoot(owner), 'jobs'],
        queryFn: ({signal}) => ingestionReads.jobs(signal),
        enabled: ready,
        retry: false,
    });
    const job = useQuery({
        queryKey: [...queryRoot(owner), 'job', jobId],
        queryFn: ({signal}) => ingestionReads.job(jobId!, signal),
        enabled: ready && !invalid && Boolean(jobId),
        retry: false,
    });
    const runs = useQuery({
        queryKey: [...queryRoot(owner), 'runs', jobId],
        queryFn: ({signal}) => ingestionReads.runs(jobId!, signal),
        enabled: ready && !invalid && Boolean(jobId),
        retry: false,
    });
    const exactRun = useQuery({
        queryKey: [...queryRoot(owner), 'run', jobId, runId],
        queryFn: ({signal}) => ingestionReads.run(jobId!, runId!, signal),
        enabled: ready && !invalid && Boolean(jobId && runId),
        retry: false,
    });
    const catalog = useQuery({
        queryKey: instrumentQueryKeys.list(),
        queryFn: () => instrumentsApi.list(),
        enabled: ready,
        retry: false,
    });
    useEffect(() => {
        if (!active) void cache.cancelQueries({queryKey: queryRoot(owner)});
        return () => {
            void cache.cancelQueries({queryKey: queryRoot(owner)});
        };
    }, [active, owner, cache]);

    function navigate(changes: Record<string, string | null>) {
        setParams((previous) => {
            const next = new URLSearchParams(previous);
            next.set('section', 'preparation');
            next.set('preparationTab', 'jobs');
            for (const [key, value] of Object.entries(changes)) {
                if (value === null) next.delete(key);
                else next.set(key, value);
            }
            return next;
        });
    }
    function viewJob(id: string) {
        navigate({ingestionJobId: id, ingestionRunId: null, ingestionRunPage: null});
    }
    async function refresh() {
        await Promise.all([
            cache.invalidateQueries({queryKey: queryRoot(owner)}),
            cache.invalidateQueries({queryKey: marketdataQueryKeys.barsAll()}),
            cache.invalidateQueries({queryKey: marketdataQueryKeys.readinessAll()}),
            cache.invalidateQueries({queryKey: marketdataQueryKeys.qualityOverviewAll()}),
        ]);
    }
    const onResult = async (result: MarketdataIngestionJob | MarketdataIngestionRun) => {
        navigate({
            ingestionJobId: result.jobId,
            ingestionRunId: 'runId' in result ? result.runId : null,
            ingestionJobPage: null,
            ingestionRunPage: null,
        });
        setOpen(false);
        await refresh();
    };
    const create = useMutation({
        mutationFn: ingestionOperations.create,
        onSuccess: onResult,
        onError: () => {
            // 原编号已保存时转到主面板恢复，避免抽屉遮住查询；明确拒绝后仍保留表单供纠正。
            if (readIngestion(owner).kind !== 'none') {
                setTimePopup(null);
                setOpen(false);
            }
        },
        retry: false,
    });
    const execute = useMutation({
        mutationFn: ingestionOperations.run,
        onSuccess: onResult,
        retry: false,
    });
    const lookup = useMutation({
        mutationFn: ingestionOperations.lookup,
        onSuccess: onResult,
        retry: false,
    });
    const resume = useMutation({
        mutationFn: ingestionOperations.resume,
        onSuccess: onResult,
        retry: false,
    });
    const acknowledge = useMutation({
        mutationFn: ingestionOperations.acknowledge,
        retry: false,
        onSuccess: () => {
            create.reset();
            execute.reset();
            lookup.reset();
            resume.reset();
            form.resetFields();
        },
    });
    const busy =
        create.isPending ||
        execute.isPending ||
        lookup.isPending ||
        resume.isPending ||
        acknowledge.isPending ||
        pending?.phase === 'SUBMITTING';
    const missing = lookup.isError && (lookup.error as AppApiError).status === 404;
    useEffect(() => {
        lookup.reset();
        resume.reset();
        acknowledge.reset();
    }, [
        owner,
        pending?.request.jobId,
        pending?.request.kind === 'RUN' ? pending.request.runId : null,
    ]);
    const symbols = (catalog.data ?? []).filter(
        (item) =>
            item.exchangeCode === exchange &&
            isActiveSpot(item) &&
            ingestionSymbols(exchange).includes(item.internalSymbol) &&
            (!researchMode || researchSymbols.includes(item.internalSymbol as (typeof researchSymbols)[number])),
    );
    const describe = (failure: unknown) => {
        const error = failure as AppApiError;
        const code = error.code?.replace(/^INGESTION_/, '');
        return error.code?.startsWith('INGESTION_')
            ? t(`ingestion.errors.${code}`, {defaultValue: formatApiError(error)})
            : formatApiError(error);
    };
    const status = (value: string) => (
        <Tag
            title={value}
            color={
                value === 'FAILED'
                    ? 'error'
                    : value === 'SUCCEEDED'
                      ? 'success'
                      : value === 'PARTIAL'
                        ? 'warning'
                        : 'default'
            }
        >
            {t(`ingestion.status.${value}`, {defaultValue: value})}
        </Tag>
    );
    const controls = (record: MarketdataIngestionJob) => (
        <Space wrap>
            <Button
                type="link"
                onClick={() => viewJob(record.jobId)}
                aria-label={t('ingestion.viewJob', {symbol: record.symbol})}
            >
                {t('ingestion.viewRuns')}
            </Button>
            <Button
                disabled={!allowed || Boolean(pending) || stored.kind === 'unavailable' || busy}
                onClick={() => execute.mutate(record.jobId)}
            >
                {t('ingestion.run')}
            </Button>
        </Space>
    );
    const jobColumns: ColumnsType<MarketdataIngestionJob> = [
        {title: t('tradingPair'), dataIndex: 'symbol', width: 130},
        {title: t('exchange'), dataIndex: 'exchangeCode', width: 100},
        {title: t('interval'), dataIndex: 'interval', width: 80},
        {title: t('status'), dataIndex: 'status', render: status, width: 130},
        {
            title: t('ingestion.range'),
            key: 'range',
            render: (_, row) => (
                <>
                    {formatDateTime(row.startTime)}
                    <br />
                    {formatDateTime(row.endTime)}
                </>
            ),
        },
        {title: t('updatedAt'), dataIndex: 'updatedAt', render: formatDateTime, width: 180},
        {title: t('action'), key: 'action', render: (_, row) => controls(row), width: 210},
    ];
    const runColumns: ColumnsType<MarketdataIngestionRun> = [
        {title: t('status'), dataIndex: 'status', render: status, width: 130},
        ...(['fetchedBars', 'insertedBars', 'updatedBars', 'skippedBars'] as const).map((key) => ({
            title: t(`ingestion.${key}`),
            dataIndex: key,
            width: 90,
        })),
        {title: t('startedAt'), dataIndex: 'startedAt', render: formatDateTime, width: 180},
        {
            title: t('action'),
            key: 'action',
            render: (_, row) => (
                <Button type="link" onClick={() => navigate({ingestionRunId: row.runId})}>
                    {t('ingestion.viewResult')}
                </Button>
            ),
        },
    ];
    const visibleJobs = jobs.isSuccess ? jobs.data : [];
    const visibleRuns = runs.isSuccess ? runs.data : [];
    const outsideWindow =
        (jobs.isSuccess &&
            jobPage != null &&
            jobPage > Math.max(1, Math.ceil(visibleJobs.length / 10))) ||
        (runs.isSuccess &&
            runPage != null &&
            runPage > Math.max(1, Math.ceil(visibleRuns.length / 5)));
    const selected = job.isSuccess ? job.data : null;
    const result = exactRun.isSuccess ? exactRun.data : null;
    const resultNotice = result ? ingestionResultNotice(result) : null;
    const errors = (
        confirmed
            ? [acknowledge.error]
            : [
                  create.error,
                  execute.error,
                  missing ? null : lookup.error,
                  resume.error,
                  acknowledge.error,
              ]
    ).filter(Boolean);
    const retryAlert = (failure: unknown, retry: () => void) => (
        <Alert
            type="error"
            showIcon
            message={describe(failure)}
            action={<Button onClick={retry}>{t('refresh')}</Button>}
        />
    );

    return (
        <Space className="ingestion-workspace" direction="vertical" size={16}>
            {stored.kind === 'unavailable' && (
                <Alert type="error" showIcon message={t('ingestion.errors.STORAGE_UNAVAILABLE')} />
            )}
            {!allowed && <Alert type="info" showIcon message={t('ingestion.errors.READ_ONLY')} />}
            {pending && (
                <Card size="small" className="ingestion-original" title={t('ingestion.original')}>
                    <Alert
                        showIcon
                        type={
                            confirmed
                                ? confirmed.status === 'FAILED'
                                    ? 'error'
                                    : 'success'
                                : 'warning'
                        }
                        message={t(
                            confirmed
                                ? 'ingestion.confirmed'
                                : pending.phase === 'SUBMITTING'
                                  ? 'ingestion.submitting'
                                  : 'ingestion.unknown',
                        )}
                        description={t(
                            confirmed ? 'ingestion.confirmedHelp' : 'ingestion.unknownHelp',
                        )}
                    />
                    <Space wrap style={{marginTop: 12}}>
                        <Tag>
                            {t(
                                pending.request.kind === 'CREATE'
                                    ? 'ingestion.create'
                                    : 'ingestion.run',
                            )}
                        </Tag>
                        {confirmed && status(confirmed.status)}
                        {!confirmed && (
                            <Button
                                disabled={busy}
                                loading={lookup.isPending}
                                onClick={() => lookup.mutate(pending)}
                            >
                                {t('ingestion.lookup')}
                            </Button>
                        )}
                        {!confirmed && (
                            <Button
                                disabled={busy || !missing || !allowed}
                                loading={resume.isPending}
                                onClick={() => resume.mutate(pending)}
                            >
                                {t('ingestion.resume')}
                            </Button>
                        )}
                        {confirmed && (
                            <Button
                                type="primary"
                                disabled={busy}
                                loading={acknowledge.isPending}
                                onClick={() => acknowledge.mutate(pending)}
                            >
                                {t('ingestion.acknowledge')}
                            </Button>
                        )}
                    </Space>
                    {missing && (
                        <Typography.Paragraph style={{marginTop: 12}}>
                            {t('ingestion.missing')}
                        </Typography.Paragraph>
                    )}
                    <Collapse
                        size="small"
                        ghost
                        items={[
                            {
                                key: 'original',
                                label: t('ingestion.originalDetails'),
                                children: (
                                    <Descriptions size="small" column={1}>
                                        <Descriptions.Item label={t('jobId')}>
                                            {pending.request.jobId}
                                        </Descriptions.Item>
                                        {pending.request.kind === 'RUN' ? (
                                            <Descriptions.Item label={t('runId')}>
                                                {pending.request.runId}
                                            </Descriptions.Item>
                                        ) : (
                                            <>
                                                <Descriptions.Item label={t('tradingPair')}>
                                                    {pending.request.input.exchangeCode} ·{' '}
                                                    {pending.request.input.symbol} ·{' '}
                                                    {pending.request.input.interval}
                                                </Descriptions.Item>
                                                <Descriptions.Item label={t('startTime')}>
                                                    {formatDateTime(
                                                        pending.request.input.startTime,
                                                    )}
                                                </Descriptions.Item>
                                                <Descriptions.Item label={t('endTime')}>
                                                    {formatDateTime(pending.request.input.endTime)}
                                                </Descriptions.Item>
                                            </>
                                        )}
                                    </Descriptions>
                                ),
                            },
                        ]}
                    />
                </Card>
            )}
            {errors.map((failure, index) => (
                <Alert key={index} type="error" showIcon message={describe(failure)} />
            ))}
            {invalid && (
                <Alert
                    type="error"
                    showIcon
                    message={t('ingestion.invalidLink')}
                    action={
                        <Button
                            onClick={() =>
                                navigate({
                                    ingestionJobId: null,
                                    ingestionRunId: null,
                                    ingestionJobPage: null,
                                    ingestionRunPage: null,
                                })
                            }
                        >
                            {t('ingestion.resetView')}
                        </Button>
                    }
                />
            )}
            {outsideWindow && (
                <Alert
                    type="warning"
                    showIcon
                    message={t('ingestion.outsideWindow')}
                    action={
                        <Button
                            onClick={() =>
                                navigate({ingestionJobPage: null, ingestionRunPage: null})
                            }
                        >
                            {t('ingestion.firstPage')}
                        </Button>
                    }
                />
            )}
            <Card
                title={t('ingestion.jobs')}
                extra={
                    <Space wrap>
                        <Button onClick={() => void jobs.refetch()} loading={jobs.isFetching}>
                            {t('refresh')}
                        </Button>
                        {!isPublicDemo && <Button
                            disabled={!allowed || busy || stored.kind !== 'none'}
                            onClick={() => {
                                const {startDate: start, endDate: end} = researchPreset(30);
                                setResearchMode(true);
                                form.setFieldsValue({exchangeCode: 'OKX', symbol: 'BTC-USDT', interval: '1h', startTime: dayjs(start), endTime: dayjs(end)});
                                setOpen(true);
                            }}
                        >{t('researchDataset.create')}</Button>}
                        <Button
                            type="primary"
                            disabled={!allowed || busy || stored.kind !== 'none'}
                            onClick={() => {setResearchMode(false); form.resetFields(); setOpen(true);}}
                        >
                            {t('ingestion.create')}
                        </Button>
                    </Space>
                }
            >
                <Typography.Paragraph type="secondary">
                    {t('ingestion.jobWindow', {count: jobs.isSuccess ? visibleJobs.length : '—'})}
                </Typography.Paragraph>
                {jobs.error ? (
                    retryAlert(jobs.error, () => void jobs.refetch())
                ) : mobile ? (
                    <>
                        <List
                            loading={jobs.isFetching}
                            dataSource={visibleJobs.slice(
                                ((jobPage ?? 1) - 1) * 10,
                                (jobPage ?? 1) * 10,
                            )}
                            locale={{emptyText: t('ingestion.emptyJobs')}}
                            renderItem={(row) => (
                                <List.Item key={row.jobId}>
                                    <Space direction="vertical" style={{width: '100%'}}>
                                        <Space wrap>
                                            <strong>{row.symbol}</strong>
                                            <span>
                                                {row.exchangeCode} · {row.interval}
                                            </span>
                                            {status(row.status)}
                                        </Space>
                                        <span>
                                            {formatDateTime(row.startTime)} →{' '}
                                            {formatDateTime(row.endTime)}
                                        </span>
                                        {controls(row)}
                                    </Space>
                                </List.Item>
                            )}
                        />
                        <Pagination
                            size="small"
                            current={jobPage ?? 1}
                            pageSize={10}
                            total={visibleJobs.length}
                            showSizeChanger={false}
                            onChange={(value) => navigate({ingestionJobPage: String(value)})}
                        />
                    </>
                ) : (
                    <Table
                        rowKey="jobId"
                        rowClassName={(record) =>
                            record.jobId === jobId ? 'ant-table-row-selected' : ''
                        }
                        size="small"
                        columns={jobColumns}
                        dataSource={visibleJobs}
                        loading={jobs.isFetching}
                        scroll={{x: 1000}}
                        locale={{emptyText: t('ingestion.emptyJobs')}}
                        pagination={{
                            current: jobPage ?? 1,
                            pageSize: 10,
                            showSizeChanger: false,
                            onChange: (value) => navigate({ingestionJobPage: String(value)}),
                        }}
                    />
                )}
            </Card>
            {jobId && !invalid && (
                <Card
                    title={
                        selected
                            ? `${selected.symbol} · ${selected.exchangeCode} · ${selected.interval}`
                            : t('ingestion.selectedJob')
                    }
                    extra={
                        <Space wrap>
                            <Button onClick={() => void refresh()}>{t('refresh')}</Button>
                            <Button
                                onClick={() =>
                                    navigate({
                                        ingestionJobId: null,
                                        ingestionRunId: null,
                                        ingestionRunPage: null,
                                    })
                                }
                            >
                                {t('ingestion.closeDetails')}
                            </Button>
                        </Space>
                    }
                >
                    {job.error
                        ? retryAlert(job.error, () => void job.refetch())
                        : selected && (
                              <Space wrap>
                                  <Typography.Text type="secondary">
                                      {t('ingestion.currentStatus')}
                                  </Typography.Text>
                                  {status(selected.status)}
                                  {controls(selected)}
                                  <span>
                                      {formatDateTime(selected.startTime)} →{' '}
                                      {formatDateTime(selected.endTime)}
                                  </span>
                              </Space>
                          )}
                    {selected && (
                        <Collapse
                            ghost
                            size="small"
                            items={[
                                {
                                    key: 'job-trace',
                                    label: t('ingestion.jobDetails'),
                                    children: (
                                        <Descriptions size="small" column={{xs: 1, md: 2}}>
                                            <Descriptions.Item label={t('jobId')}>
                                                <Typography.Text copyable>
                                                    {selected.jobId}
                                                </Typography.Text>
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('market')}>
                                                {selected.marketType}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('ingestion.jobSource')}>
                                                {selected.source}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('ingestion.createdBy')}>
                                                {selected.createdBy}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t('ingestion.createdAt')}>
                                                {formatDateTime(selected.createdAt)}
                                            </Descriptions.Item>
                                        </Descriptions>
                                    ),
                                },
                            ]}
                        />
                    )}
                    <Typography.Paragraph type="secondary" style={{marginTop: 12}}>
                        {t('ingestion.runWindow', {
                            count: runs.isSuccess ? visibleRuns.length : '—',
                        })}
                    </Typography.Paragraph>
                    {runs.error ? (
                        retryAlert(runs.error, () => void runs.refetch())
                    ) : mobile ? (
                        <>
                            <List
                                loading={runs.isFetching}
                                dataSource={visibleRuns.slice(
                                    ((runPage ?? 1) - 1) * 5,
                                    (runPage ?? 1) * 5,
                                )}
                                locale={{emptyText: t('ingestion.emptyRuns')}}
                                renderItem={(row) => (
                                    <List.Item key={row.runId}>
                                        <Space direction="vertical">
                                            <Space wrap>
                                                {status(row.status)}
                                                <span>{formatDateTime(row.startedAt)}</span>
                                            </Space>
                                            <span>
                                                {t('ingestion.runCounts', {
                                                    fetched: row.fetchedBars,
                                                    inserted: row.insertedBars,
                                                    updated: row.updatedBars,
                                                    skipped: row.skippedBars,
                                                })}
                                            </span>
                                            <Button
                                                onClick={() =>
                                                    navigate({ingestionRunId: row.runId})
                                                }
                                            >
                                                {t('ingestion.viewResult')}
                                            </Button>
                                        </Space>
                                    </List.Item>
                                )}
                            />
                            <Pagination
                                size="small"
                                current={runPage ?? 1}
                                pageSize={5}
                                total={visibleRuns.length}
                                showSizeChanger={false}
                                onChange={(value) => navigate({ingestionRunPage: String(value)})}
                            />
                        </>
                    ) : (
                        <Table
                            rowKey="runId"
                            rowClassName={(record) =>
                                record.runId === runId ? 'ant-table-row-selected' : ''
                            }
                            size="small"
                            columns={runColumns}
                            dataSource={visibleRuns}
                            loading={runs.isFetching}
                            scroll={{x: 760}}
                            locale={{emptyText: t('ingestion.emptyRuns')}}
                            pagination={{
                                current: runPage ?? 1,
                                pageSize: 5,
                                showSizeChanger: false,
                                onChange: (value) => navigate({ingestionRunPage: String(value)}),
                            }}
                        />
                    )}
                    {exactRun.error && retryAlert(exactRun.error, () => void exactRun.refetch())}
                    {result && (
                        <Card
                            size="small"
                            className="ingestion-result"
                            title={
                                <Space wrap>
                                    {t('ingestion.result')}
                                    {status(result.status)}
                                    <Typography.Text type="secondary">
                                        {formatDateTime(result.startedAt)}
                                    </Typography.Text>
                                </Space>
                            }
                        >
                            <Descriptions size="small" column={{xs: 2, md: 4}}>
                                {(
                                    [
                                        'fetchedBars',
                                        'insertedBars',
                                        'updatedBars',
                                        'skippedBars',
                                    ] as const
                                ).map((field) => (
                                    <Descriptions.Item key={field} label={t(`ingestion.${field}`)}>
                                        {result[field]}
                                    </Descriptions.Item>
                                ))}
                            </Descriptions>
                            {resultNotice && (
                                <Alert
                                    showIcon
                                    type={resultNotice.type}
                                    message={t(resultNotice.message)}
                                />
                            )}
                            {result.status === 'SUCCEEDED' && result.fetchedBars === 0 && (
                                <Alert type="info" showIcon message={t('ingestion.emptyCapture')} />
                            )}
                            {selected && !isPublicDemo && isResearchJob(selected) && (
                                <Space direction="vertical" style={{marginBlock: 12}}>
                                    <Typography.Text type="secondary">{t('researchDataset.afterIngestion')}</Typography.Text>
                                    <Link to={datasetFromJobLink(selected)}>{t('researchDataset.createDataset')}</Link>
                                </Space>
                            )}
                            <Collapse
                                ghost
                                size="small"
                                items={[
                                    {
                                        key: 'trace',
                                        label: t('ingestion.trace'),
                                        children: (
                                            <Descriptions size="small" column={{xs: 1, md: 2}}>
                                                <Descriptions.Item label={t('jobId')}>
                                                    {result.jobId}
                                                </Descriptions.Item>
                                                <Descriptions.Item label={t('runId')}>
                                                    {result.runId}
                                                </Descriptions.Item>
                                                <Descriptions.Item label={t('startedAt')}>
                                                    {formatDateTime(result.startedAt)}
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('ingestion.finishedAt')}
                                                >
                                                    {result.finishedAt
                                                        ? formatDateTime(result.finishedAt)
                                                        : '—'}
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('ingestion.requestedStart')}
                                                >
                                                    {formatDateTime(result.requestedStartTime)}
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('ingestion.requestedEnd')}
                                                >
                                                    {formatDateTime(result.requestedEndTime)}
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('ingestion.actualStart')}
                                                >
                                                    {result.actualStartTime
                                                        ? formatDateTime(result.actualStartTime)
                                                        : '—'}
                                                </Descriptions.Item>
                                                <Descriptions.Item label={t('ingestion.actualEnd')}>
                                                    {result.actualEndTime
                                                        ? formatDateTime(result.actualEndTime)
                                                        : '—'}
                                                </Descriptions.Item>
                                                {result.errorMessage && (
                                                    <Descriptions.Item
                                                        label={t('ingestion.errorMessage')}
                                                    >
                                                        {result.errorMessage}
                                                    </Descriptions.Item>
                                                )}
                                            </Descriptions>
                                        ),
                                    },
                                ]}
                            />
                        </Card>
                    )}
                </Card>
            )}
            <Drawer
                title={t(researchMode ? 'researchDataset.create' : 'ingestion.create')}
                open={open}
                // 日期弹层先消费Esc，避免同一次按键关闭外层抽屉；弹层关闭后恢复抽屉键盘关闭。
                keyboard={timePopup === null}
                width={640}
                onClose={() => {
                    setTimePopup(null);
                    setOpen(false);
                }}
                destroyOnHidden={false}
                footer={
                    <Space wrap>
                        <Button onClick={() => setOpen(false)}>{t('cancel')}</Button>
                        <Button
                            type="primary"
                            loading={create.isPending}
                            disabled={
                                !allowed || busy || stored.kind !== 'none' || (!researchMode && !catalog.isSuccess)
                            }
                            onClick={() => form.submit()}
                        >
                            {t('ingestion.create')}
                        </Button>
                    </Space>
                }
            >
                {!researchMode && catalog.error && retryAlert(catalog.error, () => void catalog.refetch())}
                <Form
                    form={form}
                    layout="vertical"
                    name="marketdata-stable-ingestion"
                    disabled={busy || stored.kind !== 'none' || !allowed}
                    initialValues={{exchangeCode: 'BINANCE', interval: '1m'}}
                    onFinish={(values) =>
                        create.mutate(researchMode ? researchWindow(values.symbol, values.startTime.format('YYYY-MM-DD'), values.endTime.format('YYYY-MM-DD')) : {
                            exchangeCode: values.exchangeCode,
                            marketType: 'SPOT',
                            symbol: values.symbol,
                            interval: values.interval,
                            startTime: values.startTime.toISOString(),
                            endTime: values.endTime.toISOString(),
                        })
                    }
                >
                    {researchMode && <Space wrap style={{marginBottom: 16}}>
                        {researchPresetDays.map(days => <Button key={days} onClick={() => {
                            const preset = researchPreset(days);
                            form.setFieldsValue({startTime: dayjs(preset.startDate), endTime: dayjs(preset.endDate)});
                        }}>{t('researchDataset.preset', {days})}</Button>)}
                    </Space>}
                    <Row gutter={16}>
                        <Col xs={24} sm={12}>
                            <Form.Item
                                name="exchangeCode"
                                label={t('exchange')}
                                rules={[{required: true}]}
                            >
                                <Select
                                    options={researchMode ? [{value: 'OKX', label: 'OKX'}] : EXCHANGE_OPTIONS}
                                    disabled={researchMode}
                                    onChange={() => form.setFieldValue('symbol', undefined)}
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={24} sm={12}>
                            <Form.Item label={t('market')}>
                                <Tag>{t('ingestion.spotOnly')}</Tag>
                            </Form.Item>
                        </Col>
                        <Col xs={24} sm={12}>
                            <Form.Item
                                name="symbol"
                                label={t('tradingPair')}
                                dependencies={['exchangeCode']}
                                rules={[
                                    {required: true},
                                    {
                                        validator: async (_, value) => {
                                            if (
                                                !(researchMode
                                                    ? researchSymbols.includes(value)
                                                    : symbols.some((item) => item.internalSymbol === value))
                                            )
                                                throw new Error(t('ingestion.chooseSupported'));
                                        },
                                    },
                                ]}
                            >
                                <Select
                                    showSearch
                                    loading={!researchMode && catalog.isFetching}
                                    options={researchMode ? researchSymbols.map((value) => ({value, label: value})) : symbols.map((item) => ({
                                        value: item.internalSymbol,
                                        label: item.internalSymbol,
                                    }))}
                                    placeholder={t('ingestion.chooseSupported')}
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={24} sm={12}>
                            <Form.Item
                                name="interval"
                                label={t('interval')}
                                rules={[{required: true}]}
                            >
                                <Select disabled={researchMode} options={researchMode ? [{value: '1h', label: '1h'}] : INTERVAL_OPTIONS} />
                            </Form.Item>
                        </Col>
                        <Col xs={24} sm={12}>
                            <Form.Item
                                name="startTime"
                                label={t(researchMode ? 'researchDataset.start' : 'startTime')}
                                rules={[{required: true, message: t('selectAStartTime')}]}
                            >
                                <DatePicker
                                    onOpenChange={(visible) =>
                                        setTimePopup((current) =>
                                            visible
                                                ? 'start'
                                                : current === 'start'
                                                  ? null
                                                  : current,
                                        )
                                    }
                                    showTime={researchMode ? false : {format: 'HH:mm:ss'}}
                                    format={researchMode ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'}
                                    onKeyDown={confirmTimeWithoutSubmittingForm}
                                    style={{width: '100%'}}
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={24} sm={12}>
                            <Form.Item
                                name="endTime"
                                label={t(researchMode ? 'researchDataset.end' : 'endTime')}
                                dependencies={['startTime']}
                                rules={[
                                    {required: true, message: t('selectAnEndTime')},
                                    endAfterStartRule('startTime'),
                                    {validator: async (_, value) => {
                                        if (!researchMode || !value || !form.getFieldValue('startTime')) return;
                                        try {researchWindow(form.getFieldValue('symbol'), form.getFieldValue('startTime').format('YYYY-MM-DD'), value.format('YYYY-MM-DD'));}
                                        catch {throw new Error(t('researchDataset.windowInvalid'));}
                                    }},
                                ]}
                            >
                                <DatePicker
                                    onOpenChange={(visible) =>
                                        setTimePopup((current) =>
                                            visible ? 'end' : current === 'end' ? null : current,
                                        )
                                    }
                                    showTime={researchMode ? false : {format: 'HH:mm:ss'}}
                                    format={researchMode ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'}
                                    onKeyDown={confirmTimeWithoutSubmittingForm}
                                    style={{width: '100%'}}
                                />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Typography.Paragraph type="secondary">
                        {t(researchMode ? 'researchDataset.help' : 'ingestion.createHelp')}
                    </Typography.Paragraph>
                    {researchMode && researchBudget && <Alert type="info" showIcon
                        message={t('researchDataset.budget', researchBudget)}
                        description={t('researchDataset.boundedContinue')}/>}
                </Form>
                {create.error && <Alert type="error" showIcon message={describe(create.error)} />}
            </Drawer>
        </Space>
    );
}
