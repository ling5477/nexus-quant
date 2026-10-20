import {
    Alert,
    Button,
    Card,
    Collapse,
    Descriptions,
    Empty,
    Select,
    Space,
    Spin,
    Table,
    Tabs,
    Tag,
    Typography,
} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Link, useSearchParams} from 'react-router-dom';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {NqAmountText} from '@/components/nq/NqNumericText';
import {NqMetricCard} from '@/components/nq/NqMetricCard';
import {simAnalysisApi, type SimAnalysis} from '@/features/paper-trading/api/analysis';
import {CanonicalFactTable} from '@/features/paper-trading/components/CanonicalSimFacts';
import {CanonicalSimOrderDetails} from '@/features/paper-trading/components/CanonicalSimOrderDetails';
import {SimPerformancePanel} from '@/features/paper-trading/components/SimPerformancePanel';
import {
    SimExecutionCode,
    SimExecutionDiagnostics,
} from '@/features/paper-trading/components/SimExecutionDiagnostics';
import {useAuthStore} from '@/store/auth-store';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';

/** 身份只由URL或用户明确选择；不可见、读取失败和切换实例时不沿用上一实例的资产。 */
export function SimAnalysisWorkspace({kind}: {kind: 'portfolio' | 'reviews' | 'diagnostics'}) {
    const {t} = useTranslation('pages');
    const userId = useAuthStore((state) => state.currentUser?.userId);
    const [params, setParams] = useSearchParams();
    const [offset, setOffset] = useState(0);
    const paperRunId = params.get('paperRunId');
    const invalid = paperRunId !== null && (!paperRunId.trim() || paperRunId.length > 128);
    const catalog = useQuery({
        queryKey: paperTradingQueryKeys.analysisCatalog(userId, offset),
        queryFn: ({signal}) => simAnalysisApi.list(offset, signal),
        enabled: Boolean(userId),
        retry: false,
    });
    const query = useQuery({
        queryKey: paperTradingQueryKeys.analysis(userId, paperRunId ?? ''),
        queryFn: ({signal}) => simAnalysisApi.read(paperRunId!, signal),
        enabled: Boolean(userId && paperRunId && !invalid),
        retry: false,
        refetchInterval: 30_000,
    });
    const analysis =
        !invalid && !query.isError && query.data?.run.paperRunId === paperRunId ? query.data : null;
    const options = (catalog.isError ? [] : (catalog.data?.items ?? [])).map((run) => ({
        value: run.paperRunId,
        label: `${run.strategyCode ?? run.versionName ?? run.symbol} · v${run.version ?? '—'} · ${run.status} · ${run.paperRunId.slice(-8)}`,
    }));
    if (analysis && !options.some((option) => option.value === paperRunId))
        options.push({
            value: analysis.run.paperRunId,
            label: `${analysis.run.strategyCode ?? analysis.run.symbol} · v${analysis.run.version ?? '—'} · ${analysis.run.status} · ${analysis.run.paperRunId.slice(-8)}`,
        });
    const select = (id: string) => {
        const next = new URLSearchParams(params);
        next.set('paperRunId', id);
        next.delete('orderId');
        next.delete('tab');
        setParams(next);
    };
    const requestedTab = params.get('tab');
    const allowedTabs =
        kind === 'portfolio' ? ['positions', 'ledger'] : ['decisions', 'risk', 'orders'];
    const tab = requestedTab && allowedTabs.includes(requestedTab) ? requestedTab : allowedTabs[0];
    const openOrder = (orderId: string) => {
        const next = new URLSearchParams(params);
        next.set('orderId', orderId);
        setParams(next);
    };
    return (
        <Space
            direction="vertical"
            size={12}
            style={{width: '100%', minWidth: 0}}
            data-testid={`sim-analysis-${kind}`}
        >
            <Card className="page-section" variant="borderless">
                <div className="sim-analysis-toolbar">
                    <div className="sim-analysis-selector">
                        <Typography.Text strong>{t('pages:simAnalysis.select')}</Typography.Text>
                        <Select
                            aria-label={t('pages:simAnalysis.select')}
                            placeholder={t('pages:simAnalysis.selectPlaceholder')}
                            showSearch
                            optionFilterProp="label"
                            value={paperRunId ?? undefined}
                            onChange={select}
                            options={options}
                            loading={catalog.isFetching}
                        />
                    </div>
                    <Space wrap>
                        <Button disabled={offset === 0} onClick={() => setOffset(offset - 50)}>
                            {t('pages:simAnalysis.previous')}
                        </Button>
                        <Typography.Text>
                            {t('pages:simAnalysis.page', {page: offset / 50 + 1})}
                        </Typography.Text>
                        <Button
                            disabled={!catalog.data?.hasMore || catalog.isFetching}
                            onClick={() => setOffset(offset + 50)}
                        >
                            {t('pages:simAnalysis.next')}
                        </Button>
                        <Button
                            disabled={!paperRunId || invalid}
                            loading={query.isFetching}
                            onClick={() => void query.refetch()}
                        >
                            {t('pages:simAnalysis.refresh')}
                        </Button>
                    </Space>
                </div>
                <Typography.Paragraph type="secondary" style={{margin: '10px 0 0'}}>
                    {t(`pages:simAnalysis.scope_${kind}`)}
                </Typography.Paragraph>
                {catalog.isError && (
                    <Alert
                        type="error"
                        showIcon
                        message={formatApiError(catalog.error as AppApiError)}
                        action={
                            <Button onClick={() => void catalog.refetch()}>
                                {t('pages:retry')}
                            </Button>
                        }
                    />
                )}
                {catalog.data?.windowLimited && (
                    <Alert type="warning" message={t('pages:simAnalysis.catalogLimited')} />
                )}
            </Card>
            {invalid && <Alert type="error" message={t('pages:simAnalysis.invalidIdentity')} />}
            {!paperRunId && (
                <Card className="page-section" variant="borderless">
                    <Empty description={t('pages:simAnalysis.chooseRun')} />
                </Card>
            )}
            {paperRunId && !invalid && query.isPending && <Spin />}
            {query.isError && !invalid && (
                <Alert
                    type="error"
                    showIcon
                    message={formatApiError(query.error as AppApiError)}
                    action={
                        <Button onClick={() => void query.refetch()}>{t('pages:retry')}</Button>
                    }
                />
            )}
            {analysis && (
                <>
                    <Card
                        className="page-section"
                        variant="borderless"
                        title={
                            <Space wrap>
                                <span>
                                    {analysis.run.symbol} · {analysis.run.intervalCode}
                                </span>
                                <Tag>{analysis.run.status}</Tag>
                            </Space>
                        }
                        extra={
                            <Link
                                to={`/paper-trading/runs?paperRunId=${encodeURIComponent(analysis.run.paperRunId)}`}
                            >
                                {t('pages:simAnalysis.manageRun')}
                            </Link>
                        }
                    >
                        {kind === 'portfolio' ? (
                            <AssetSummary analysis={analysis} />
                        ) : kind === 'diagnostics' ? (
                            <SimExecutionDiagnostics
                                analysis={analysis}
                                onInspect={(target) => {
                                    const next = new URLSearchParams(params);
                                    next.set('tab', target);
                                    setParams(next);
                                }}
                            />
                        ) : (
                            <ReviewSummary analysis={analysis} />
                        )}
                    </Card>
                    {kind !== 'diagnostics' && (
                        <SimPerformancePanel
                            key={analysis.run.paperRunId}
                            paperRunId={analysis.run.paperRunId}
                            kind={kind}
                        />
                    )}
                    <Card className="page-section" variant="borderless">
                        <Tabs
                            activeKey={tab}
                            onChange={(value) => {
                                const next = new URLSearchParams(params);
                                next.set('tab', value);
                                setParams(next);
                            }}
                            items={
                                kind === 'portfolio'
                                    ? [
                                          {
                                              key: 'positions',
                                              label: t('pages:simTable_positions'),
                                              children: (
                                                  <CanonicalFactTable
                                                      kind="positions"
                                                      facts={analysis.facts}
                                                      locatedOrderId={null}
                                                  />
                                              ),
                                          },
                                          {
                                              key: 'ledger',
                                              label: t('pages:simTable_ledgerEntries'),
                                              children: (
                                                  <>
                                                      <Typography.Paragraph type="secondary">
                                                          {t('pages:ledgerCashMeaning')}
                                                      </Typography.Paragraph>
                                                      <CanonicalFactTable
                                                          kind="ledgerEntries"
                                                          facts={analysis.facts}
                                                          locatedOrderId={null}
                                                      />
                                                  </>
                                              ),
                                          },
                                      ]
                                    : [
                                          {
                                              key: 'decisions',
                                              label: t('pages:strategySimDecisions'),
                                              children: (
                                                  <>
                                                      {analysis.metrics.decisionWindowLimited && (
                                                          <Alert
                                                              type="warning"
                                                              message={t(
                                                                  'pages:simAnalysis.decisionLimited',
                                                              )}
                                                          />
                                                      )}
                                                      <Table
                                                          size="small"
                                                          rowKey="decisionId"
                                                          dataSource={analysis.decisions}
                                                          pagination={{pageSize: 10}}
                                                          scroll={{x: 1050}}
                                                          columns={[
                                                              {
                                                                  title: t('pages:simSignalTime'),
                                                                  dataIndex: 'signalOpenTime',
                                                                  width: 210,
                                                                  render: formatDateTime,
                                                              },
                                                              {
                                                                  title: t(
                                                                      'pages:simColumn_status',
                                                                  ),
                                                                  dataIndex: 'status',
                                                                  width: 180,
                                                                  render: (value: string) => (
                                                                      <SimExecutionCode
                                                                          kind="status"
                                                                          value={value}
                                                                      />
                                                                  ),
                                                              },
                                                              {
                                                                  title: t(
                                                                      'pages:simColumn_reason',
                                                                  ),
                                                                  dataIndex: 'reason',
                                                                  width: 250,
                                                                  render: (value: string) => (
                                                                      <SimExecutionCode
                                                                          kind="reason"
                                                                          value={value}
                                                                      />
                                                                  ),
                                                              },
                                                              {
                                                                  title: t('pages:simColumn_side'),
                                                                  dataIndex: 'side',
                                                                  width: 90,
                                                                  render: (value: string | null) =>
                                                                      value ?? '—',
                                                              },
                                                              {
                                                                  title: t('pages:simColumn_qty'),
                                                                  dataIndex: 'quantity',
                                                                  width: 140,
                                                                  render: (
                                                                      value: string | null,
                                                                  ) => (
                                                                      <NqAmountText
                                                                          exact
                                                                          value={value}
                                                                      />
                                                                  ),
                                                              },
                                                              {
                                                                  title: t('pages:actions'),
                                                                  width: 110,
                                                                  fixed: 'right',
                                                                  render: (_, decision) =>
                                                                      decision.orderId ? (
                                                                          <Button
                                                                              type="link"
                                                                              onClick={() =>
                                                                                  openOrder(
                                                                                      decision.orderId!,
                                                                                  )
                                                                              }
                                                                          >
                                                                              {t(
                                                                                  'pages:orderDetails',
                                                                              )}
                                                                          </Button>
                                                                      ) : (
                                                                          t('pages:simNotProduced')
                                                                      ),
                                                              },
                                                          ]}
                                                      />
                                                  </>
                                              ),
                                          },
                                          {
                                              key: 'risk',
                                              label: t('pages:simTable_riskEvents'),
                                              children: (
                                                  <CanonicalFactTable
                                                      kind="riskEvents"
                                                      facts={analysis.facts}
                                                      locatedOrderId={null}
                                                  />
                                              ),
                                          },
                                          {
                                              key: 'orders',
                                              label: t('pages:simTable_orders'),
                                              children: (
                                                  <CanonicalFactTable
                                                      kind="orders"
                                                      facts={analysis.facts}
                                                      locatedOrderId={params.get('orderId')}
                                                  />
                                              ),
                                          },
                                      ]
                            }
                        />
                        <Typography.Text type="secondary">
                            {t('pages:simReadBounds')}
                        </Typography.Text>
                    </Card>
                    <Collapse
                        items={[
                            {
                                key: 'sources',
                                label: t('pages:frozenIdentityDetails'),
                                children: (
                                    <Descriptions size="small" bordered column={1}>
                                        {(
                                            [
                                                'paperRunId',
                                                'canonicalAccountId',
                                                'strategyVersionId',
                                                'publishId',
                                                'backtestRunId',
                                            ] as const
                                        ).map((key) => (
                                            <Descriptions.Item
                                                key={key}
                                                label={t(`pages:simAnalysis.identity_${key}`)}
                                            >
                                                <Typography.Text copyable>
                                                    {String(analysis.run[key] ?? '—')}
                                                </Typography.Text>
                                            </Descriptions.Item>
                                        ))}
                                        <Descriptions.Item label={t('pages:simAnalysis.createdAt')}>
                                            {formatDateTime(analysis.run.createdAt)}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:simAnalysis.costSnapshot')}
                                        >
                                            <pre className="sim-analysis-json">
                                                {analysis.run.configSnapshotJson ?? '—'}
                                            </pre>
                                        </Descriptions.Item>
                                    </Descriptions>
                                ),
                            },
                        ]}
                    />
                    <CanonicalSimOrderDetails
                        paperRunId={analysis.run.paperRunId}
                        orderId={params.get('orderId')}
                        onClose={() => {
                            const next = new URLSearchParams(params);
                            next.delete('orderId');
                            setParams(next, {replace: true});
                        }}
                    />
                </>
            )}
        </Space>
    );
}

