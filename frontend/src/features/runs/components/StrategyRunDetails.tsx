import {Alert, Button, Collapse, Descriptions, Space, Tabs, Tag, Typography} from 'antd';
import {useQueryClient} from '@tanstack/react-query';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {runQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {RUN_TRIGGER_TYPE_OPTIONS} from '@/constants/filter-options';
import {useRunOverviewQuery} from '@/features/runs/hooks/useRunListQuery';
import {useAuthStore} from '@/store/auth-store';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {StrategyRunBusinessEventsTable} from './StrategyRunBusinessEventsTable';
import {RunRiskEventsTable, RunLedgerEntriesTable} from './StrategyRunRelationTables';
import {RunOrdersTable, RunTradesTable} from './StrategyRunRelatedTables';

/** 概览与可见页签独立有界读取；高级追踪信息保留，能力缺口不伪装成零条业务事实。 */
export function StrategyRunDetails({runId}: {runId: string}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    const cache = useQueryClient();
    const query = useRunOverviewQuery(runId);
    const [tab, setTab] = useState('orders');
    if (query.isPending) return <Typography.Text>{t('loadingRunDetails')}</Typography.Text>;
    if (query.isError) {
        const missing = (query.error as AppApiError).status === 404;
        return (
            <Alert
                type="error"
                showIcon
                message={t(missing ? 'runNotFound' : 'failedToLoadRunDetails')}
                description={
                    missing ? t('runNotFoundHint') : formatApiError(query.error as AppApiError)
                }
                action={<Button onClick={() => void query.refetch()}>{t('retry')}</Button>}
            />
        );
    }
    const overview = query.data,
        run = overview.run;
    const gapLabels: Record<string, string> = {
        LEDGER_RUN_LINK: t('runGapLedger'),
        RISK_RUN_LINK: t('runGapRisk'),
        EVENT_RUN_LINK: t('runGapEvent'),
        UNSCOPED_SYSTEM_EVENTS: t('runEvents.unscopedGap'),
    };
    const copy = (value: string | null) =>
        value ? (
            <Typography.Text copyable style={{wordBreak: 'break-all'}}>
                {value}
            </Typography.Text>
        ) : (
            '—'
        );
    const refresh = () => cache.invalidateQueries({queryKey: runQueryKeys.run(owner, runId)});
    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Space wrap style={{justifyContent: 'space-between', width: '100%'}}>
                <Space wrap>
                    <Tag
                        color={
                            run.status === 'FAILED'
                                ? 'error'
                                : run.status === 'SUCCEEDED'
                                  ? 'success'
                                  : 'blue'
                        }
                    >
                        {run.status}
                    </Tag>
                    <Typography.Text>
                        {RUN_TRIGGER_TYPE_OPTIONS.find((option) => option.value === run.triggerType)
                            ?.label ?? run.triggerType}
                    </Typography.Text>
                </Space>
                <Button loading={query.isFetching} onClick={() => void refresh()}>
                    {t('refreshDetails')}
                </Button>
            </Space>
            <Descriptions
                bordered
                size="small"
                layout="horizontal"
                column={{xs: 1, md: 2}}
                styles={{label: {whiteSpace: 'nowrap'}}}
            >
                <Descriptions.Item label={t('runId')}>{copy(run.strategyRunId)}</Descriptions.Item>
                <Descriptions.Item label={t('strategyId')}>
                    {copy(run.strategyId)}
                </Descriptions.Item>
                <Descriptions.Item label={t('scheduleId')}>
                    {copy(run.scheduleJobId)}
                </Descriptions.Item>
                <Descriptions.Item label={t('exchange')}>{run.exchangeCode}</Descriptions.Item>
                <Descriptions.Item label={t('account')}>{run.accountId ?? '—'}</Descriptions.Item>
                <Descriptions.Item label={t('tradingEnvironment')}>
                    {run.tradeEnv}
                </Descriptions.Item>
                <Descriptions.Item label={t('startTime')}>
                    {formatDateTime(run.startedAt)}
                </Descriptions.Item>
                <Descriptions.Item label={t('endTime')}>
                    {formatDateTime(run.finishedAt)}
                </Descriptions.Item>
                <Descriptions.Item label={t('orderCount')}>{overview.orderCount}</Descriptions.Item>
                <Descriptions.Item label={t('tradeCount2')}>
                    {overview.tradeCount}
                </Descriptions.Item>
            </Descriptions>
            {run.status === 'FAILED' ? (
                <Alert type="warning" showIcon message={t('runFailureHint')} />
            ) : null}
            <Tabs
                activeKey={tab}
                onChange={setTab}
                destroyOnHidden
                items={[
                    {
                        key: 'orders',
                        label: `${t('runRelatedOrders')} (${overview.orderCount})`,
                        children: <RunOrdersTable runId={runId} active={tab === 'orders'} />,
                    },
                    {
                        key: 'trades',
                        label: `${t('runRelatedTrades')} (${overview.tradeCount})`,
                        children: <RunTradesTable runId={runId} active={tab === 'trades'} />,
                    },
                    {
                        key: 'risk-events',
                        label: t('runOrderRisk'),
                        disabled: overview.relationshipScopes?.risk !== 'ORDER_EVENTS',
                        children: (
                            <RunRiskEventsTable runId={runId} active={tab === 'risk-events'} />
                        ),
                    },
                    {
                        key: 'business-events',
                        label: t('runEvents.title'),
                        disabled:
                            overview.relationshipScopes?.events !== 'CANONICAL_BUSINESS_EVENTS',
                        children: (
                            <StrategyRunBusinessEventsTable
                                runId={runId}
                                active={tab === 'business-events'}
                                onOrders={() => setTab('orders')}
                                onTrades={() => setTab('trades')}
                            />
                        ),
                    },
                    {
                        key: 'ledger-entries',
                        label: t('runTradeLedger'),
                        disabled: overview.relationshipScopes?.ledger !== 'TRADE_POSTINGS',
                        children: (
                            <RunLedgerEntriesTable
                                runId={runId}
                                active={tab === 'ledger-entries'}
                            />
                        ),
                    },
                ]}
            />
            {overview.unavailableRelationships.length ? (
                <Typography.Paragraph type="secondary" style={{margin: 0}}>
                    {t('runRelationshipGap', {
                        items: overview.unavailableRelationships
                            .map((key) => gapLabels[key] ?? t('runGapUnknown'))
                            .join(' / '),
                    })}
                </Typography.Paragraph>
            ) : null}
            <Collapse
                items={[
                    {
                        key: 'diagnostics',
                        label: t('runDiagnostics'),
                        children: (
                            <Space direction="vertical" style={{display: 'flex'}}>
                                <Descriptions size="small" column={1}>
                                    <Descriptions.Item label={t('requestId')}>
                                        {copy(run.requestId)}
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('runTraceId')}>
                                        {copy(overview.traceId)}
                                    </Descriptions.Item>
                                    <Descriptions.Item label={t('errorDiagnostics')}>
                                        <Typography.Text
                                            style={{whiteSpace: 'pre-wrap', wordBreak: 'break-all'}}
                                        >
                                            {run.errorMessage ?? '—'}
                                        </Typography.Text>
                                    </Descriptions.Item>
                                </Descriptions>
                                {overview.unavailableRelationships.length ? (
                                    <Typography.Paragraph type="secondary">
                                        {t(
                                            overview.unavailableRelationships.includes(
                                                'UNSCOPED_SYSTEM_EVENTS',
                                            )
                                                ? 'runEvents.technicalScope'
                                                : 'runGapTechnical',
                                        )}
                                    </Typography.Paragraph>
                                ) : null}
                            </Space>
                        ),
                    },
                ]}
            />
            <Typography.Text type="secondary">{t('runReadOnlyHint')}</Typography.Text>
        </Space>
    );
}
