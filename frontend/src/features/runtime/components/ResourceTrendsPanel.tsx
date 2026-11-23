import {useEffect, useMemo, useState} from 'react';
import {
    Alert,
    Button,
    Card,
    Col,
    Collapse,
    Descriptions,
    Empty,
    Row,
    Space,
    Spin,
    Table,
    Tag,
    Typography,
} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {resourceQueryKeys} from '@/api/query-keys';
import {NqMetricCard} from '@/components/nq';
import {formatNqNumber} from '@/nq-design-system/format/nqFormat';
import {useEChart} from '@/nq-design-system/charts/useEChart';
import {echarts} from '@/nq-design-system/charts/echarts-core';
import {buildNqLineChartBaseOption} from '@/nq-design-system/theme/nqChartTheme';
import {nqColor} from '@/theme/tokens';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {bytesToMiB, resourceApi, resourcesAreStale, type JvmResourceSample} from '../api/resources';

const formatMiB = (value: number | null) => {
    const measured = bytesToMiB(value);
    return measured == null ? '—' : `${formatNqNumber(measured, {precision: 1})} MiB`;
};

/** 两个有界时间图只显示当前JVM的实际采样；明细提供不依赖悬停的键盘和触摸读取。 */
export function ResourceTrendsPanel({active}: {active: boolean}) {
    const {t} = useTranslation('pages');
    const [now, setNow] = useState(Date.now);
    const query = useQuery({
        queryKey: resourceQueryKeys.history(),
        queryFn: ({signal}) => resourceApi.read(signal),
        enabled: active,
        retry: false,
        refetchInterval: active ? 15_000 : false,
        refetchIntervalInBackground: false,
    });
    useEffect(() => {
        if (!active) return;
        const timer = window.setInterval(() => setNow(Date.now()), 5000);
        return () => window.clearInterval(timer);
    }, [active]);
    const snapshot = query.data;
    const samples = snapshot?.samples ?? [];
    const latest = samples.at(-1);
    const stale =
        query.isError ||
        Boolean(snapshot && resourcesAreStale(snapshot, Math.max(now, Date.now())));
    const status = stale && samples.length ? 'STALE' : snapshot?.status;
    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:resources.title')}
            data-testid="jvm-resource-trends"
            extra={
                <Button
                    size="small"
                    loading={query.isFetching}
                    onClick={() => void query.refetch()}
                >
                    {t('pages:resources.refresh')}
                </Button>
            }
        >
            <Space direction="vertical" size={12} style={{width: '100%', minWidth: 0}}>
                {query.isPending && (
                    <Spin>
                        <div style={{minHeight: 160}} />
                    </Spin>
                )}
                {query.isError && (
                    <Alert
                        showIcon
                        type={samples.length ? 'warning' : 'error'}
                        message={formatApiError(query.error as AppApiError)}
                        description={samples.length ? t('pages:resources.lastKnown') : undefined}
                    />
                )}
                {snapshot && (
                    <>
                        <Space wrap>
                            <Tag>{t('pages:resources.scope')}</Tag>
                            <Tag color={status === 'AVAILABLE' ? 'blue' : 'orange'}>
                                {t(`pages:resources.status_${status}`)}
                            </Tag>
                            <Typography.Text type="secondary">
                                {t('pages:resources.lastSample')}：
                                {formatDateTime(snapshot.lastSampleAt)}
                            </Typography.Text>
                        </Space>
                        {latest ? (
                            <>
                                <div className="nq-status-strip nq-resource-metrics">
                                    <NqMetricCard
                                        label={t('pages:resources.cpu')}
                                        value={
                                            latest.processCpuPercent == null
                                                ? '—'
                                                : `${formatNqNumber(latest.processCpuPercent, {precision: 2})}%`
                                        }
                                        footer={t('pages:resources.cpuBasis', {
                                            count: latest.availableProcessors,
                                        })}
                                    />
                                    <NqMetricCard
                                        label={t('pages:resources.heapUsed')}
                                        value={formatMiB(latest.heapUsedBytes)}
                                        footer={`${t('pages:resources.heapMax')} ${formatMiB(latest.heapMaxBytes)}`}
                                    />
                                    <NqMetricCard
                                        label={t('pages:resources.threads')}
                                        value={latest.liveThreads ?? '—'}
                                    />
                                    <NqMetricCard
                                        label={t('pages:resources.uptime')}
                                        value={
                                            latest.uptimeMillis == null
                                                ? '—'
                                                : t('pages:resources.minutes', {
                                                      count: Math.floor(
                                                          latest.uptimeMillis / 60_000,
                                                      ),
                                                  })
                                        }
                                    />
                                </div>
                                <Row gutter={[12, 12]}>
                                    <Col xs={24} lg={12}>
                                        <Typography.Text strong>
                                            {t('pages:resources.cpu')} (%)
                                        </Typography.Text>
                                        <ResourceChart samples={samples} kind="cpu" />
                                    </Col>
                                    <Col xs={24} lg={12}>
                                        <Space wrap>
                                            <Typography.Text strong>
                                                {t('pages:resources.memory')} (MiB)
                                            </Typography.Text>
                                            <Tag color={nqColor.primary}>
                                                {t('pages:resources.heapUsed')}
                                            </Tag>
                                            <Tag>{t('pages:resources.heapCommitted')}</Tag>
                                        </Space>
                                        <ResourceChart samples={samples} kind="memory" />
                                    </Col>
                                </Row>
                            </>
                        ) : (
                            <Empty description={t('pages:resources.status_WARMING_UP')} />
                        )}
                        <Typography.Text type="secondary">
                            {t('pages:resources.coverage', {
                                count: samples.length,
                                start: formatDateTime(snapshot.windowStart),
                                seconds: snapshot.sampleSeconds,
                                max: snapshot.maxSamples,
                            })}
                        </Typography.Text>
                        <Typography.Text type="secondary">
                            {t('pages:resources.boundary')}
                        </Typography.Text>
                        <Collapse
                            items={[
                                {
                                    key: 'metrics',
                                    label: t('pages:resources.details'),
                                    children: (
                                        <>
                                            <Descriptions column={{xs: 1, md: 3}} size="small">
                                                <Descriptions.Item
                                                    label={t('pages:resources.nonHeap')}
                                                >
                                                    {formatMiB(latest?.nonHeapUsedBytes ?? null)}
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('pages:resources.gcCollections')}
                                                >
                                                    {latest?.gcCollections ?? '—'}
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('pages:resources.gcTime')}
                                                >
                                                    {latest?.gcTimeMillis == null
                                                        ? '—'
                                                        : `${latest.gcTimeMillis} ms`}
                                                </Descriptions.Item>
                                            </Descriptions>
                                            <Typography.Paragraph type="secondary">
                                                {t('pages:resources.gcBasis')}
                                            </Typography.Paragraph>
                                            <Table<JvmResourceSample>
                                                size="small"
                                                rowKey={(row) => row.measuredAt}
                                                dataSource={[...samples].reverse()}
                                                pagination={{pageSize: 15, showSizeChanger: false}}
                                                scroll={{x: 680}}
                                                columns={[
                                                    {
                                                        title: t('pages:resources.lastSample'),
                                                        dataIndex: 'measuredAt',
                                                        render: formatDateTime,
                                                    },
                                                    {
                                                        title: `${t('pages:resources.cpu')} (%)`,
                                                        dataIndex: 'processCpuPercent',
                                                        align: 'right',
                                                        render: (value: number | null) =>
                                                            value == null
                                                                ? '—'
                                                                : formatNqNumber(value, {
                                                                      precision: 2,
                                                                  }),
                                                    },
                                                    {
                                                        title: `${t('pages:resources.heapUsed')} (bytes)`,
                                                        dataIndex: 'heapUsedBytes',
                                                        align: 'right',
                                                        render: (value: number | null) =>
                                                            value == null
                                                                ? '—'
                                                                : formatNqNumber(value, {
                                                                      exact: true,
                                                                  }),
                                                    },
                                                    {
                                                        title: `${t('pages:resources.heapCommitted')} (bytes)`,
                                                        dataIndex: 'heapCommittedBytes',
                                                        align: 'right',
                                                        render: (value: number | null) =>
                                                            value == null
                                                                ? '—'
                                                                : formatNqNumber(value, {
                                                                      exact: true,
                                                                  }),
                                                    },
                                                    {
                                                        title: t('pages:resources.threads'),
                                                        dataIndex: 'liveThreads',
                                                        align: 'right',
                                                        render: (value: number | null) =>
                                                            value ?? '—',
                                                    },
                                                ]}
                                            />
                                        </>
                                    ),
                                },
                            ]}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function ResourceChart({samples, kind}: {samples: JvmResourceSample[]; kind: 'cpu' | 'memory'}) {
    const {t, i18n} = useTranslation('pages');
    const option = useMemo(() => {
        const base = buildNqLineChartBaseOption();
        const keys =
            kind === 'cpu'
                ? (['processCpuPercent'] as const)
                : (['heapUsedBytes', 'heapCommittedBytes'] as const);
        const names =
            kind === 'cpu'
                ? [t('pages:resources.cpu')]
                : [t('pages:resources.heapUsed'), t('pages:resources.heapCommitted')];
        return {
            ...base,
            animation: false,
            grid: {...base.grid, top: 20, bottom: 24},
            xAxis: {
                ...base.xAxis,
                type: 'time',
                boundaryGap: false,
                splitNumber: 3,
                axisLabel: {
                    ...base.xAxis.axisLabel,
                    hideOverlap: true,
                    formatter: (value: number) =>
                        new Date(value).toLocaleTimeString(i18n.resolvedLanguage, {
                            hour: '2-digit',
                            minute: '2-digit',
                            second: '2-digit',
                            hour12: false,
                        }),
                },
            },
            yAxis: {...base.yAxis, min: 0, max: kind === 'cpu' ? 100 : undefined},
            tooltip: {
                ...base.tooltip,
                confine: true,
                backgroundColor: 'rgba(5,17,29,.28)',
                hideDelay: 0,
                transitionDuration: 0,
                valueFormatter: (value: unknown) =>
                    value == null ? '—' : formatNqNumber(Number(value), {precision: 2}),
                axisPointer: {
                    ...base.tooltip.axisPointer,
                    label: {
                        formatter: (params: {value: number}) =>
                            formatDateTime(new Date(params.value).toISOString()),
                    },
                },
            },
            series: keys.map((key, index) => ({
                name: names[index],
                type: 'line',
                smooth: false,
                connectNulls: false,
                showSymbol: samples.length < 3,
                data: samples.map((sample) => [
                    Date.parse(sample.measuredAt),
                    kind === 'cpu' ? sample.processCpuPercent : bytesToMiB(sample[key]),
                ]),
                lineStyle: {
                    width: index === 0 ? 2 : 1,
                    color: index === 0 ? nqColor.primary : nqColor.textTertiary,
                },
                itemStyle: {color: index === 0 ? nqColor.primary : nqColor.textTertiary},
            })),
        };
    }, [samples, kind, t, i18n.resolvedLanguage]);
    const ref = useEChart(option);
    return (
        <div
            ref={ref}
            role="img"
            aria-label={t(
                kind === 'cpu' ? 'pages:resources.cpuChart' : 'pages:resources.memoryChart',
            )}
            className="nq-chart"
            style={{height: 230}}
            onMouseLeave={() => {
                if (ref.current)
                    echarts.getInstanceByDom(ref.current)?.dispatchAction({type: 'hideTip'});
            }}
        />
    );
}
