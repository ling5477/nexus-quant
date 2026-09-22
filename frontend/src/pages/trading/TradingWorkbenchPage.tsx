import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    App,
    Button,
    Card,
    Col,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    InputNumber,
    Row,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import type {FormInstance} from 'antd';
import {useEffect, useMemo, useState} from 'react';

import {formatApiError, showApiError} from '@/api/errors';
import {RuntimeGuardBanner} from '@/components/nq';
import {PageHero} from '@/components/page/PageHero';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {
    useCancelOrderMutation,
    usePlaceOrderMutation,
    useReconcileMutation,
    useRecoveryMutation,
    useTradingOrderListQuery,
    useTradingWorkbenchLookupQuery,
} from '@/hooks/useTradingWorkbench';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {
    AccountBalanceView,
    OperationTriggerResponse,
    OrderCancelRequestBody,
    OrderSubmitRequest,
    OrderView,
    ReconcileRunOnceRequest,
    RecoveryRunOnceRequest,
    TradingOrderListRequest,
    TradingWorkbenchLookupRequest,
    TradingWorkbenchLookupResult,
} from '@/types/trading-workbench';
import {formatDateTime, formatNumber, normalizeOptionalText} from '@/utils/formatters';

type ActionDrawer = 'place' | 'cancel' | 'reconcile' | 'recovery' | null;

interface TradingWorkbenchPageProps {
    legacyAlias?: boolean;
}

interface TradingOrderListForm {
    orderId?: string;
    symbol?: string;
    status?: string;
}

/**
 * TradingWorkbenchPage 是当前控制台的正式交易工作台。
 *
 * Why:
 * 页面以 header 中的正式 exchangeAccountId 为唯一账户上下文来源，列表、详情和写动作都围绕该上下文工作；
 * `/trade-validation` 只作为过渡入口复用本页，不再成为独立业务模式。
 */
