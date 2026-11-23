import {
    ArrowDownOutlined,
    ArrowUpOutlined,
    DeleteOutlined,
    StarFilled,
    StarOutlined,
} from '@ant-design/icons';
import {Alert, Button, List, Modal, Select, Space, Tag, Tooltip, Typography} from 'antd';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Link} from 'react-router-dom';
import {instrumentQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {instrumentsApi} from '@/features/instruments/api/instruments';
import type {InstrumentCatalogItem} from '@/features/instruments/types/instruments';
import {useUserPreferences} from '../hooks/useUserPreferences';
import type {WatchlistSymbol} from '../api/preferences';

export const marketKey = (market: WatchlistSymbol) =>
    `${market.exchangeCode}:${market.marketType}:${market.symbol}`;
export const isActiveSpot = (item: InstrumentCatalogItem) =>
    item.instrumentType === 'SPOT' && ['TRADING', 'LIVE', 'ACTIVE'].includes(item.status);
export const catalogMarket = (item: InstrumentCatalogItem): WatchlistSymbol => ({
    exchangeCode: item.exchangeCode,
    marketType: 'SPOT',
    symbol: item.internalSymbol,
});

/** 所有选择来自正式目录；保存失败保留草稿，冲突后由用户重新读取。 */
export function WatchlistEditor() {
    const {t} = useTranslation();
    const {query, save} = useUserPreferences();
    const catalog = useQuery({
        queryKey: instrumentQueryKeys.list(),
        queryFn: () => instrumentsApi.list(),
    });
    const [open, setOpen] = useState(false);
    const [draft, setDraft] = useState<WatchlistSymbol[]>([]);
    const [defaultMarket, setDefaultMarket] = useState<WatchlistSymbol | null>(null);
    const [version, setVersion] = useState(0);
    const [addition, setAddition] = useState<string | undefined>();
    const options = (catalog.data ?? []).filter(isActiveSpot).map((item) => ({
        value: marketKey(catalogMarket(item)),
        label: `${item.internalSymbol} · ${item.exchangeCode}`,
    }));
    const activeKeys = new Set(options.map((item) => item.value));
    function loadCurrent() {
        if (query.data) {
            setDraft([...query.data.watchlist]);
            setDefaultMarket(query.data.defaultMarket);
            setVersion(query.data.version);
            setAddition(undefined);
            save.reset();
        }
    }
    function move(index: number, direction: number) {
        const copy = [...draft];
        [copy[index], copy[index + direction]] = [copy[index + direction], copy[index]];
        setDraft(copy);
    }
    return (
        <>
            <Button
                aria-label={t('markets.editWatchlist')}
                onClick={() => {
                    loadCurrent();
                    setOpen(true);
                }}
                disabled={!query.data}
                icon={<StarOutlined />}
            >
                {t('markets.editWatchlist')}
            </Button>
            {query.error && (
                <Alert
                    type="error"
                    message={formatApiError(query.error)}
                    action={
                        <Button onClick={() => void query.refetch()}>
                            {t('preferences.reload')}
                        </Button>
                    }
                />
            )}
            <Modal
                open={open}
                title={t('markets.editWatchlist')}
                onCancel={() => setOpen(false)}
                width={600}
                okText={t('preferences.save')}
                cancelText={t('cancel')}
                confirmLoading={save.isPending}
                onOk={() =>
                    save.mutate(
                        {watchlist: draft, defaultMarket, expectedVersion: version},
                        {onSuccess: () => setOpen(false)},
                    )
                }
            >
                <Typography.Paragraph type="secondary">
                    {t('markets.watchlistScope')}
                </Typography.Paragraph>
                {catalog.error && (
                    <Alert
                        type="error"
                        showIcon
                        message={formatApiError(catalog.error)}
                        action={
                            <Button onClick={() => void catalog.refetch()}>
                                {t('preferences.reload')}
                            </Button>
                        }
                    />
                )}
                {!catalog.isPending && !catalog.error && options.length === 0 && (
                    <Alert
                        type="info"
                        message={t('markets.catalogEmpty')}
                        action={
                            <Link to="/instruments" onClick={() => setOpen(false)}>
                                {t('markets.openCatalog')}
                            </Link>
                        }
                    />
                )}
                <Space.Compact style={{width: '100%'}}>
                    <Select
                        aria-label={t('markets.addMarket')}
                        showSearch
                        optionFilterProp="label"
                        value={addition}
                        onChange={setAddition}
                        style={{flex: 1}}
                        loading={catalog.isPending}
                        placeholder={t('markets.addMarket')}
                        options={options.filter(
                            (item) => !draft.some((m) => marketKey(m) === item.value),
                        )}
                    />
                    <Button
                        disabled={!addition || draft.length >= 20}
                        onClick={() => {
                            const item = catalog.data?.find(
                                (item) => marketKey(catalogMarket(item)) === addition,
                            );
                            if (item && isActiveSpot(item)) {
                                setDraft([...draft, catalogMarket(item)]);
                                setAddition(undefined);
                            }
                        }}
                    >
                        {t('markets.add')}
                    </Button>
                </Space.Compact>
                <List
                    dataSource={draft}
                    locale={{emptyText: t('markets.watchlistEmpty')}}
                    renderItem={(item, index) => (
                        <List.Item
                            key={marketKey(item)}
                            actions={[
                                <Tooltip key="default" title={t('markets.setDefault')}>
                                    <Button
                                        type="text"
                                        aria-label={`${t('markets.setDefault')} ${item.symbol} ${item.exchangeCode}`}
                                        disabled={!activeKeys.has(marketKey(item))}
                                        onClick={() => setDefaultMarket(item)}
                                        icon={
                                            defaultMarket &&
                                            marketKey(item) === marketKey(defaultMarket) ? (
                                                <StarFilled />
                                            ) : (
                                                <StarOutlined />
                                            )
                                        }
                                    />
                                </Tooltip>,
                                <Button
                                    key="up"
                                    type="text"
                                    aria-label={`${t('markets.moveUp')} ${item.symbol}`}
                                    disabled={index === 0}
                                    onClick={() => move(index, -1)}
                                    icon={<ArrowUpOutlined />}
                                />,
                                <Button
                                    key="down"
                                    type="text"
                                    aria-label={`${t('markets.moveDown')} ${item.symbol}`}
                                    disabled={index === draft.length - 1}
                                    onClick={() => move(index, 1)}
                                    icon={<ArrowDownOutlined />}
                                />,
                                <Button
                                    key="remove"
                                    type="text"
                                    aria-label={`${t('markets.remove')} ${item.symbol}`}
                                    icon={<DeleteOutlined />}
                                    onClick={() => {
                                        setDraft(
                                            draft.filter((m) => marketKey(m) !== marketKey(item)),
                                        );
                                        if (
                                            defaultMarket &&
                                            marketKey(defaultMarket) === marketKey(item)
                                        )
                                            setDefaultMarket(null);
                                    }}
                                />,
                            ]}
                        >
                            <Space>
                                <Typography.Text strong>{item.symbol}</Typography.Text>
                                <Typography.Text type="secondary">
                                    {item.exchangeCode}
                                </Typography.Text>
                                {!activeKeys.has(marketKey(item)) && !catalog.isPending && (
                                    <Tag color="warning">{t('markets.unavailable')}</Tag>
                                )}
                            </Space>
                        </List.Item>
                    )}
                />
                <label className="market-default-field">
                    {t('markets.defaultMarket')}{' '}
                    <Select
                        allowClear
                        showSearch
                        optionFilterProp="label"
                        style={{width: '100%'}}
                        aria-label={t('markets.defaultMarket')}
                        value={defaultMarket ? marketKey(defaultMarket) : undefined}
                        options={options}
                        onChange={(key) =>
                            setDefaultMarket(
                                key
                                    ? (catalog.data
                                          ?.map(catalogMarket)
                                          .find((item) => marketKey(item) === key) ?? null)
                                    : null,
                            )
                        }
                    />
                </label>
                {defaultMarket &&
                    !activeKeys.has(marketKey(defaultMarket)) &&
                    !catalog.isPending && (
                        <Alert type="warning" message={t('markets.defaultUnavailable')} />
                    )}
                {save.error && (
                    <Alert
                        style={{marginTop: 12}}
                        type="error"
                        message={formatApiError(save.error)}
                        action={
                            <Button
                                onClick={async () => {
                                    const result = await query.refetch();
                                    if (result.data) {
                                        setDraft([...result.data.watchlist]);
                                        setDefaultMarket(result.data.defaultMarket);
                                        setVersion(result.data.version);
                                        save.reset();
                                    }
                                }}
                            >
                                {t('preferences.reload')}
                            </Button>
                        }
                    />
                )}
            </Modal>
        </>
    );
}