function AssetSummary({analysis}: {analysis: SimAnalysis}) {
    const {t} = useTranslation('pages');
    const {facts} = analysis;
    const value = (key: 'cash' | 'positionQuantity' | 'equity' | 'pnl') =>
        ['equity', 'pnl'].includes(key) && !facts.markAsOf
            ? null
            : (facts.exactValues?.[key] ?? facts[key]);
    return (
        <>
            <div className="sim-analysis-metrics">
                <NqMetricCard
                    label={t('pages:strategySimBudget')}
                    value={<NqAmountText exact value={facts.initialBudget} />}
                    footer="USDT"
                />
                {(['cash', 'equity', 'pnl', 'positionQuantity'] as const).map((key) => (
                    <NqMetricCard
                        key={key}
                        label={t(`pages:simFact_${key}`)}
                        value={<NqAmountText exact value={value(key)} />}
                        footer={
                            key === 'positionQuantity' ? analysis.run.symbol.split('-')[0] : 'USDT'
                        }
                    />
                ))}
            </div>
            <Typography.Paragraph type="secondary" style={{marginTop: 12}}>
                {t('pages:simBudgetMeaning')}
            </Typography.Paragraph>
            <Descriptions size="small" column={{xs: 1, md: 3}}>
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
        </>
    );
}