export function TradingWorkbenchPage({legacyAlias = false}: TradingWorkbenchPageProps) {
    const {i18n: pageI18n} = useTranslation('pages');
    const {message} = App.useApp();
    const [listForm] = useLocalizedForm<TradingOrderListForm>();
    const [placeForm] = useLocalizedForm<OrderSubmitRequest>();
    const [cancelForm] = useLocalizedForm<OrderCancelRequestBody>();
    const [reconcileForm] = useLocalizedForm<ReconcileRunOnceRequest>();
    const [recoveryForm] = useLocalizedForm<RecoveryRunOnceRequest>();
    const selectedExchangeAccountId = useAccountContextStore((state) => state.selectedExchangeAccountId);
    const exchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const tradeEnv = useAccountContextStore((state) => state.tradeEnv);
    const accountAlias = useAccountContextStore((state) => state.accountAlias);
    const legacyAccountId = useAccountContextStore((state) => state.legacyAccountId);
    const [submittedListRequest, setSubmittedListRequest] = useState<TradingOrderListRequest | null>(null);
    const [listSearchVersion, setListSearchVersion] = useState(0);
    const [detailRequest, setDetailRequest] = useState<TradingWorkbenchLookupRequest | null>(null);
    const [detailSearchVersion, setDetailSearchVersion] = useState(0);
    const [detailOpen, setDetailOpen] = useState(false);
    const [activeAction, setActiveAction] = useState<ActionDrawer>(null);
    const [lastActionResult, setLastActionResult] = useState<OperationTriggerResponse | null>(null);

    const orderListQuery = useTradingOrderListQuery(submittedListRequest, listSearchVersion);
    const detailQuery = useTradingWorkbenchLookupQuery(detailRequest, detailSearchVersion);
    const placeOrderMutation = usePlaceOrderMutation();
    const cancelOrderMutation = useCancelOrderMutation();
    const reconcileMutation = useReconcileMutation();
    const recoveryMutation = useRecoveryMutation();

    const accountContextReady = Boolean(selectedExchangeAccountId && exchangeCode && tradeEnv);
    const currentContextLabel = accountContextReady
        ? `${exchangeCode} / ${tradeEnv} / ${accountAlias}（exchangeAccountId=${selectedExchangeAccountId}）`
        : t('pages:noCanonicalAccountContextSelected');

    useEffect(() => {
        if (!accountContextReady || !selectedExchangeAccountId) {
            setSubmittedListRequest(null);
            return;
        }
        const nextRequest: TradingOrderListRequest = {
            accountId: selectedExchangeAccountId,
            venue: exchangeCode ?? undefined,
            environment: tradeEnv ?? undefined,
            page: 0,
            size: 20,
        };
        listForm.setFieldsValue({orderId: undefined, symbol: undefined, status: undefined});
        placeForm.setFieldsValue({accountId: selectedExchangeAccountId, venue: exchangeCode ?? undefined});
        cancelForm.setFieldsValue({accountId: selectedExchangeAccountId});
        reconcileForm.setFieldsValue({venue: exchangeCode ?? undefined});
        recoveryForm.setFieldsValue({venue: exchangeCode ?? undefined});
        setSubmittedListRequest(nextRequest);
        setListSearchVersion((value) => value + 1);
    }, [
        accountContextReady,
        cancelForm,
        exchangeCode,
        listForm,
        placeForm,
        recoveryForm,
        reconcileForm,
        selectedExchangeAccountId,
        tradeEnv,
    ]);

    const orderColumns = useMemo<ColumnsType<OrderView>>(() => [
        {
            title: t('pages:orderId'),
            dataIndex: 'orderId',
            key: 'orderId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: t('pages:environment'),
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 100,
            render: (value: string) => <Tag color={value === 'LIVE' ? 'red' : 'blue'}>{value}</Tag>,
        },
        {
            title: t('pages:venue'),
            dataIndex: 'venue',
            key: 'venue',
            width: 110,
        },
        {
            title: t('pages:tradingPair'),
            dataIndex: 'symbol',
            key: 'symbol',
            width: 130,
        },
        {
            title: t('pages:side'),
            dataIndex: 'side',
            key: 'side',
            width: 90,
        },
        {
            title: t('pages:type'),
            dataIndex: 'type',
            key: 'type',
            width: 100,
        },
        {
            title: t('pages:price'),
            dataIndex: 'price',
            key: 'price',
            width: 120,
            render: (value: number | null) => formatNumber(value, 8),
        },
        {
            title: t('pages:quantity'),
            dataIndex: 'quantity',
            key: 'quantity',
            width: 120,
            render: (value: number) => formatNumber(value, 8),
        },
        {
            title: t('pages:status'),
            dataIndex: 'status',
            key: 'status',
            width: 130,
            render: (value: string) => <Tag color="blue">{value}</Tag>,
        },
        {
            title: t('pages:createdAt'),
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: t('pages:actions'),
            key: 'action',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button type="link" onClick={() => openDetail(record)}>
                    {t('pages:viewDetails')}</Button>
            ),
        },
    ], [pageI18n.resolvedLanguage]);

    const handleListSearch = (values: TradingOrderListForm) => {
        if (!selectedExchangeAccountId) {
            message.warning(t('pages:selectACanonicalAccountContextFirst'));
            return;
        }
        setSubmittedListRequest({
            accountId: selectedExchangeAccountId,
            orderId: normalizeOptionalText(values.orderId),
            venue: exchangeCode ?? undefined,
            symbol: normalizeOptionalText(values.symbol),
            status: normalizeOptionalText(values.status),
            environment: tradeEnv ?? undefined,
            page: 0,
            size: 20,
        });
        setListSearchVersion((value) => value + 1);
    };

    const handleListReset = () => {
        listForm.resetFields();
        if (!selectedExchangeAccountId) {
            setSubmittedListRequest(null);
            return;
        }
        setSubmittedListRequest({
            accountId: selectedExchangeAccountId,
            venue: exchangeCode ?? undefined,
            environment: tradeEnv ?? undefined,
            page: 0,
            size: 20,
        });
        setListSearchVersion((value) => value + 1);
    };

    const openDetail = (order: OrderView) => {
        setDetailRequest({
            orderId: order.orderId,
            accountId: selectedExchangeAccountId ?? undefined,
            symbol: order.symbol,
        });
        setDetailSearchVersion((value) => value + 1);
        setDetailOpen(true);
    };

    const handleActionSuccess = (result: OperationTriggerResponse, options?: { close?: boolean; refetch?: boolean }) => {
        setLastActionResult(result);
        message.success(t('pages:actionCompleted', {action: result.action}));
        const orderId = extractOrderId(result.detail);
        if (orderId && selectedExchangeAccountId) {
            setSubmittedListRequest({
                accountId: selectedExchangeAccountId,
                orderId,
                venue: exchangeCode ?? undefined,
                environment: tradeEnv ?? undefined,
                page: 0,
                size: 20,
            });
            setListSearchVersion((value) => value + 1);
        } else if (options?.refetch && submittedListRequest) {
            setListSearchVersion((value) => value + 1);
        }
        if (options?.close) {
            setActiveAction(null);
        }
    };

    return (
        <>
            <NqPageScaffold>
                {legacyAlias ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:tradeValidationIsATransitionalEntry')}
                        description={t('pages:theTradingWorkbenchIsAvailableAtTradingTheLegacyPathRemainsForCompatibility')}
                    />
                ) : null}

                <Card className="page-card" bordered={false}>
                    <PageHero
                        title={t('pages:tradingWorkbench')}
                        description={t('pages:queryOrdersAndViewDetailsInTheExchangeaccountidContextWithSimLiveAndPrerequisiteRiskControlsVisible')}
                        badge="Trading"
                    />
                </Card>

                <RuntimeGuardBanner variant="trading-workbench"/>

                <Card className="page-section" bordered={false} title={t('pages:accountContext')}>
                    {accountContextReady ? (
                        <Descriptions bordered size="small" column={2}>
                            <Descriptions.Item label={t('pages:currentAccount')}>{currentContextLabel}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:compatibleLegacyaccountid')}>{legacyAccountId ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="SIM / LIVE">
                                <Tag color={tradeEnv === 'LIVE' ? 'red' : 'blue'}>{tradeEnv}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:scope')}>
                                {t('pages:thisPageUsesTheCanonicalExchangeaccountidTheBackendHandlesCompatibilityWithLegacyTradingAccounts')}</Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Alert
                            type="warning"
                            showIcon
                            message={t('pages:noCanonicalAccountContextSelected')}
                            description={t('pages:selectAnExchangeAccountInTheHeaderOrAccountSettingsFirstQueriesAndWritesRequireAnAccountContext')}
                        />
                    )}
                </Card>

                <Card
                    className="page-section"
                    bordered={false}
                    title={t('pages:orderSearch')}
                    extra={(
                        <Space>
                            <Button type="primary" disabled={!accountContextReady} onClick={() => listForm.submit()}>
                                {t('pages:search')}</Button>
                            <Button disabled={!accountContextReady} onClick={handleListReset}>
                                {t('pages:reset')}</Button>
                        </Space>
                    )}
                >
                    <Form form={listForm} layout="vertical" onFinish={handleListSearch}>
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:orderId')} name="orderId">
                                    <Input placeholder={t('pages:optionalExactMatch')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:tradingPair')} name="symbol">
                                    <Input placeholder={t('pages:forExampleBtcUsdt')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:orderStatus')} name="status">
                                    <Select
                                        allowClear
                                        options={['CREATED', 'RISK_PASSED', 'ACCEPTED', 'PARTIALLY_FILLED', 'FILLED', 'CANCEL_REQUESTED', 'CANCELLED', 'REJECTED', 'FAILED'].map((value) => ({
                                            label: value,
                                            value,
                                        }))}
                                    />
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>

                <Card
                    className="page-section"
                    bordered={false}
                    title={t('pages:orders')}
                    extra={orderListQuery.data ? <Typography.Text type="secondary">{t('pages:total')}{orderListQuery.data.total} {t('pages:records')}</Typography.Text> : null}
                >
                    {!accountContextReady ? (
                        <Empty description={t('pages:ordersLoadAutomaticallyAfterAnAccountContextIsSelected')}/>
                    ) : orderListQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryOrders')}
                            description={formatApiError(orderListQuery.error as AppApiError)}
                            action={<Button size="small" onClick={() => setListSearchVersion((value) => value + 1)}>{t('pages:retry')}</Button>}
                        />
                    ) : (
                        <Table
                            rowKey="orderId"
                            columns={orderColumns}
                            dataSource={orderListQuery.data?.items ?? []}
                            loading={orderListQuery.isFetching}
                            pagination={false}
                            scroll={{x: 1500}}
                            locale={{emptyText: t('pages:noMatchingOrdersInTheCurrentAccountContext')}}
                        />
                    )}
                </Card>

                <Card className="page-section" bordered={false} title={t('pages:actions2')}>
                    <Space wrap>
                        <Button type="primary" disabled={!accountContextReady} onClick={() => setActiveAction('place')}>
                            {t('pages:preOrderChecks')}</Button>
                        <Button disabled={!accountContextReady} onClick={() => setActiveAction('cancel')}>
                            {t('pages:cancelOrder')}</Button>
                        <Button disabled={!accountContextReady} onClick={() => setActiveAction('reconcile')}>
                            {t('pages:runReconciliation')}</Button>
                        <Button disabled={!accountContextReady} onClick={() => setActiveAction('recovery')}>
                            {t('pages:runRecovery')}</Button>
                    </Space>
                    {lastActionResult ? (
                        <Card size="small" style={{marginTop: 16}} title={t('pages:latestActionFeedback')}>
                            <Descriptions bordered size="small" column={1}>
                                <Descriptions.Item label={t('pages:action')}>{lastActionResult.action}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:traceId')}>{lastActionResult.traceId}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:resultSummary')}>{lastActionResult.detail}</Descriptions.Item>
                            </Descriptions>
                        </Card>
                    ) : null}
                </Card>
            </NqPageScaffold>

            <Drawer open={detailOpen} width={860} title={t('pages:orderDetails')} onClose={() => setDetailOpen(false)} destroyOnClose>
                {detailQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingOrderDetails')}/>
                ) : detailQuery.error ? (
                    <Alert type="error" showIcon message={t('pages:failedToLoadOrderDetails')} description={formatApiError(detailQuery.error as AppApiError)}/>
                ) : detailQuery.data ? (
                    <OrderDetailContent result={detailQuery.data}/>
                ) : null}
            </Drawer>

            <Drawer open={activeAction === 'place'} width={640} title={t('pages:preOrderChecks')} onClose={() => setActiveAction(null)} destroyOnClose>
                <Alert
                    type={tradeEnv === 'LIVE' ? 'warning' : 'info'}
                    showIcon
                    style={{marginBottom: 16}}
                    message={t('pages:riskSummary')}
                    description={t('pages:orderRiskBoundary', {account: currentContextLabel})}
                />
                <Form
                    form={placeForm}
                    layout="vertical"
                    initialValues={{accountId: selectedExchangeAccountId ?? undefined, venue: exchangeCode ?? 'OKX', side: 'BUY', orderType: 'LIMIT'}}
                    onFinish={(values) => placeOrderMutation.mutate(normalizePlaceOrder(values), {
                        onSuccess: (result) => handleActionSuccess(result, {close: true}),
                        onError: (error) => showApiError(error as AppApiError, message),
                    })}
                >
                    <OrderActionFields/>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={placeOrderMutation.isPending}>
                            {t('pages:confirmAndSubmit')}</Button>
                        <Button onClick={() => setActiveAction(null)}>{t('pages:cancel')}</Button>
                    </Space>
                </Form>
            </Drawer>

            <Drawer open={activeAction === 'cancel'} width={560} title={t('pages:cancelOrder')} onClose={() => setActiveAction(null)} destroyOnClose>
                <Form
                    form={cancelForm}
                    layout="vertical"
                    initialValues={{accountId: selectedExchangeAccountId ?? undefined, reason: 'manual cancel'}}
                    onFinish={(values) => cancelOrderMutation.mutate({
                        orderId: normalizeOptionalText(values.orderId),
                        accountId: values.accountId,
                        clientOrderId: normalizeOptionalText(values.clientOrderId),
                        reason: normalizeOptionalText(values.reason),
                    }, {
                        onSuccess: (result) => handleActionSuccess(result, {close: true}),
                        onError: (error) => showApiError(error as AppApiError, message),
                    })}
                >
                    <Form.Item label={t('pages:orderId')} name="orderId">
                        <Input placeholder={t('pages:optionalIfOmittedProvideAccountidAndClientorderid')}/>
                    </Form.Item>
                    <Form.Item label={t('pages:accountId')} name="accountId">
                        <InputNumber style={{width: '100%'}} min={1}/>
                    </Form.Item>
                    <Form.Item label={t('pages:clientOrderId')} name="clientOrderId">
                        <Input placeholder={t('pages:identifiesTheOrderTogetherWithAccountid')}/>
                    </Form.Item>
                    <Form.Item label={t('pages:cancellationReason')} name="reason" rules={[{required: true, message: t('pages:enterACancellationReason')}]}>
                        <Input/>
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={cancelOrderMutation.isPending}>{t('pages:submitCancellation')}</Button>
                        <Button onClick={() => setActiveAction(null)}>{t('pages:cancel')}</Button>
                    </Space>
                </Form>
            </Drawer>

            <MaintenanceDrawer
                open={activeAction === 'reconcile'}
                showLimit
                title={t('pages:runReconciliation')}
                form={reconcileForm}
                defaultVenue={exchangeCode ?? 'OKX'}
                loading={reconcileMutation.isPending}
                onClose={() => setActiveAction(null)}
                onFinish={(values) => reconcileMutation.mutate({
                    venue: normalizeOptionalText(values.venue),
                    limit: values.limit,
                }, {
                    onSuccess: (result) => handleActionSuccess(result, {close: true, refetch: true}),
                    onError: (error) => showApiError(error as AppApiError, message),
                })}
            />
            <MaintenanceDrawer
                open={activeAction === 'recovery'}
                title={t('pages:runRecovery')}
                form={recoveryForm}
                defaultVenue={exchangeCode ?? 'OKX'}
                loading={recoveryMutation.isPending}
                onClose={() => setActiveAction(null)}
                onFinish={(values) => recoveryMutation.mutate({venue: normalizeOptionalText(values.venue)}, {
                    onSuccess: (result) => handleActionSuccess(result, {close: true, refetch: true}),
                    onError: (error) => showApiError(error as AppApiError, message),
                })}
            />
        </>
    );
}

