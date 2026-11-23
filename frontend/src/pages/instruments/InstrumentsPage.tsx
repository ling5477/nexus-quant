import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    App,
    Button,
    Card,
    Descriptions,
    Drawer,
    Form,
    Input,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery} from '@tanstack/react-query';
import {useEffect, useState} from 'react';
import {useSearchParams} from 'react-router-dom';

import {formatApiError, showApiError} from '@/api/errors';
import {instrumentsApi} from '@/features/instruments/api/instruments';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {useAccountContextStore} from '@/store/account-context-store';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {InstrumentCatalogItem} from '@/features/instruments/types/instruments';
import {formatDateTime} from '@/utils/formatters';
import {formatNqNumber} from '@/nq-design-system/format/nqFormat';
import {ExchangeBadge} from '@/nq-design-system/brand/ExchangeBadge';
import './InstrumentsPage.css';
import {ResearchResultRecovery, usePendingResultAction} from '@/features/ResearchResultRecovery';
import {submitResultAction} from '@/features/research-result-recovery';

interface InstrumentFilterValues {
    exchangeCode?: string;
    keyword?: string;
    market?: string;
    status?: string;
}

const columns: ColumnsType<InstrumentCatalogItem> = [
    {
        get title() {
            return t('pages:exchange');
        },
        dataIndex: 'exchangeCode',
        key: 'exchangeCode',
        width: 120,
        render: (value) => <ExchangeBadge code={value} />,
    },
    {
        get title() {
            return t('pages:internalSymbol');
        },
        dataIndex: 'internalSymbol',
        key: 'internalSymbol',
        width: 160,
    },
    {
        get title() {
            return t('pages:nativeSymbol');
        },
        dataIndex: 'exchangeSymbol',
        key: 'exchangeSymbol',
        width: 160,
    },
    {
        get title() {
            return t('pages:baseAsset');
        },
        dataIndex: 'baseAsset',
        key: 'baseAsset',
        width: 100,
    },
    {
        get title() {
            return t('pages:quoteAsset');
        },
        dataIndex: 'quoteAsset',
        key: 'quoteAsset',
        width: 100,
    },
    {
        get title() {
            return t('catalog.market');
        },
        dataIndex: 'instrumentType',
        key: 'market',
        width: 100,
        render: (value) => (value === 'SPOT' ? t('catalog.spot') : value),
    },
    {
        get title() {
            return t('pages:status');
        },
        dataIndex: 'status',
        key: 'status',
        width: 140,
        render: (value: string) => (
            <Tag
                title={value}
                color={value === 'TRADING' || value === 'LIVE' ? 'success' : 'default'}
            >
                {value === 'TRADING' || value === 'LIVE' ? t('catalog.trading') : value}
            </Tag>
        ),
    },
    {
        get title() {
            return t('pages:priceTick');
        },
        dataIndex: 'tickSize',
        key: 'tickSize',
        width: 120,
        render: (value: number | string | null) => formatNqNumber(value, {exact: true}),
    },
    {
        get title() {
            return t('pages:quantityStep');
        },
        dataIndex: 'stepSize',
        key: 'stepSize',
        width: 120,
        render: (value: number | string | null) => formatNqNumber(value, {exact: true}),
    },
    {
        get title() {
            return t('pages:minimumQuantity');
        },
        dataIndex: 'minQuantity',
        key: 'minQuantity',
        width: 140,
        render: (value: number | string | null) => formatNqNumber(value, {exact: true}),
    },
    {
        get title() {
            return t('pages:source');
        },
        dataIndex: 'source',
        key: 'source',
        width: 220,
        render: (value) =>
            /FIXTURE|DEMO/.test(value) ? (
                <Tag color="orange">
                    {t('catalog.testSource')} · {value}
                </Tag>
            ) : (
                value
            ),
    },
    {
        get title() {
            return t('pages:synchronizedAt');
        },
        dataIndex: 'syncedAt',
        key: 'syncedAt',
        width: 180,
        render: (value: string) => formatDateTime(value),
    },
];

