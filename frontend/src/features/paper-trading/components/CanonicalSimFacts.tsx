import {Alert, Button, Collapse, Descriptions, List, Space, Table, Tabs, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {Link, useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {paperTradingQueryKeys, schedulerQueryKeys} from '@/api/query-keys';
import {NqAmountText} from '@/components/nq';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import {schedulerApi} from '@/features/scheduler/api/scheduler';
import type {StrategySimFacts} from '@/features/paper-trading/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';
import {CanonicalSimOrderDetails} from './CanonicalSimOrderDetails';
import {ledgerPostingRole} from '../ledger-posting-role';
import {feePaymentStatusKey} from '@/features/backtests/fee-source';
import {parseFrozenSimFeeCharge} from '../analysis-response';
import {formatRatioPercent} from '@/utils/formatters';

export function useCanonicalSimFacts(paperRunId: string | null) {
    return useQuery({
        queryKey: paperTradingQueryKeys.canonicalFacts(paperRunId ?? ''),
        queryFn: () => paperTradingApi.strategySimFacts(paperRunId!),
        enabled: Boolean(paperRunId),
        refetchInterval: 30_000,
        retry: false,
    });
}

/** 三个页面共用服务端经济投影；账户 ID 不转换成 exchange account 上下文。 */
export function CanonicalSimFacts({
    paperRunId,
    details = false,
}: {
    paperRunId: string;
    details?: boolean;
}) {
    const {t} = useTranslation('pages');
    const [params, setParams] = useSearchParams();
    const locatedOrderId = params.get('orderId');
    const factsQuery = useCanonicalSimFacts(paperRunId);
    const run = useQuery({
        queryKey: paperTradingQueryKeys.detail(paperRunId),
        queryFn: () => paperTradingApi.detail(paperRunId),
        refetchInterval: 30_000,
        retry: false,
    });
    const decisions = useQuery({
        queryKey: paperTradingQueryKeys.decisions(paperRunId),
        queryFn: () => paperTradingApi.strategySimDecisions(paperRunId),
        refetchInterval: 30_000,
        retry: false,
    });
    const continuous = useQuery({
        queryKey: paperTradingQueryKeys.continuous(paperRunId),
        queryFn: ({signal}) => paperTradingApi.continuousSimStatus(paperRunId, signal),
        refetchInterval: 30_000,
        retry: false,
    });
    const scheduler = useQuery({
        queryKey: schedulerQueryKeys.list(),
        queryFn: schedulerApi.list,
        refetchInterval: 30_000,
        retry: false,
    });
    const facts =
        !factsQuery.isError && factsQuery.data?.paperRunId === paperRunId ? factsQuery.data : null;
    const discount = facts?.discountAsset;
    const discountPriceUnavailable = discount?.status === 'PRICE_UNAVAILABLE';
    const number = (value: unknown) => (
        <NqAmountText exact value={value as string | number | null} />
    );
    return (
        <Space direction="vertical" style={{width: '100%'}} data-testid="canonical-sim-facts">
            <Typography.Text strong>{t('pages:simCanonical')}</Typography.Text>
            {factsQuery.isError && (
                <Alert type="error" showIcon message={t('pages:simFactsUnavailable')} />
            )}
            {facts && (
                <>
                    <Descriptions size="small" bordered column={{xs: 1, md: 2, xl: 3}}>
                        <Descriptions.Item label={t('pages:runStatus')}>
                            {run.isError
                                ? t('pages:simFactsUnavailable')
                                : (run.data?.status ?? '—')}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:strategySimBudget')}>
                            {number(facts.initialBudget)} USDT
                        </Descriptions.Item>
                        {(['cash', 'positionQuantity', 'equity', 'pnl', 'markPrice'] as const).map(
                            (key) => (
                                <Descriptions.Item key={key} label={t(`pages:simFact_${key}`)}>
                                    {number(
                                        (discountPriceUnavailable &&
                                            ['equity', 'pnl'].includes(key)) ||
                                            (['markPrice', 'equity', 'pnl'].includes(key) &&
                                                !facts.markAsOf)
                                            ? null
                                            : (facts.exactValues?.[key] ?? facts[key]),
                                    )}{' '}
                                    {key === 'positionQuantity' ? 'BTC' : 'USDT'}
                                </Descriptions.Item>
                            ),
                        )}
                        <Descriptions.Item label={t('pages:simMarkAsOf')}>
                            {formatDateTime(facts.markAsOf)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:simLedgerAsOf')}>
                            {formatDateTime(facts.ledgerAsOf)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:simPositionAsOf')}>
                            {formatDateTime(facts.positionAsOf)}
                        </Descriptions.Item>
                    </Descriptions>
                    {discount && (
                        <>
                            {discountPriceUnavailable && (
                                <Alert
                                    type="warning"
                                    showIcon
                                    message={t('pages:discountFee.priceUnavailable')}
                                />
                            )}
                            <Typography.Text type="secondary">
                                {t('pages:discountFee.valuationConversion')}
                            </Typography.Text>
                            <Descriptions
                                size="small"
                                bordered
                                title={t('pages:discountFee.assetTitle', {
                                    asset: discount.assetCurrency,
                                })}
                                column={{xs: 1, md: 2, xl: 3}}
                            >
                                <Descriptions.Item label={t('pages:discountFee.budgetFraction')}>
                                    {formatRatioPercent(discount.budgetFraction)}
                                </Descriptions.Item>
                                {(
                                    ['initialQuantity', 'balance', 'reserved', 'available'] as const
                                ).map((key) => (
                                    <Descriptions.Item
                                        key={key}
                                        label={t(`pages:discountFee.${key}`)}
                                    >
                                        {number(discount[key])} {discount.assetCurrency}
                                    </Descriptions.Item>
                                ))}
                                {(
                                    [
                                        'initialQuoteValue',
                                        'initialCash',
                                        'quoteValue',
                                        'conversionPrice',
                                    ] as const
                                ).map((key) => (
                                    <Descriptions.Item
                                        key={key}
                                        label={t(`pages:discountFee.${key}`)}
                                    >
                                        {number(
                                            discountPriceUnavailable &&
                                                ['quoteValue', 'conversionPrice'].includes(key)
                                                ? null
                                                : discount[key],
                                        )}{' '}
                                        USDT
                                    </Descriptions.Item>
                                ))}
                                <Descriptions.Item label={t('pages:discountFee.observedAt')}>
                                    {formatDateTime(discount.observedAt)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:discountFee.visibleAt')}>
                                    {formatDateTime(discount.visibleAt)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:discountFee.valuationAt')}>
                                    {formatDateTime(discount.valuationAt)}
                                </Descriptions.Item>
                            </Descriptions>
                        </>
                    )}
                    <Typography.Text type="secondary">
                        {t('pages:simValuationExplanation')}
                    </Typography.Text>
                    <Typography.Text type="secondary">
                        {t('pages:simBudgetMeaning')}
                    </Typography.Text>
                    <Space wrap>
                        <Link to={`/trading?paperRunId=${encodeURIComponent(paperRunId)}`}>
                            {t('pages:strategySimOpenTrading')}
                        </Link>
                        <Link
                            to={`/paper-trading/runs?paperRunId=${encodeURIComponent(paperRunId)}`}
                        >
                            {t('pages:simOpenPaper')}
                        </Link>
                        <Link to="/system/scheduler">{t('pages:simOpenScheduler')}</Link>
                    </Space>
                </>
            )}
            <CanonicalSimOrderDetails
                paperRunId={paperRunId}
                orderId={locatedOrderId}
                onClose={() => {
                    const next = new URLSearchParams(params);
                    next.delete('orderId');
                    setParams(next, {replace: true});
                }}
            />
            {facts && details && (
                <Tabs
                    key={paperRunId + (locatedOrderId ?? '')}
                    defaultActiveKey="orders"
                    items={(
                        ['orders', 'trades', 'ledgerEntries', 'riskEvents', 'positions'] as const
                    ).map((kind) => ({
                        key: kind,
                        label: t(`pages:simTable_${kind}`),
                        children: (
                            <CanonicalFactTable
                                kind={kind}
                                facts={facts}
                                locatedOrderId={locatedOrderId}
                            />
                        ),
                    }))}
                />
            )}
            <Collapse
                items={[
                    {
                        key: 'diagnostics',
                        label: t('pages:validationAdvancedDiagnostics'),
                        children: (
                            <Space direction="vertical" style={{width: '100%'}}>
                                {facts && (
                                    <Descriptions bordered column={1} size="small">
                                        <Descriptions.Item label={t('pages:paperRunId')}>
                                            <Typography.Text copyable>{paperRunId}</Typography.Text>
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:accountId')}>
                                            <Typography.Text copyable>
                                                {String(facts.canonicalAccountId)}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:strategySimVersion')}>
                                            <Typography.Text copyable>
                                                {facts.strategyVersionId}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:publishId')}>
                                            <Typography.Text copyable>
                                                {facts.publishId}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:strategySimInput')}>
                                            <Typography.Text copyable>
                                                {facts.inputSha256}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                        {discount && (
                                            <>
                                                <Descriptions.Item
                                                    label={t('pages:discountFee.datasetIdentity')}
                                                >
                                                    <Typography.Text copyable>
                                                        {discount.datasetId}
                                                    </Typography.Text>
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('pages:discountFee.snapshotIdentity')}
                                                >
                                                    <Typography.Text copyable>
                                                        {discount.snapshotId}
                                                    </Typography.Text>
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('pages:discountFee.snapshotDigest')}
                                                >
                                                    <Typography.Text
                                                        copyable
                                                        style={{overflowWrap: 'anywhere'}}
                                                    >
                                                        {discount.snapshotSha256}
                                                    </Typography.Text>
                                                </Descriptions.Item>
                                                <Descriptions.Item
                                                    label={t('pages:discountFee.sourceDigest')}
                                                >
                                                    <Typography.Text
                                                        copyable
                                                        style={{overflowWrap: 'anywhere'}}
                                                    >
                                                        {discount.priceSourceSha256 ?? '—'}
                                                    </Typography.Text>
                                                </Descriptions.Item>
                                            </>
                                        )}
                                    </Descriptions>
                                )}
                                <Typography.Text type="secondary">
                                    {t('pages:simReadBounds')}
                                </Typography.Text>
                                <Typography.Paragraph copyable style={{overflowWrap: 'anywhere'}}>
                                    {run.isError ? '—' : (run.data?.datasetSnapshotJson ?? '—')}
                                </Typography.Paragraph>
                                {!continuous.isError && continuous.data && (
                                    <Descriptions size="small" bordered column={{xs: 1, md: 2}}>
                                        <Descriptions.Item label={t('pages:continuousSimStatus')}>
                                            {continuous.data.status}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:simContinuousIdentity')}>
                                            <Typography.Text copyable>
                                                {continuous.data.paperRunId}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:continuousSimLastBar')}>
                                            {formatDateTime(continuous.data.lastProcessedBar)}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:continuousSimNextBar')}>
                                            {formatDateTime(continuous.data.nextExpectedBar)}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:continuousSimGapStart')}>
                                            {formatDateTime(continuous.data.gapStartBar)}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:continuousSimLastAvailable')}
                                        >
                                            {formatDateTime(continuous.data.lastObservedBar)}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:continuousSimLastPoll')}>
                                            {formatDateTime(continuous.data.lastPollAt)}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('pages:continuousSimReason')}>
                                            {continuous.data.blockReason ?? '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:continuousSimLastDecision')}
                                        >
                                            {continuous.data.lastDecisionId ?? '—'} /{' '}
                                            {continuous.data.lastDecisionStatus ?? '—'}
                                        </Descriptions.Item>
                                    </Descriptions>
                                )}
                                {continuous.isError && (
                                    <Typography.Text type="secondary">
                                        {t('pages:simContinuousUnavailable')}
                                    </Typography.Text>
                                )}
                                {scheduler.isError && (
                                    <Alert
                                        type="warning"
                                        message={t('pages:simSchedulerUnavailable')}
                                    />
                                )}
                                {!scheduler.isError &&
                                    scheduler.data
                                        ?.filter((job) =>
                                            ['CONTINUOUS_SIM_POLL', 'PAPER_MATCHING'].includes(
                                                job.jobKey,
                                            ),
                                        )
                                        .map((job) => (
                                            <Typography.Paragraph key={job.jobKey}>
                                                {job.jobKey}:{' '}
                                                {job.enabled
                                                    ? t('pages:simEnabled')
                                                    : t('pages:simDisabled')}{' '}
                                                · {job.lastStatus} · {job.lastErrorCode ?? '—'}
                                            </Typography.Paragraph>
                                        ))}
                                {decisions.isError && (
                                    <Alert
                                        type="warning"
                                        message={t('pages:simDecisionsUnavailable')}
                                    />
                                )}
                                <List
                                    size="small"
                                    bordered
                                    header={t('pages:strategySimDecisions')}
                                    dataSource={decisions.isError ? [] : (decisions.data ?? [])}
                                    locale={{emptyText: t('pages:strategySimNoDecisions')}}
                                    renderItem={(decision) => {
                                        const charge = parseFrozenSimFeeCharge(
                                            decision.feeChargeJson,
                                        );
                                        return (
                                            <List.Item key={decision.decisionId}>
                                                <Space direction="vertical" size={0}>
                                                    <Typography.Text>
                                                        {decision.status} · {decision.reason} ·{' '}
                                                        {decision.side ?? '—'}{' '}
                                                        {number(decision.quantity)}
                                                    </Typography.Text>
                                                    <Typography.Text>
                                                        {t('pages:simSignalTime')}:{' '}
                                                        {formatDateTime(decision.signalOpenTime)}
                                                    </Typography.Text>
                                                    <Typography.Text copyable>
                                                        {decision.decisionId}
                                                    </Typography.Text>
                                                    <Typography.Text>
                                                        {t('pages:simStrategyRun')}:{' '}
                                                        {decision.strategyRunId ?? '—'}
                                                    </Typography.Text>
                                                    <Typography.Text>
                                                        {t('pages:orderId')}:{' '}
                                                        {decision.orderId ??
                                                            t('pages:simNotProduced')}
                                                    </Typography.Text>
                                                    {charge && (
                                                        <>
                                                            <Typography.Text type="secondary">
                                                                {t(
                                                                    'pages:discountFee.paymentConversion',
                                                                )}
                                                            </Typography.Text>
                                                            <Typography.Text>
                                                                {t(
                                                                    'pages:discountFee.nativeCharge',
                                                                )}
                                                                : {number(charge.amount)}{' '}
                                                                {charge.currency} ·{' '}
                                                                {t('pages:discountFee.quoteValue')}:{' '}
                                                                {number(charge.quoteValue)} USDT
                                                            </Typography.Text>
                                                            <Typography.Text>
                                                                {t(
                                                                    'pages:discountFee.paymentResult',
                                                                )}
                                                                :{' '}
                                                                {feePaymentStatusKey(
                                                                    charge.paymentStatus,
                                                                )
                                                                    ? t(
                                                                          `pages:${feePaymentStatusKey(charge.paymentStatus)}`,
                                                                      )
                                                                    : charge.paymentStatus}{' '}
                                                                · {t('pages:discountFee.reserved')}:{' '}
                                                                {number(charge.positiveReserve)}{' '}
                                                                {charge.currency}
                                                            </Typography.Text>
                                                            {charge.quotePerAsset && (
                                                                <Typography.Text>
                                                                    {t(
                                                                        'pages:discountFee.conversionPrice',
                                                                    )}
                                                                    : {number(charge.quotePerAsset)}{' '}
                                                                    USDT/BNB ·{' '}
                                                                    {t(
                                                                        'pages:discountFee.observedAt',
                                                                    )}
                                                                    :{' '}
                                                                    {formatDateTime(
                                                                        charge.observedAt,
                                                                    )}
                                                                </Typography.Text>
                                                            )}
                                                            {charge.visibleAt && (
                                                                <Typography.Text>
                                                                    {t(
                                                                        'pages:discountFee.visibleAt',
                                                                    )}
                                                                    :{' '}
                                                                    {formatDateTime(
                                                                        charge.visibleAt,
                                                                    )}
                                                                </Typography.Text>
                                                            )}
                                                            {charge.sourceSha256 && (
                                                                <Typography.Text
                                                                    copyable
                                                                    style={{
                                                                        overflowWrap: 'anywhere',
                                                                    }}
                                                                >
                                                                    {charge.sourceSha256}
                                                                </Typography.Text>
                                                            )}
                                                        </>
                                                    )}
                                                    {decision.feeChargeJson && !charge && (
                                                        <Typography.Text type="warning">
                                                            {t(
                                                                'pages:discountFee.chargeUnavailable',
                                                            )}
                                                        </Typography.Text>
                                                    )}
                                                    {facts?.orders
                                                        .filter(
                                                            (order) =>
                                                                order.order_id === decision.orderId,
                                                        )
                                                        .map((order) => (
                                                            <Typography.Text
                                                                key={String(order.order_id)}
                                                            >
                                                                {t('pages:simRiskOrderResult')}:{' '}
                                                                {String(order.status)} ·{' '}
                                                                {String(order.reason ?? '—')}
                                                            </Typography.Text>
                                                        ))}
                                                </Space>
                                            </List.Item>
                                        );
                                    }}
                                />
                            </Space>
                        ),
                    },
                ]}
            />
        </Space>
    );
}

export function CanonicalFactTable({
    kind,
    facts,
    locatedOrderId,
}: {
    kind: 'orders' | 'trades' | 'ledgerEntries' | 'riskEvents' | 'positions';
    facts: StrategySimFacts;
    locatedOrderId: string | null;
}) {
    const {t} = useTranslation('pages');
    const [params, setParams] = useSearchParams();
    // 成交费用可能是收费或返佣，币种必须取正式成交字段；不能据交易对或冻结模型猜结算币种。
    // 缺失币种保留为空态，负费用仍由原精确金额组件展示，不换算或取绝对值。
    const keys =
        kind === 'orders'
            ? ['order_id', 'strategy_run_id', 'symbol', 'side', 'qty', 'price', 'status', 'reason']
            : kind === 'trades'
              ? [
                    'symbol',
                    'side',
                    'qty',
                    'price',
                    'fee',
                    'fee_currency',
                    'fee_payment_status',
                    'fee_quote_value',
                    'ts',
                    'trade_id',
                    'order_id',
                ]
              : kind === 'positions'
                ? ['id', 'account_id', 'symbol', 'qty', 'avg_price', 'updated_at']
                : kind === 'riskEvents'
                  ? ['risk_event_id', 'order_id', 'rule_id', 'decision', 'reason', 'created_at']
                  : ['ts', 'currency', 'postingRole', 'delta', 'ref_type', 'ref_id', 'entry_id'];
    const index =
        kind === 'orders'
            ? facts.orders.findIndex((order) => order.order_id === locatedOrderId)
            : -1;
    // 成交的标的、金额和币种先显示，内部标识仍保留复制；列顺序不能改变持久成交的行身份。
    const rowIdentity =
        kind === 'ledgerEntries' ? 'entry_id' : kind === 'trades' ? 'trade_id' : keys[0];
    return (
        <Table
            size="small"
            title={() => t(`pages:simTable_${kind}`)}
            dataSource={facts[kind]}
            rowClassName={(row) => (row.order_id === locatedOrderId ? 'nq-located-object' : '')}
            rowKey={(row) => String(row[rowIdentity])}
            pagination={{pageSize: 10, defaultCurrent: index < 0 ? 1 : Math.floor(index / 10) + 1}}
            scroll={{x: 1000}}
            columns={[
                ...keys.map((key) => ({
                    width: key.endsWith('_id')
                        ? 250
                        : ['ts', 'created_at', 'updated_at'].includes(key)
                          ? 240
                          : 150,
                    title:
                        key === 'fee_payment_status'
                            ? t('pages:discountFee.paymentResult')
                            : key === 'fee_quote_value'
                              ? t('pages:discountFee.quoteValue')
                              : t(`pages:simColumn_${key}`),
                    dataIndex: key,
                    render: (value: unknown, row: Record<string, unknown>) =>
                        key === 'postingRole' ? (
                            t(`pages:ledgerRole_${ledgerPostingRole(row)}`)
                        ) : key === 'fee_payment_status' ? (
                            feePaymentStatusKey(value) ? (
                                t(`pages:${feePaymentStatusKey(value)}`)
                            ) : (
                                String(value ?? '—')
                            )
                        ) : [
                              'qty',
                              'price',
                              'fee',
                              'delta',
                              'avg_price',
                              'fee_quote_value',
                          ].includes(key) ? (
                            <NqAmountText
                                exact
                                value={(row[`exact_${key}`] ?? value) as string | number | null}
                            />
                        ) : (
                            <Typography.Text copyable={key.endsWith('_id')}>
                                {['ts', 'created_at', 'updated_at'].includes(key)
                                    ? formatDateTime(value as string)
                                    : String(value ?? '—')}
                            </Typography.Text>
                        ),
                })),
                ...(kind === 'orders'
                    ? [
                          {
                              title: t('pages:actions'),
                              fixed: 'right' as const,
                              width: 90,
                              render: (_: unknown, row: Record<string, unknown>) => (
                                  <Button
                                      type="link"
                                      onClick={() => {
                                          const next = new URLSearchParams(params);
                                          next.set('orderId', String(row.order_id));
                                          setParams(next);
                                      }}
                                  >
                                      {t('pages:viewDetails')}
                                  </Button>
                              ),
                          },
                      ]
                    : []),
            ]}
        />
    );
}