function OrderDetailContent({result}: { result: TradingWorkbenchLookupResult }) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Descriptions bordered column={2} size="small" title={t('pages:order')}>
                <Descriptions.Item label={t('pages:orderId')}>{result.order.orderId}</Descriptions.Item>
                <Descriptions.Item label={t('pages:account')}>{result.order.accountId}</Descriptions.Item>
                <Descriptions.Item label={t('pages:venue')}>{result.order.venue}</Descriptions.Item>
                <Descriptions.Item label="SIM / LIVE"><Tag color={result.order.tradeEnv === 'LIVE' ? 'red' : 'blue'}>{result.order.tradeEnv}</Tag></Descriptions.Item>
                <Descriptions.Item label={t('pages:tradingPair')}>{result.order.symbol}</Descriptions.Item>
                <Descriptions.Item label={t('pages:status')}>{result.order.status}</Descriptions.Item>
                <Descriptions.Item label={t('pages:side')}>{result.order.side}</Descriptions.Item>
                <Descriptions.Item label={t('pages:type')}>{result.order.type}</Descriptions.Item>
                <Descriptions.Item label={t('pages:price')}>{formatNumber(result.order.price, 8)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:quantity')}>{formatNumber(result.order.quantity, 8)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:clientOrderId')}>{result.order.clientOrderId}</Descriptions.Item>
                <Descriptions.Item label={t('pages:externalOrderId')}>{result.order.externalOrderId || '-'}</Descriptions.Item>
                <Descriptions.Item label={t('pages:createdAt')}>{formatDateTime(result.order.createdAt)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:updatedAt')}>{formatDateTime(result.order.updatedAt)}</Descriptions.Item>
            </Descriptions>
            {result.latestTrade ? (
                <Descriptions bordered column={2} size="small" title={t('pages:trade')}>
                    <Descriptions.Item label={t('pages:tradeId')}>{result.latestTrade.tradeId}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:exchangeTradeId')}>{result.latestTrade.exchangeTradeId || '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:price')}>{formatNumber(result.latestTrade.price, 8)}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:quantity')}>{formatNumber(result.latestTrade.quantity, 8)}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:fee')}>{formatNumber(result.latestTrade.fee, 8)}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:feeCurrency')}>{result.latestTrade.feeCurrency || '-'}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:tradeTime')}>{formatDateTime(result.latestTrade.tradeTs)}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:traceId')}>{result.latestTrade.traceId}</Descriptions.Item>
                </Descriptions>
            ) : (
                <Alert type="info" showIcon message={t('pages:noLatestTradeIsAvailableForThisOrder')}/>
            )}
            {result.account ? (
                <Card title={t('pages:account')} size="small">
                    <Table
                        rowKey={(record) => `${record.currency}-${record.snapshotTs}`}
                        columns={balanceColumns}
                        dataSource={result.account.balances}
                        pagination={false}
                        size="small"
                        locale={{emptyText: t('pages:noBalanceSnapshotForThisAccount')}}
                    />
                </Card>
            ) : (
                <Alert type="info" showIcon message={t('pages:noBalanceSnapshotIsAvailableForThisAccount')}/>
            )}
            {result.position ? (
                <Descriptions bordered column={2} size="small" title={t('pages:position')}>
                    <Descriptions.Item label={t('pages:accountId')}>{result.position.accountId}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:venue')}>{result.position.venue}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:tradingPair')}>{result.position.symbol}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:positionQuantity')}>{formatNumber(result.position.quantity, 8)}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:availableQuantity')}>{formatNumber(result.position.availableQuantity, 8)}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:averagePrice')}>{formatNumber(result.position.avgPrice, 8)}</Descriptions.Item>
                </Descriptions>
            ) : (
                <Alert type="info" showIcon message={t('pages:noPositionSnapshotIsAvailableForThisAccountAndTradingPair')}/>
            )}
        </Space>
    );
}

