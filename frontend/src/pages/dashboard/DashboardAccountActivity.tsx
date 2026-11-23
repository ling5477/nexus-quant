import {Card, Input, Space, Table, Tabs, Typography} from 'antd';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import {tradingWorkbenchApi} from '@/features/trading/api/trading-workbench';
import {useUserPreferences} from '@/features/preferences/hooks/useUserPreferences';
import {WatchlistEditor} from '@/features/preferences/components/WatchlistEditor';
import {NqEmptyState, NqErrorState} from '@/components/nq';
import {formatDateTime} from '@/utils/formatters';
import {formatNqNumber} from '@/nq-design-system/format/nqFormat';
import type {OrderView, TradeView, PositionView} from '@/features/trading/types/trading-workbench';
import type {AppApiError} from '@/types/api';
import {tradingWorkbenchQueryKeys} from '@/api/query-keys';

/** 自选只呈现用户已保存的市场身份；没有行情来源时不拼造价格和趋势。 */
export function DashboardWatchlist() {
    const {t} = useTranslation('product');
    const {query} = useUserPreferences();
    return <Card className="page-section nq-dashboard__watchlist">
        <Space wrap>
            <Typography.Text strong>{t('watchlistTab')}</Typography.Text>
            <WatchlistEditor />
            {(query.data?.watchlist ?? []).map(market => <Link key={`${market.exchangeCode}:${market.symbol}`} to={'/marketdata?' + new URLSearchParams({...market, section: 'market'})}>
                {market.symbol} · {market.exchangeCode}
            </Link>)}
            {query.isSuccess && query.data.watchlist.length === 0 && <Typography.Text type="secondary">{t('watchlistEmpty')}</Typography.Text>}
            {query.error && <Typography.Text type="danger">{t('watchlistUnavailable')}</Typography.Text>}
        </Space>
    </Card>;
}

