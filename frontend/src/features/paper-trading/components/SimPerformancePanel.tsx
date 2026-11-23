import {Alert, Button, Card, DatePicker, Form, Space, Spin, Table, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useEffect} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import dayjs, {type Dayjs} from 'dayjs';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {useAuthStore} from '@/store/auth-store';
import {NqMetricCard} from '@/components/nq/NqMetricCard';
import {NqAmountText} from '@/components/nq/NqNumericText';
import {EquityCurveChart} from '@/features/accounts/components/AccountEquityChart';
import {PerformanceStatisticsCards} from '@/features/accounts/components/PerformanceStatisticsCards';
import {decimalDisplay} from '@/utils/decimal-display';
import {formatDateTime} from '@/utils/formatters';
import {confirmTimeWithoutSubmittingForm, endAfterStartRule} from '@/utils/time-range';
import type {AppApiError} from '@/types/api';
import {simAnalysisApi} from '../api/analysis';
import {accountValuationCurrency} from '@/features/preferences/currencies';
import {PaperEquitySnapshotAction} from './PaperEquitySnapshotAction';

/** URL与实例共同决定统计范围；切换对象或读取失败不沿用上一对象的指标，也不发起任何推进操作。 */
export function SimPerformancePanel({
    paperRunId,
    kind,
}: {
    paperRunId: string;
    kind: 'portfolio' | 'reviews';
}) {
    const {t} = useTranslation();
    const userId = useAuthStore((s) => s.currentUser?.userId);
    const [params, setParams] = useSearchParams();
    const [form] = Form.useForm<{from: Dayjs; to: Dayjs}>();
    const from = params.get('simFrom'),
        to = params.get('simTo');
    const valid =
        (from === null && to === null) ||
        (Boolean(from && to) &&
            dayjs(from).isValid() &&
            dayjs(to).isValid() &&
            dayjs(to).isAfter(dayjs(from)) &&
            dayjs(to).diff(dayjs(from), 'day', true) <= 366);
    const query = useQuery({
        queryKey: paperTradingQueryKeys.performance(userId, paperRunId, from, to),
        queryFn: ({signal}) => simAnalysisApi.performance(paperRunId, from, to, signal),
        enabled: Boolean(userId && valid),
        retry: false,
        refetchInterval: 30_000,
    });
    const data =
        !query.isError && valid && query.data?.paperRunId === paperRunId ? query.data : null;
    const valuationCurrency = data ? accountValuationCurrency(data) : 'USDT';
    useEffect(() => {
        form.setFieldsValue({
            from: dayjs(from ?? data?.coverageStart ?? undefined),
            to: dayjs(to ?? data?.coverageEnd ?? undefined),
        });
    }, [form, from, to, data?.coverageStart, data?.coverageEnd]);
    const reset = () => {
        // URL可能已经是全部范围；仍须清掉尚未提交的逆序草稿及其错误，不能等待依赖变化才重置表单。
        form.resetFields();
        if (data?.coverageStart && data.coverageEnd)
            form.setFieldsValue({from: dayjs(data.coverageStart), to: dayjs(data.coverageEnd)});
        const next = new URLSearchParams(params);
        next.delete('simFrom');
        next.delete('simTo');
        setParams(next);
    };
    const reason = (status: string) =>
        t(`performanceStatistics.states.${status}`, {
            defaultValue: t('performanceStatistics.unavailable'),
        });
    const percentage = (v: string | null, signed = false) =>
        v === null ? '—' : decimalDisplay(v, 4, 2, signed) + '%';
    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t(
                kind === 'portfolio'
                    ? 'performanceStatistics.historyTitle'
                    : 'performanceStatistics.outcomeTitle',
            )}
            data-testid="sim-performance-panel"
            extra={
                <Button
                    loading={query.isFetching}
                    disabled={!valid}
                    onClick={() => void query.refetch()}
                >
                    {t('pages:simAnalysis.refresh')}
                </Button>
            }
        >
            <Form
                form={form}
                layout="inline"
                className="nq-account-range"
                onFinish={(range) => {
                    const next = new URLSearchParams(params);
                    next.set('simFrom', range.from.toISOString());
                    next.set('simTo', range.to.toISOString());
                    setParams(next);
                }}
            >
                <Form.Item
                    name="from"
                    label={t('accountPerformance.from')}
                    rules={[
                        {required: true},
                        {
                            validator: (_, v: Dayjs) =>
                                v && form.getFieldValue('to')?.diff(v, 'day', true) > 366
                                    ? Promise.reject(new Error(t('accountPerformance.rangeLimit')))
                                    : Promise.resolve(),
                        },
                    ]}
                >
                    <DatePicker
                        showTime={{format: 'HH:mm:ss'}}
                        format="YYYY-MM-DD HH:mm:ss"
                        allowClear={false}
                        onKeyDown={confirmTimeWithoutSubmittingForm}
                    />
                </Form.Item>
                <Form.Item
                    name="to"
                    label={t('accountPerformance.to')}
                    dependencies={['from']}
                    rules={[{required: true}, endAfterStartRule('from')]}
                >
                    <DatePicker
                        showTime={{format: 'HH:mm:ss'}}
                        format="YYYY-MM-DD HH:mm:ss"
                        allowClear={false}
                        onKeyDown={confirmTimeWithoutSubmittingForm}
                    />
                </Form.Item>
                <Form.Item>
                    <Space wrap>
                        <Button htmlType="submit" loading={query.isFetching}>
                            {t('accountPerformance.apply')}
                        </Button>
                        <Button onClick={reset}>{t('performanceStatistics.allEvents')}</Button>
                    </Space>
                </Form.Item>
            </Form>
            {!valid && <Alert type="error" message={t('accountPerformance.invalidRange')} />}
            {valid && query.isPending && <Spin />}
            {query.isError && (
                <Alert
                    type="error"
                    message={formatApiError(query.error as AppApiError)}
                    action={
                        <Button onClick={() => void query.refetch()}>{t('pages:retry')}</Button>
                    }
                />
            )}
            {data && (
                <>
                    <Typography.Paragraph type="secondary">
                        {t(
                            data.timeBasis === 'REPLAY_EVENT_TIME'
                                ? 'performanceStatistics.replayMeaning'
                                : 'performanceStatistics.actualMeaning',
                        )}
                    </Typography.Paragraph>
                    <div className="nq-account-coverage">
                        <Typography.Text>
                            {t('accountPerformance.coverage')}: {formatDateTime(data.coverageStart)}{' '}
                            — {formatDateTime(data.coverageEnd)}
                        </Typography.Text>
                        <Typography.Text type="secondary">
                            {' '}
                            · {data.samples.length} {t('accountPerformance.samples')} ·{' '}
                            {reason(data.status)}
                        </Typography.Text>
                    </div>
                    {data.tradesAfterCoverage > 0 && (
                        <Alert
                            type="warning"
                            message={t('performanceStatistics.afterCoverage', {
                                count: data.tradesAfterCoverage,
                            })}
                        />
                    )}
                    {kind === 'portfolio' ? (
                        <>
                            <div className="sim-analysis-metrics">
                                <NqMetricCard
                                    label={t('accountPerformance.pnl')}
                                    value={<NqAmountText exact value={data.pnl} />}
                                    footer={valuationCurrency}
                                />
                                <NqMetricCard
                                    label={t('accountPerformance.return')}
                                    value={percentage(data.returnRatio, true)}
                                />
                                <NqMetricCard
                                    label={t('accountPerformance.drawdown')}
                                    value={<NqAmountText exact value={data.maxDrawdown} />}
                                    footer={valuationCurrency}
                                />
                                <NqMetricCard
                                    label={t('accountPerformance.drawdownRatio')}
                                    value={percentage(data.maxDrawdownRatio)}
                                />
                            </div>
                            {data.samples.length > 0 && (
                                <EquityCurveChart
                                    samples={data.samples.map((s) => ({
                                        sampledAt: s.eventAt,
                                        equity: s.equity,
                                    }))}
                                    gapSeconds={data.sharpe.samplingSeconds * 2}
                                    labelKey="performanceStatistics.chartLabel"
                                />
                            )}
                            <Table
                                size="small"
                                rowKey="eventAt"
                                dataSource={data.samples}
                                pagination={{pageSize: 10}}
                                scroll={{x: 950}}
                                columns={[
                                    {
                                        title: t('performanceStatistics.eventTime'),
                                        dataIndex: 'eventAt',
                                        render: formatDateTime,
                                        width: 210,
                                    },
                                    {
                                        title: t('performanceStatistics.markSource'),
                                        dataIndex: 'markType',
                                        render: (v: string) =>
                                            t(`performanceStatistics.markTypes.${v}`),
                                        width: 150,
                                    },
                                    {
                                        title: t('performanceStatistics.markPrice'),
                                        dataIndex: 'markPrice',
                                        render: (v: string) => <NqAmountText exact value={v} />,
                                        width: 160,
                                    },
                                    {
                                        title: t('pages:simFact_cash'),
                                        dataIndex: 'cash',
                                        render: (v: string) => <NqAmountText exact value={v} />,
                                        width: 160,
                                    },
                                    {
                                        title: t('pages:simFact_positionQuantity'),
                                        dataIndex: 'positionQuantity',
                                        render: (v: string) => <NqAmountText exact value={v} />,
                                        width: 160,
                                    },
                                    {
                                        title:
                                            t('accountPerformance.equity') +
                                            ' · ' +
                                            valuationCurrency,
                                        dataIndex: 'equity',
                                        render: (v: string) => <NqAmountText exact value={v} />,
                                        width: 180,
                                    },
                                ]}
                            />
                            <PaperEquitySnapshotAction
                                key={`${userId}/${paperRunId}`}
                                paperRunId={paperRunId}
                                sample={
                                    !from &&
                                    !to &&
                                    data.samples.at(-1)?.valuationStatus === 'VERIFIED'
                                        ? data.samples.at(-1)!
                                        : null
                                }
                            />
                        </>
                    ) : (
                        <PerformanceStatisticsCards sharpe={data.sharpe} outcomes={data.outcomes} />
                    )}
                    <Typography.Text type="secondary">
                        {t('pages:simLedgerAsOf')}: {formatDateTime(data.ledgerAsOf)}
                    </Typography.Text>
                </>
            )}
        </Card>
    );
}