const balanceColumns: ColumnsType<AccountBalanceView> = [
    {get title() { return t('pages:currency'); }, dataIndex: 'currency', key: 'currency', width: 120},
    {get title() { return t('pages:totalBalance'); }, dataIndex: 'balance', key: 'balance', width: 140, render: (value: number) => formatNumber(value, 8)},
    {get title() { return t('pages:available'); }, dataIndex: 'available', key: 'available', width: 140, render: (value: number) => formatNumber(value, 8)},
    {get title() { return t('pages:frozen'); }, dataIndex: 'frozen', key: 'frozen', width: 140, render: (value: number) => formatNumber(value, 8)},
    {get title() { return t('pages:snapshotTime'); }, dataIndex: 'snapshotTs', key: 'snapshotTs', width: 180, render: (value: string) => formatDateTime(value)},
];

function OrderActionFields() {
    useTranslation('pages');
    return (
        <Row gutter={[16, 0]}>
            <Col span={12}>
                <Form.Item label={t('pages:accountId')} name="accountId" rules={[{required: true, message: t('pages:enterAccountid')}]}>
                    <InputNumber style={{width: '100%'}} min={1}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label={t('pages:strategyRunId')} name="strategyRunId">
                    <Input placeholder={t('pages:optional')}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label={t('pages:venue')} name="venue" rules={[{required: true, message: t('pages:enterAVenue')}]}>
                    <Select options={[{label: 'OKX', value: 'OKX'}, {label: 'BINANCE', value: 'BINANCE'}]}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label={t('pages:clientOrderId')} name="clientOrderId" rules={[{required: true, message: t('pages:enterClientorderid')}]}>
                    <Input/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label={t('pages:tradingPair')} name="symbol" rules={[{required: true, message: t('pages:enterASymbol')}]}>
                    <Input/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label={t('pages:side')} name="side" rules={[{required: true, message: t('pages:selectASide')}]}>
                    <Select options={[{label: 'BUY', value: 'BUY'}, {label: 'SELL', value: 'SELL'}]}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label={t('pages:orderType')} name="orderType" rules={[{required: true, message: t('pages:selectAnOrderType')}]}>
                    <Select options={[{label: 'LIMIT', value: 'LIMIT'}, {label: 'MARKET', value: 'MARKET'}]}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label={t('pages:price')} name="price">
                    <InputNumber style={{width: '100%'}} min={0}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label={t('pages:quantity')} name="quantity" rules={[{required: true, message: t('pages:enterAQuantity')}]}>
                    <InputNumber style={{width: '100%'}} min={0.00000001}/>
                </Form.Item>
            </Col>
        </Row>
    );
}