export function InstrumentsPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const currentUser = useAuthStore((state) => state.currentUser);
    // 仅预判写入口的交互可用性；实际权限仍由正式 API 决定，目录读取不授予交易权限。
    const canSync = Boolean(
        currentUser?.roles.some((role) =>
            ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
        ),
    );
    const [form] = useLocalizedForm<InstrumentFilterValues>();
    const [params, setParams] = useSearchParams();
    const contextExchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const exchangeCode =
        params.get('venue') === 'ALL'
            ? undefined
            : (params.get('venue') ?? contextExchangeCode ?? undefined);
    const keyword = (params.get('q') ?? '').trim().toUpperCase();
    const market = params.get('market') ?? '';
    const status = params.get('status') ?? '';
    const [detail, setDetail] = useState<InstrumentCatalogItem | null>(null);
    useEffect(() => {
        form.setFieldsValue({
            exchangeCode,
            keyword,
            market: market || undefined,
            status: status || undefined,
        });
    }, [form, exchangeCode, keyword, market, status]);

    const instrumentsQuery = useQuery({
        queryKey: ['instruments', exchangeCode ?? 'ALL'],
        queryFn: ({signal}) => instrumentsApi.list(exchangeCode, signal),
    });
    const visible = (instrumentsQuery.data ?? []).filter(
        (item) =>
            (!market || item.instrumentType === market) &&
            (!status || item.status === status) &&
            (!keyword ||
                [item.internalSymbol, item.exchangeSymbol, item.baseAsset, item.quoteAsset].some(
                    (value) => value.toUpperCase().includes(keyword),
                )),
    );
    const markets = [...new Set((instrumentsQuery.data ?? []).map((item) => item.instrumentType))];
    const statuses = [...new Set((instrumentsQuery.data ?? []).map((item) => item.status))];

    const pendingSync = usePendingResultAction('INSTRUMENT_SYNC');
    const syncMutation = useMutation({
        mutationFn: (venue?: string) =>
            submitResultAction('INSTRUMENT_SYNC', venue ?? 'ALL', (requestId) =>
                instrumentsApi.sync(venue, requestId),
            ),
        onSuccess: async (result) => {
            message.success(t('pages:catalogSyncCompleted', {...result}));
            await instrumentsQuery.refetch();
        },
        onError: (error) => {
            if (error.message === 'INSTRUMENT_CATALOG_SYNC_DISABLED') {
                message.warning(t('catalog.syncDisabled'));
            } else showApiError(error as AppApiError, message);
        },
    });

    return (
        <Space
            className="instrument-catalog-page"
            direction="vertical"
            size={16}
            style={{display: 'flex'}}
        >
            <Card className="page-card" bordered={false}>
                <NqPageHeader
                    title={t('pages:instruments')}
                    description={t(
                        'pages:canonicalInstrumentAndSymbolCatalogForPairSelectionPrecisionValidationAndMultiCurrencyWorkflows',
                    )}
                />
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title={t('pages:filterAndSynchronize')}
                extra={
                    <Space wrap>
                        <Button type="primary" onClick={() => form.submit()}>
                            {t('pages:search')}
                        </Button>
                        <Button
                            onClick={() => syncMutation.mutate(exchangeCode)}
                            loading={syncMutation.isPending}
                            disabled={!canSync || Boolean(pendingSync)}
                            title={!canSync ? t('catalog.syncReadOnly') : undefined}
                        >
                            {t('pages:synchronizeCatalog')}
                        </Button>
                    </Space>
                }
            >
                <ResearchResultRecovery kind="INSTRUMENT_SYNC" />
                {!canSync && <Alert type="info" showIcon message={t('catalog.syncReadOnly')} />}
                <Form<InstrumentFilterValues>
                    className="instrument-catalog-filters"
                    form={form}
                    layout="vertical"
                    initialValues={{
                        exchangeCode,
                        keyword,
                        market: market || undefined,
                        status: status || undefined,
                    }}
                    onFinish={(values) => {
                        const next = new URLSearchParams(params);
                        next.set('venue', values.exchangeCode || 'ALL');
                        for (const [key, value] of Object.entries({
                            q: values.keyword,
                            market: values.market,
                            status: values.status,
                        })) {
                            if (value) next.set(key, value);
                            else next.delete(key);
                        }
                        setParams(next);
                    }}
                >
                    <Form.Item label={t('pages:exchange')} name="exchangeCode">
                        <Select
                            style={{width: '100%'}}
                            allowClear
                            placeholder={t('catalog.allVenues')}
                            options={[
                                {label: 'BINANCE', value: 'BINANCE'},
                                {label: 'OKX', value: 'OKX'},
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label={t('catalog.pair')} name="keyword">
                        <Input
                            allowClear
                            placeholder={t('catalog.searchPlaceholder')}
                            style={{width: '100%'}}
                        />
                    </Form.Item>
                    <Form.Item label={t('catalog.market')} name="market">
                        <Select
                            style={{width: '100%'}}
                            allowClear
                            placeholder={t('catalog.allMarkets')}
                            options={markets.map((value) => ({
                                value,
                                label: value === 'SPOT' ? t('catalog.spot') : value,
                            }))}
                        />
                    </Form.Item>
                    <Form.Item label={t('pages:status')} name="status">
                        <Select
                            style={{width: '100%'}}
                            allowClear
                            placeholder={t('catalog.allStatuses')}
                            options={statuses.map((value) => ({
                                value,
                                label:
                                    value === 'LIVE' || value === 'TRADING'
                                        ? t('catalog.trading')
                                        : value,
                            }))}
                        />
                    </Form.Item>
                </Form>
                <Typography.Paragraph type="secondary" style={{margin: '12px 0 0'}}>
                    {t('catalog.scope')}
                </Typography.Paragraph>
            </Card>
            <Card className="page-section" bordered={false} title={t('pages:catalogEntries')}>
                {instrumentsQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadTheInstrumentCatalog')}
                        description={formatApiError(instrumentsQuery.error as AppApiError)}
                        action={
                            // 相同筛选不会改变查询键，失败后提供明确的原目录重读入口。
                            <Button
                                loading={instrumentsQuery.isFetching}
                                onClick={() => void instrumentsQuery.refetch()}
                            >
                                {t('retry')}
                            </Button>
                        }
                    />
                ) : (
                    <Table
                        rowKey="instrumentId"
                        columns={[
                            ...columns,
                            {
                                title: t('catalog.details'),
                                key: 'details',
                                fixed: 'right',
                                width: 90,
                                render: (_, item) => (
                                    <Button
                                        size="small"
                                        type="link"
                                        onClick={() => setDetail(item)}
                                    >
                                        {t('catalog.details')}
                                    </Button>
                                ),
                            },
                        ]}
                        dataSource={visible}
                        loading={instrumentsQuery.isLoading || instrumentsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1600}}
                    />
                )}
            </Card>
            <Drawer
                title={detail?.internalSymbol ?? t('catalog.details')}
                open={detail !== null}
                onClose={() => setDetail(null)}
                width="min(500px, 100vw)"
                destroyOnHidden
            >
                {detail && (
                    <Descriptions
                        column={1}
                        items={Object.entries(detail).map(([key, value]) => ({
                            key,
                            label: t('catalog.fields.' + key, {defaultValue: key}),
                            children: /At$/.test(key)
                                ? formatDateTime(String(value))
                                : ['tickSize', 'stepSize', 'minQuantity'].includes(key)
                                  ? // 精度明细与列表统一展开十进制，不经浮点转换或另行舍入。
                                    formatNqNumber(value, {exact: true, thousands: false})
                                  : key === 'instrumentType' && value === 'SPOT'
                                    ? t('catalog.spot')
                                    : String(value ?? '—'),
                        }))}
                    />
                )}
            </Drawer>
        </Space>
    );
}