/** 本地事实随正式查看账户切换；独立SIM仅在运行工作区展示，未知币种不补造估值。 */
export function DashboardAccountActivity() {
    const {t} = useTranslation('product');
    const owner = useAuthStore(state => state.currentUser?.userId);
    const {selectedExchangeAccountId: accountId, exchangeCode, tradeEnv, accountAlias} = useAccountContextStore();
    const [tab, setTab] = useState('orders');
    const [window, setWindow] = useState({accountId, tab, page: 0, symbol: ''});
    const scope = window.accountId === accountId && window.tab === tab ? window : {accountId, tab, page: 0, symbol: ''};
    const request = accountId && exchangeCode && tradeEnv ? {accountId, venue: exchangeCode, environment: tradeEnv, page: scope.page, size: 10, symbol: scope.symbol || undefined} : null;
    const enabled = Boolean(owner && request);
    const orders = useQuery({queryKey: tradingWorkbenchQueryKeys.accountFacts(owner, 'orders', request), queryFn: () => tradingWorkbenchApi.listOrders(request!), enabled: enabled && tab === 'orders'});
    const trades = useQuery({queryKey: tradingWorkbenchQueryKeys.accountFacts(owner, 'trades', request), queryFn: () => tradingWorkbenchApi.listAccountTrades(request!), enabled: enabled && tab === 'trades'});
    const positions = useQuery({queryKey: tradingWorkbenchQueryKeys.accountFacts(owner, 'positions', request), queryFn: () => tradingWorkbenchApi.listAccountPositions(request!), enabled: enabled && tab === 'positions'});
    const active = tab === 'trades' ? trades : tab === 'positions' ? positions : orders;
    const amount = (value: string | null) => formatNqNumber(value, {exact: true});
    const symbolColumn = {key: 'symbol', dataIndex: 'symbol', title: t('symbol'), width: 120};
    const pagination = {current: scope.page + 1, pageSize: 10, total: active.data?.total ?? 0, showSizeChanger: false, onChange: (page: number) => setWindow({...scope, page: page - 1})};
    const orderLink = (orderId: string) => <Link to={'/trading?' + new URLSearchParams({exchangeAccountId: String(accountId), orderId})}>{t('details')}</Link>;
    return <Card className="page-section nq-dashboard__account-activity" title={t('accountActivity')}
        extra={<Link to={accountId ? `/trading?exchangeAccountId=${accountId}` : '/trading'}>{t('openTrading')} →</Link>}>
        <Typography.Paragraph type="secondary">{accountAlias ?? t('chooseAccount')} · {exchangeCode} · {tradeEnv} · {t('localFactsScope')}</Typography.Paragraph>
        <Input.Search aria-label={t('filterSymbol')} placeholder={t('filterSymbol')} allowClear onSearch={symbol => setWindow({...scope, symbol: symbol.trim(), page: 0})} style={{maxWidth: 300, marginBottom: 12}} />
        <Tabs activeKey={tab} onChange={setTab} items={['orders','trades','positions'].map(key => ({key, label: t('activity_' + key)}))} />
        {!request ? <NqEmptyState description={t('chooseAccount')} /> : active.error ? <NqErrorState title={t('accountActivity')} error={active.error as AppApiError} /> : <>
        {tab === 'orders' && <Table<OrderView> rowKey="orderId" size="small" loading={orders.isPending} dataSource={orders.data?.items ?? []} pagination={pagination} scroll={{x: 820}} columns={[
            symbolColumn,
            {key: 'side', dataIndex: 'side', title: t('side'), width: 90, render: (value: string) => t(`pages:tradingActions.sides.${value}`)},
            {key: 'price', dataIndex: 'price', title: t('price'), align: 'right', width: 130, render: amount},
            {key: 'quantity', dataIndex: 'quantity', title: t('quantity'), align: 'right', width: 120, render: amount},
            {key: 'status', dataIndex: 'status', title: t('status'), width: 120, render: (value: string) => t(`pages:tradingActions.orderStatuses.${value}`)},
            {key: 'createdAt', dataIndex: 'createdAt', title: t('time'), width: 180, render: formatDateTime},
            {key: 'action', title: t('details'), fixed: 'right', width: 90, render: (_, row) => orderLink(row.orderId)},
        ]} />}
        {tab === 'trades' && <Table<TradeView> rowKey="tradeId" size="small" loading={trades.isPending} dataSource={trades.data?.items ?? []} pagination={pagination} scroll={{x: 900}} columns={[
            symbolColumn,
            {key: 'price', dataIndex: 'price', title: t('price'), align: 'right', render: amount},
            {key: 'quantity', dataIndex: 'quantity', title: t('quantity'), align: 'right', render: amount},
            {key: 'fee', title: t('fee'), render: (_, row) => `${amount(row.fee)} ${row.feeCurrency ?? t('currencyUnknown')}`},
            {key: 'time', dataIndex: 'tradeTs', title: t('time'), render: formatDateTime},
            {key: 'id', dataIndex: 'tradeId', title: t('tradeId'), render: (id: string) => <Typography.Text copyable>{id}</Typography.Text>},
            {key: 'order', title: t('order'), render: (_, row) => orderLink(row.orderId)},
        ]} />}
        {tab === 'positions' && <><Typography.Paragraph type="secondary">{t('positionSourceLimit')}</Typography.Paragraph><Table<PositionView> rowKey="symbol" size="small" loading={positions.isPending} dataSource={positions.data?.items ?? []} pagination={pagination} scroll={{x: 650}} columns={[
            symbolColumn,
            {key: 'qty', dataIndex: 'quantity', title: t('quantity'), align: 'right', render: amount},
            {key: 'available', dataIndex: 'availableQuantity', title: t('availableQuantity'), align: 'right', render: amount},
            {key: 'avg', dataIndex: 'avgPrice', title: t('avgPriceUnvalued'), align: 'right', render: amount},
        ]} /></>}
        </>}
    </Card>;
}
