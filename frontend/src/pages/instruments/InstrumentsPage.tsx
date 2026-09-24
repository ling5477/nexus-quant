import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Alert, App, Button, Card, Form, Select, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery} from '@tanstack/react-query';
import {useState} from 'react';

import {formatApiError, showApiError} from '@/api/errors';
import {instrumentsApi} from '@/features/instruments/api/instruments';
import {PageHero} from '@/components/page/PageHero';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {InstrumentCatalogItem} from '@/features/instruments/types/instruments';
import {formatDateTime, formatNumber} from '@/utils/formatters';

interface InstrumentFilterValues {
    exchangeCode?: string;
}

const columns: ColumnsType<InstrumentCatalogItem> = [
    {get title() { return t('pages:exchange'); }, dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {get title() { return t('pages:internalSymbol'); }, dataIndex: 'internalSymbol', key: 'internalSymbol', width: 160},
    {get title() { return t('pages:nativeSymbol'); }, dataIndex: 'exchangeSymbol', key: 'exchangeSymbol', width: 160},
    {get title() { return t('pages:baseAsset'); }, dataIndex: 'baseAsset', key: 'baseAsset', width: 100},
    {get title() { return t('pages:quoteAsset'); }, dataIndex: 'quoteAsset', key: 'quoteAsset', width: 100},
    {get title() { return t('pages:status'); }, dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'TRADING' || value === 'LIVE' ? 'success' : 'default'}>{value}</Tag>},
    {get title() { return t('pages:priceTick'); }, dataIndex: 'tickSize', key: 'tickSize', width: 120, render: (value: number | null) => formatNumber(value, 8)},
    {get title() { return t('pages:quantityStep'); }, dataIndex: 'stepSize', key: 'stepSize', width: 120, render: (value: number | null) => formatNumber(value, 8)},
    {get title() { return t('pages:minimumQuantity'); }, dataIndex: 'minQuantity', key: 'minQuantity', width: 140, render: (value: number | null) => formatNumber(value, 8)},
    {get title() { return t('pages:source'); }, dataIndex: 'source', key: 'source', width: 220},
    {get title() { return t('pages:synchronizedAt'); }, dataIndex: 'syncedAt', key: 'syncedAt', width: 180, render: (value: string) => formatDateTime(value)},
];

export function InstrumentsPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const [form] = useLocalizedForm<InstrumentFilterValues>();
    const contextExchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const [exchangeCode, setExchangeCode] = useState<string | undefined>(contextExchangeCode ?? undefined);

    const instrumentsQuery = useQuery({
        queryKey: ['instruments', exchangeCode ?? 'ALL'],
        queryFn: () => instrumentsApi.list(exchangeCode),
    });

    const syncMutation = useMutation({
        mutationFn: instrumentsApi.sync,
        onSuccess: async (result) => {
            message.success(t('pages:catalogSyncCompleted', {...result}));
            await instrumentsQuery.refetch();
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <PageHero
                    title={t('pages:instruments')}
                    description={t('pages:canonicalInstrumentAndSymbolCatalogForPairSelectionPrecisionValidationAndMultiCurrencyWorkflows')}
                    badge="Catalog"
                />
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title={t('pages:filterAndSynchronize')}
                extra={
                    <Space>
                        <Button type="primary" onClick={() => form.submit()}>
                            {t('pages:search')}</Button>
                        <Button onClick={() => syncMutation.mutate(exchangeCode)} loading={syncMutation.isPending}>
                            {t('pages:synchronizeCatalog')}</Button>
                    </Space>
                }
            >
                <Form<InstrumentFilterValues>
                    form={form}
                    layout="vertical"
                    initialValues={{exchangeCode: contextExchangeCode ?? undefined}}
                    onFinish={(values) => setExchangeCode(values.exchangeCode || undefined)}
                >
                    <Form.Item label={t('pages:exchange')} name="exchangeCode">
                        <Select allowClear options={[{label: 'BINANCE', value: 'BINANCE'}, {label: 'OKX', value: 'OKX'}]} />
                    </Form.Item>
                </Form>
                <Typography.Text type="secondary">
                    {t('pages:defaultExchangeForTheCurrentAccountContext')}{contextExchangeCode ?? t('pages:notSelected')}
                </Typography.Text>
            </Card>
            <Card className="page-section" bordered={false} title={t('pages:catalogEntries')}>
                {instrumentsQuery.error ? (
                    <Alert type="error" showIcon message={t('pages:failedToLoadTheInstrumentCatalog')} description={formatApiError(instrumentsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey="instrumentId"
                        columns={columns}
                        dataSource={instrumentsQuery.data ?? []}
                        loading={instrumentsQuery.isLoading || instrumentsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1600}}
                    />
                )}
            </Card>
        </Space>
    );
}
