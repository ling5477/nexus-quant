import {Alert, Descriptions, List, Space, Table, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {paperTradingQueryKeys, schedulerQueryKeys} from '@/api/query-keys';
import {NqAmountText} from '@/components/nq';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import {schedulerApi} from '@/features/scheduler/api/scheduler';
import type {StrategySimFacts} from '@/features/paper-trading/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

export function useCanonicalSimFacts(paperRunId: string | null) {
    return useQuery({queryKey: paperTradingQueryKeys.canonicalFacts(paperRunId ?? ''),
        queryFn: () => paperTradingApi.strategySimFacts(paperRunId!), enabled: Boolean(paperRunId),
        refetchInterval: 30_000, retry: false});
}

/** 三个页面共用服务端经济投影；账户 ID 不转换成 exchange account 上下文。 */
export function CanonicalSimFacts({paperRunId, details = false}: {paperRunId: string; details?: boolean}) {
    const {t} = useTranslation('pages');
    const factsQuery = useCanonicalSimFacts(paperRunId);
    const run = useQuery({queryKey: paperTradingQueryKeys.detail(paperRunId),
        queryFn: () => paperTradingApi.detail(paperRunId), refetchInterval: 30_000, retry: false});
    const decisions = useQuery({queryKey: paperTradingQueryKeys.decisions(paperRunId),
        queryFn: () => paperTradingApi.strategySimDecisions(paperRunId), refetchInterval: 30_000, retry: false});
    const continuous = useQuery({queryKey: paperTradingQueryKeys.continuous(paperRunId),
        queryFn: () => paperTradingApi.continuousSimStatus(paperRunId), refetchInterval: 30_000, retry: false});
    const scheduler = useQuery({queryKey: schedulerQueryKeys.list(), queryFn: schedulerApi.list,
        refetchInterval: 30_000, retry: false});
    const facts = !factsQuery.isError && factsQuery.data?.paperRunId === paperRunId ? factsQuery.data : null;
    const number = (value: unknown) => <NqAmountText exact value={value as string | number | null}/>;
    return <Space direction="vertical" style={{width: '100%'}} data-testid="canonical-sim-facts">
        <Typography.Text strong>{t('pages:simCanonical')}</Typography.Text>
        <Typography.Text copyable>{paperRunId}</Typography.Text>
        {factsQuery.isError && <Alert type="error" showIcon message={t('pages:simFactsUnavailable')}/>}
        {facts && <>
            <Descriptions size="small" bordered column={1}>
                <Descriptions.Item label={t('pages:runStatus')}>{run.isError ? t('pages:simFactsUnavailable') : run.data?.status ?? '—'}</Descriptions.Item>
                <Descriptions.Item label={t('pages:simDataset')}><Typography.Text copyable style={{overflowWrap: 'anywhere'}}>{run.isError ? '—' : run.data?.datasetSnapshotJson ?? '—'}</Typography.Text></Descriptions.Item>
                <Descriptions.Item label={t('pages:accountId')}><Typography.Text copyable>{String(facts.canonicalAccountId)}</Typography.Text></Descriptions.Item>
                <Descriptions.Item label={t('pages:strategySimVersion')}><Typography.Text copyable>{facts.strategyVersionId}</Typography.Text></Descriptions.Item>
                <Descriptions.Item label={t('pages:publishId')}><Typography.Text copyable>{facts.publishId}</Typography.Text></Descriptions.Item>
                <Descriptions.Item label={t('pages:strategySimInput')}><Typography.Text copyable>{facts.inputSha256}</Typography.Text></Descriptions.Item>
                {(['cash', 'positionQuantity', 'equity', 'pnl', 'markPrice'] as const).map((key) =>
                    <Descriptions.Item key={key} label={t(`pages:simFact_${key}`)}>{number(['markPrice', 'equity', 'pnl'].includes(key) && !facts.markAsOf ? null : facts.exactValues?.[key] ?? facts[key])} {key === 'positionQuantity' ? 'BTC' : 'USDT'}</Descriptions.Item>)}
                <Descriptions.Item label={t('pages:simMarkAsOf')}>{formatDateTime(facts.markAsOf)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:simLedgerAsOf')}>{formatDateTime(facts.ledgerAsOf)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:simPositionAsOf')}>{formatDateTime(facts.positionAsOf)}</Descriptions.Item>
            </Descriptions>
            <Typography.Text type="secondary">{t('pages:simValuationExplanation')}</Typography.Text>
            <Typography.Text type="secondary">{t('pages:simReadBounds')}</Typography.Text>
            <Space wrap>
                <Link to={`/trading?paperRunId=${encodeURIComponent(paperRunId)}`}>{t('pages:strategySimOpenTrading')}</Link>
                <Link to={`/paper-trading/runs?paperRunId=${encodeURIComponent(paperRunId)}`}>{t('pages:simOpenPaper')}</Link>
                <Link to="/system/scheduler">{t('pages:simOpenScheduler')}</Link>
                <Typography.Text>{t('pages:simNavigationIdentity')}</Typography.Text>
            </Space>
        </>}
        {!continuous.isError && continuous.data && <Descriptions size="small" bordered column={{xs: 1, md: 2}}>
            <Descriptions.Item label={t('pages:continuousSimStatus')}>{continuous.data.status}</Descriptions.Item>
            <Descriptions.Item label={t('pages:simContinuousIdentity')}><Typography.Text copyable>{continuous.data.paperRunId}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label={t('pages:continuousSimLastBar')}>{formatDateTime(continuous.data.lastProcessedBar)}</Descriptions.Item>
            <Descriptions.Item label={t('pages:continuousSimNextBar')}>{formatDateTime(continuous.data.nextExpectedBar)}</Descriptions.Item>
            <Descriptions.Item label={t('pages:continuousSimGapStart')}>{formatDateTime(continuous.data.gapStartBar)}</Descriptions.Item>
            <Descriptions.Item label={t('pages:continuousSimLastAvailable')}>{formatDateTime(continuous.data.lastObservedBar)}</Descriptions.Item>
            <Descriptions.Item label={t('pages:continuousSimLastPoll')}>{formatDateTime(continuous.data.lastPollAt)}</Descriptions.Item>
            <Descriptions.Item label={t('pages:continuousSimReason')}>{continuous.data.blockReason ?? '—'}</Descriptions.Item>
            <Descriptions.Item label={t('pages:continuousSimLastDecision')}>{continuous.data.lastDecisionId ?? '—'} / {continuous.data.lastDecisionStatus ?? '—'}</Descriptions.Item>
        </Descriptions>}
        {continuous.isError && <Typography.Text type="secondary">{t('pages:simContinuousUnavailable')}</Typography.Text>}
        {scheduler.isError && <Alert type="warning" message={t('pages:simSchedulerUnavailable')}/>}
        {!scheduler.isError && scheduler.data?.filter(job => ['CONTINUOUS_SIM_POLL', 'PAPER_MATCHING'].includes(job.jobKey)).map(job =>
            <Typography.Paragraph key={job.jobKey}>{job.jobKey}: {job.enabled ? t('pages:simEnabled') : t('pages:simDisabled')} · {job.lastStatus} · {job.lastErrorCode ?? '—'}</Typography.Paragraph>)}
        {decisions.isError && <Alert type="warning" message={t('pages:simDecisionsUnavailable')}/>}
        <List size="small" bordered header={t('pages:strategySimDecisions')} dataSource={decisions.isError ? [] : decisions.data ?? []}
            locale={{emptyText: t('pages:strategySimNoDecisions')}} renderItem={decision => <List.Item key={decision.decisionId}>
                <Space direction="vertical" size={0}>
                    <Typography.Text>{decision.status} · {decision.reason} · {decision.side ?? '—'} {number(decision.quantity)}</Typography.Text>
                    <Typography.Text>{t('pages:simSignalTime')}: {formatDateTime(decision.signalOpenTime)}</Typography.Text>
                    <Typography.Text copyable>{decision.decisionId}</Typography.Text>
                    <Typography.Text>{t('pages:simStrategyRun')}: {decision.strategyRunId ?? '—'}</Typography.Text>
                    <Typography.Text>{t('pages:orderId')}: {decision.orderId ?? t('pages:simNotProduced')}</Typography.Text>
                    {facts?.orders.filter(order => order.order_id === decision.orderId).map(order =>
                        <Typography.Text key={String(order.order_id)}>{t('pages:simRiskOrderResult')}: {String(order.status)} · {String(order.reason ?? '—')}</Typography.Text>)}
                </Space>
            </List.Item>}/>
        {details && facts && (['orders', 'trades', 'ledgerEntries', 'riskEvents', 'positions'] as const).map(kind =>
            <CanonicalFactTable key={kind} kind={kind} facts={facts}/>)}
    </Space>;
}