function ReviewSummary({analysis}: {analysis: SimAnalysis}) {
    const {t} = useTranslation('pages');
    const {metrics, run} = analysis;
    return (
        <>
            <div className="sim-analysis-metrics">
                <NqMetricCard
                    label={t('pages:simAnalysis.decisions')}
                    value={metrics.decisionCount}
                />
                <NqMetricCard
                    label={t('pages:simAnalysis.orders')}
                    value={metrics.orderStatusCounts.reduce((sum, item) => sum + item.count, 0)}
                />
                <NqMetricCard label={t('pages:simAnalysis.trades')} value={metrics.tradeCount} />
                <NqMetricCard
                    label={t('pages:simAnalysis.riskEvents')}
                    value={metrics.riskEventCount}
                />
            </div>
            <Space wrap style={{marginTop: 12}}>
                {metrics.orderStatusCounts.map((item) => (
                    <Tag key={item.status}>
                        {item.status} · {item.count}
                    </Tag>
                ))}
            </Space>
            <Table
                size="small"
                style={{marginTop: 12}}
                pagination={false}
                rowKey={(row) => row.currency ?? 'UNKNOWN'}
                dataSource={metrics.fees}
                locale={{emptyText: t('pages:simAnalysis.noFees')}}
                columns={[
                    {
                        title: t('pages:simColumn_fee_currency'),
                        dataIndex: 'currency',
                        render: (value: string | null) =>
                            value ?? t('pages:simAnalysis.unknownCurrency'),
                    },
                    {
                        title: t('pages:simAnalysis.actualFees'),
                        dataIndex: 'totalFee',
                        render: (value: string | null) => <NqAmountText exact value={value} />,
                    },
                    {
                        title: t('pages:simAnalysis.feeCoverage'),
                        render: (_, row) => `${row.measuredFeeCount}/${row.tradeCount}`,
                    },
                ]}
            />
            <Space wrap style={{marginTop: 12}}>
                {run.backtestConfigId && (
                    <Link to={`/backtests/${encodeURIComponent(run.backtestConfigId)}`}>
                        {t('pages:simAnalysis.frozenBacktest')}
                    </Link>
                )}
                <Link to={`/publishes?publishId=${encodeURIComponent(run.publishId)}`}>
                    {t('pages:simAnalysis.publishedSource')}
                </Link>
            </Space>
            <Typography.Paragraph type="secondary" style={{margin: '12px 0 0'}}>
                {t('pages:simAnalysis.reviewMeaning')}
            </Typography.Paragraph>
        </>
    );
}
