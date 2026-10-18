import {Alert, Button, Card, Collapse, DatePicker, Form, Space, Table, Tag, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import dayjs, {type Dayjs} from 'dayjs';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import {NqEmptyState, NqErrorState, NqMetricCard} from '@/components/nq';
import {confirmTimeWithoutSubmittingForm, endAfterStartRule} from '@/utils/time-range';
import {formatDateTime} from '@/utils/formatters';
import {formatNqNumber} from '@/nq-design-system/format/nqFormat';
import {decimalDisplay} from '@/utils/decimal-display';
import type {AppApiError} from '@/types/api';
import {readAccountPerformance, parseAccountValuationSource} from '../api/performance';
import {AccountEquityChart} from './AccountEquityChart';
import {PerformanceStatisticsCards} from './PerformanceStatisticsCards';
import type {AccountEquitySample} from '../types/performance';
import {accountValuationCurrency} from '@/features/preferences/currencies';

/** 单账户真实采样视图；用户、账户及时间参与缓存身份，缺失/过期数据保留状态，不汇总独立SIM。 */
export function AccountPerformancePanel() {
    const {t} = useTranslation();
    const [params, setParams] = useSearchParams();
    const [form] = Form.useForm<{from: Dayjs; to: Dayjs}>();
    const accountId = useAccountContextStore((s) => s.selectedExchangeAccountId);
    const alias = useAccountContextStore((s) => s.accountAlias);
    const userId = useAuthStore((s) => s.currentUser?.userId);
    const initial = useMemo(() => ({from: dayjs().subtract(1, 'day'), to: dayjs()}), []);
    const from = params.get('accountFrom') ?? initial.from.toISOString();
    const to = params.get('accountTo') ?? initial.to.toISOString();
    const rangeValid =
        dayjs(from).isValid() &&
        dayjs(to).isValid() &&
        dayjs(to).isAfter(dayjs(from)) &&
        dayjs(to).diff(dayjs(from), 'day', true) <= 366;
    useEffect(() => {
        form.setFieldsValue({
            from: dayjs(from).isValid() ? dayjs(from) : initial.from,
            to: dayjs(to).isValid() ? dayjs(to) : initial.to,
        });
    }, [form, from, to, initial]);
    // URL恢复的时间同样必须通过范围校验，避免绕过表单后发起逆序或无界历史查询。
    const query = useQuery({
        queryKey: ['account-performance', userId, accountId, from, to, 'USDT'],
        queryFn: ({signal}) => readAccountPerformance(accountId!, from, to, signal),
        enabled: Boolean(userId && accountId && rangeValid),
    });
    const summary = query.data?.summary;
    const valuationCurrency = summary ? accountValuationCurrency(summary) : 'USDT';
    const latest = summary?.samples.at(-1);
    const stale =
        latest &&
        dayjs(to).isAfter(dayjs().subtract(30, 'minute')) &&
        Date.now() - Date.parse(latest.sampledAt) > 1800000;
    const amount = (value: string | null | undefined) => decimalDisplay(value, 8);
    const ratio = (value: string | null | undefined, signed = false) =>
        value == null ? '—' : decimalDisplay(value, 4, 2, signed) + '%';
    const columns = [
        {
            title: t('accountPerformance.sampleTime'),
            dataIndex: 'sampledAt',
            width: 190,
            render: formatDateTime,
        },
        {
            title: t('accountPerformance.equity') + ' · ' + valuationCurrency,
            dataIndex: 'equity',
            width: 180,
            render: (v: string | null) => formatNqNumber(v, {exact: true}),
            align: 'right' as const,
        },
        {
            title: t('accountPerformance.cumulativeFlow'),
            dataIndex: 'cumulativeNetFlow',
            width: 180,
            render: (v: string | null) => formatNqNumber(v, {exact: true}),
            align: 'right' as const,
        },
        {
            title: t('accountPerformance.dataStatus'),
            dataIndex: 'status',
            width: 160,
            render: (v: string) => t('accountPerformance.states.' + v),
        },
    ];
    return (
        <Card
            className="page-section nq-dashboard__account"
            bordered={false}
            data-testid="account-performance-panel"
            title={
                <Space wrap>
                    <span>{t('accountPerformance.title')}</span>
                    <Tag>{alias ?? t('shell.chooseAccount')}</Tag>
                    <Tag>
                        {t('pages:currencyRegistry.valuationLabel', {currency: valuationCurrency})}
                    </Tag>
                </Space>
            }
            extra={
                <Button
                    size="small"
                    onClick={() => {
                        form.setFieldsValue({from: dayjs(from), to: dayjs()});
                        const next = new URLSearchParams(params);
                        next.set('accountTo', dayjs().toISOString());
                        setParams(next);
                    }}
                >
                    {t('accountPerformance.refresh')}
                </Button>
            }
        >
            <Form
                form={form}
                layout="inline"
                className="nq-account-range"
                initialValues={{from: dayjs(from), to: dayjs(to)}}
                onFinish={(value) => {
                    const next = new URLSearchParams(params);
                    next.set('accountFrom', value.from.toISOString());
                    next.set('accountTo', value.to.toISOString());
                    setParams(next);
                }}
            >
                <Form.Item
                    name="from"
                    label={t('accountPerformance.from')}
                    rules={[
                        {required: true},
                        {
                            validator: (_, value: Dayjs) =>
                                value && form.getFieldValue('to')?.diff(value, 'day', true) > 366
                                    ? Promise.reject(new Error(t('accountPerformance.rangeLimit')))
                                    : Promise.resolve(),
                        },
                    ]}
                >
                    <DatePicker
                        onKeyDown={confirmTimeWithoutSubmittingForm}
                        showTime={{format: 'HH:mm:ss'}}
                        format="YYYY-MM-DD HH:mm:ss"
                        allowClear={false}
                    />
                </Form.Item>
                <Form.Item
                    name="to"
                    label={t('accountPerformance.to')}
                    dependencies={['from']}
                    rules={[{required: true}, endAfterStartRule('from')]}
                >
                    <DatePicker
                        onKeyDown={confirmTimeWithoutSubmittingForm}
                        showTime={{format: 'HH:mm:ss'}}
                        format="YYYY-MM-DD HH:mm:ss"
                        allowClear={false}
                    />
                </Form.Item>
                <Form.Item>
                    <Button htmlType="submit" loading={query.isFetching}>
                        {t('accountPerformance.apply')}
                    </Button>
                </Form.Item>
            </Form>
            {!accountId ? (
                <NqEmptyState description={t('accountPerformance.chooseAccount')} />
            ) : !rangeValid ? (
                <Alert type="error" message={t('accountPerformance.invalidRange')} />
            ) : query.error ? (
                <NqErrorState title={t('queryFailed')} error={query.error as AppApiError} />
            ) : (
                <>
                    <div className="nq-status-strip nq-account-metrics">
                        <NqMetricCard
                            label={t('accountPerformance.equity')}
                            value={amount(summary?.finalEquity)}
                            footer={valuationCurrency}
                            loading={query.isPending}
                        />
                        <NqMetricCard
                            label={t('accountPerformance.pnl')}
                            value={amount(summary?.pnl)}
                            footer={valuationCurrency}
                            loading={query.isPending}
                        />
                        <NqMetricCard
                            label={t('accountPerformance.return')}
                            value={ratio(summary?.returnRatio, true)}
                            loading={query.isPending}
                        />
                        <NqMetricCard
                            label={t('accountPerformance.drawdown')}
                            value={amount(summary?.maxDrawdown)}
                            footer={valuationCurrency}
                            loading={query.isPending}
                        />
                        <NqMetricCard
                            label={t('accountPerformance.drawdownRatio')}
                            value={ratio(summary?.maxDrawdownRatio)}
                            loading={query.isPending}
                        />
                    </div>
                    {query.data && (
                        <PerformanceStatisticsCards
                            sharpe={query.data.sharpe}
                            outcomes={query.data.outcomes}
                        />
                    )}
                    {query.isSuccess &&
                        (!summary?.samples.length ? (
                            <NqEmptyState description={t('accountPerformance.noHistory')} />
                        ) : (
                            <>
                                <div className="nq-account-coverage">
                                    <Typography.Text type="secondary">
                                        {t('accountPerformance.coverage')}:{' '}
                                        {formatDateTime(summary.coverageStart)} —{' '}
                                        {formatDateTime(summary.coverageEnd)} ·{' '}
                                        {summary.samples.length} {t('accountPerformance.samples')}
                                    </Typography.Text>
                                    <Tag
                                        color={
                                            stale || summary.status !== 'AVAILABLE'
                                                ? 'orange'
                                                : 'blue'
                                        }
                                    >
                                        {stale
                                            ? t('accountPerformance.stale')
                                            : t('accountPerformance.states.' + summary.status)}
                                    </Tag>
                                </div>
                                {query.data?.historyLimited && (
                                    <Alert
                                        type="warning"
                                        message={t('accountPerformance.limited')}
                                    />
                                )}
                                {summary.status !== 'AVAILABLE' && (
                                    <Typography.Paragraph type="secondary">
                                        {t('accountPerformance.partial')}
                                    </Typography.Paragraph>
                                )}
                                {summary.samples.some((sample) => sample.equity !== null) ? (
                                    <AccountEquityChart key={accountId} samples={summary.samples} />
                                ) : (
                                    <NqEmptyState
                                        description={t('accountPerformance.noValuation')}
                                    />
                                )}
                            </>
                        ))}
                    <Collapse
                        size="small"
                        items={[
                            {
                                key: 'method',
                                label: t('accountPerformance.methodTitle'),
                                children: (
                                    <Typography.Paragraph>
                                        {t('accountPerformance.method')}
                                    </Typography.Paragraph>
                                ),
                            },
                            {
                                key: 'samples',
                                label: t('accountPerformance.details'),
                                children: (
                                    <Table<AccountEquitySample>
                                        size="small"
                                        columns={columns}
                                        rowKey="sampleId"
                                        dataSource={summary?.samples ?? []}
                                        scroll={{x: 760}}
                                        pagination={{pageSize: 10}}
                                        expandable={{
                                            expandedRowRender: (sample) => (
                                                <pre
                                                    style={{
                                                        whiteSpace: 'pre-wrap',
                                                        overflowWrap: 'anywhere',
                                                    }}
                                                >
                                                    {JSON.stringify(
                                                        parseAccountValuationSource(
                                                            sample.sourceJson,
                                                        ),
                                                        null,
                                                        2,
                                                    )}
                                                </pre>
                                            ),
                                        }}
                                    />
                                ),
                            },
                        ]}
                    />
                </>
            )}
        </Card>
    );
}