function CanonicalFactTable({kind, facts}: {kind: 'orders' | 'trades' | 'ledgerEntries' | 'riskEvents' | 'positions'; facts: StrategySimFacts}) {
    const {t} = useTranslation('pages');
    const keys = kind === 'orders' ? ['order_id', 'strategy_run_id', 'symbol', 'side', 'qty', 'price', 'status', 'reason']
        : kind === 'trades' ? ['trade_id', 'order_id', 'symbol', 'side', 'qty', 'price', 'fee', 'ts']
        : kind === 'positions' ? ['id', 'account_id', 'symbol', 'qty', 'avg_price', 'updated_at']
        : kind === 'riskEvents' ? ['risk_event_id', 'order_id', 'rule_id', 'decision', 'reason', 'created_at']
        : ['entry_id', 'currency', 'delta', 'ref_type', 'ref_id', 'ts'];
    return <Table size="small" title={() => t(`pages:simTable_${kind}`)} dataSource={facts[kind]}
        rowKey={row => String(row[keys[0]])} pagination={{pageSize: 10}} scroll={{x: 1000}}
        columns={keys.map(key => ({width: key.endsWith('_id') ? 250 : ['ts', 'created_at', 'updated_at'].includes(key) ? 240 : 150, title: t(`pages:simColumn_${key}`), dataIndex: key,
            render: (value: unknown, row: Record<string, unknown>) => ['qty', 'price', 'fee', 'delta', 'avg_price'].includes(key)
                ? <NqAmountText exact value={(row[`exact_${key}`] ?? value) as string | number | null}/>
                : <Typography.Text copyable={key.endsWith('_id')}>{String(value ?? '—')}</Typography.Text>}))}/>;
}