function MaintenanceDrawer<T extends ReconcileRunOnceRequest | RecoveryRunOnceRequest>({
    open,
    showLimit = false,
    title,
    form,
    defaultVenue,
    loading,
    onClose,
    onFinish,
}: {
    open: boolean;
    showLimit?: boolean;
    title: string;
    form: FormInstance<T>;
    defaultVenue: string;
    loading: boolean;
    onClose: () => void;
    onFinish: (values: T) => void;
}) {
    useTranslation('pages');
    return (
        <Drawer open={open} width={520} title={title} onClose={onClose} destroyOnClose>
            <Form form={form} layout="vertical" initialValues={{venue: defaultVenue, limit: 100}} onFinish={onFinish}>
                <Form.Item label={t('pages:venue')} name="venue">
                    <Select options={[{label: 'OKX', value: 'OKX'}, {label: 'BINANCE', value: 'BINANCE'}]}/>
                </Form.Item>
                {showLimit ? (
                    <Form.Item label={t('pages:scanLimit')} name="limit">
                        <InputNumber style={{width: '100%'}} min={1}/>
                    </Form.Item>
                ) : null}
                <Space>
                    <Button type="primary" htmlType="submit" loading={loading}>{title}</Button>
                    <Button onClick={onClose}>{t('pages:cancel')}</Button>
                </Space>
            </Form>
        </Drawer>
    );
}

function normalizePlaceOrder(values: OrderSubmitRequest): OrderSubmitRequest {
    return {
        ...values,
        strategyRunId: normalizeOptionalText(values.strategyRunId),
        venue: normalizeOptionalText(values.venue),
        clientOrderId: normalizeOptionalText(values.clientOrderId),
        symbol: normalizeOptionalText(values.symbol),
    };
}

function extractOrderId(detail: string): string | null {
    const matched = /order_id=([^,]+)/.exec(detail);
    return matched?.[1] ?? null;
}
