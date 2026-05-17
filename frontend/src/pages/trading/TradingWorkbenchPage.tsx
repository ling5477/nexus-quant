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

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
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
 * TradingWorkbenchPage 是 GateH-1 的正式交易工作台。
 *
 * Why:
 * 页面以 header 中的正式 exchangeAccountId 为唯一账户上下文来源，列表、详情和写动作都围绕该上下文工作；
 * `/trade-validation` 只作为过渡入口复用本页，不再成为独立业务模式。
 */
export function TradingWorkbenchPage({legacyAlias = false}: TradingWorkbenchPageProps) {
    const {message} = App.useApp();
    const [listForm] = Form.useForm<TradingOrderListForm>();
    const [placeForm] = Form.useForm<OrderSubmitRequest>();
    const [cancelForm] = Form.useForm<OrderCancelRequestBody>();
    const [reconcileForm] = Form.useForm<ReconcileRunOnceRequest>();
    const [recoveryForm] = Form.useForm<RecoveryRunOnceRequest>();
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
        : '当前未选择正式账户上下文';

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
            title: '订单 ID',
            dataIndex: 'orderId',
            key: 'orderId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '环境',
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 100,
            render: (value: string) => <Tag color={value === 'LIVE' ? 'red' : 'blue'}>{value}</Tag>,
        },
        {
            title: 'Venue',
            dataIndex: 'venue',
            key: 'venue',
            width: 110,
        },
        {
            title: '交易对',
            dataIndex: 'symbol',
            key: 'symbol',
            width: 130,
        },
        {
            title: '方向',
            dataIndex: 'side',
            key: 'side',
            width: 90,
        },
        {
            title: '类型',
            dataIndex: 'type',
            key: 'type',
            width: 100,
        },
        {
            title: '价格',
            dataIndex: 'price',
            key: 'price',
            width: 120,
            render: (value: number | null) => formatNumber(value, 8),
        },
        {
            title: '数量',
            dataIndex: 'quantity',
            key: 'quantity',
            width: 120,
            render: (value: number) => formatNumber(value, 8),
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 130,
            render: (value: string) => <Tag color="blue">{value}</Tag>,
        },
        {
            title: '创建时间',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 120,
            render: (_, record) => (
                <Button type="link" onClick={() => openDetail(record)}>
                    查看详情
                </Button>
            ),
        },
    ], []);

    const handleListSearch = (values: TradingOrderListForm) => {
        if (!selectedExchangeAccountId) {
            message.warning('请先选择正式账户上下文。');
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
        message.success(`${result.action} 已执行。`);
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
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                {legacyAlias ? (
                    <Alert
                        type="warning"
                        showIcon
                        message="/trade-validation 是过渡入口"
                        description="正式交易工作台入口为 /trading；当前旧路径保留兼容，不再作为独立业务入口。"
                    />
                ) : null}

                <Card className="page-card" bordered={false}>
                    <PageHero
                        title="交易工作台"
                        description="正式交易工作台。当前页围绕 exchangeAccountId 账户上下文查询订单、查看详情，并展示 SIM / LIVE 与风控前置状态。"
                        badge="GateH-1"
                    />
                </Card>

                <Card className="page-section" bordered={false} title="账户上下文">
                    {accountContextReady ? (
                        <Descriptions bordered size="small" column={2}>
                            <Descriptions.Item label="当前账户">{currentContextLabel}</Descriptions.Item>
                            <Descriptions.Item label="兼容 legacyAccountId">{legacyAccountId ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="SIM / LIVE">
                                <Tag color={tradeEnv === 'LIVE' ? 'red' : 'blue'}>{tradeEnv}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="边界说明">
                                当前页面只使用正式 exchangeAccountId；后端负责兼容映射到 legacy trading account。
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Alert
                            type="warning"
                            showIcon
                            message="当前未选择正式账户上下文"
                            description="请先在 Header 或账户管理页选择 exchange account。交易工作台不会绕过账户上下文执行查询或写动作。"
                        />
                    )}
                </Card>

                <Card
                    className="page-section"
                    bordered={false}
                    title="订单查询"
                    extra={(
                        <Space>
                            <Button type="primary" disabled={!accountContextReady} onClick={() => listForm.submit()}>
                                查询
                            </Button>
                            <Button disabled={!accountContextReady} onClick={handleListReset}>
                                重置
                            </Button>
                        </Space>
                    )}
                >
                    <Form form={listForm} layout="vertical" onFinish={handleListSearch}>
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="订单 ID" name="orderId">
                                    <Input placeholder="可空，精确筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="交易对" name="symbol">
                                    <Input placeholder="例如 BTC-USDT"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="订单状态" name="status">
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
                    title="订单列表"
                    extra={orderListQuery.data ? <Typography.Text type="secondary">共 {orderListQuery.data.total} 条记录</Typography.Text> : null}
                >
                    {!accountContextReady ? (
                        <Empty description="选择账户上下文后自动加载订单列表。"/>
                    ) : orderListQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="订单列表查询失败"
                            description={formatApiError(orderListQuery.error as AppApiError)}
                            action={<Button size="small" onClick={() => setListSearchVersion((value) => value + 1)}>重试</Button>}
                        />
                    ) : (
                        <Table
                            rowKey="orderId"
                            columns={orderColumns}
                            dataSource={orderListQuery.data?.items ?? []}
                            loading={orderListQuery.isFetching}
                            pagination={false}
                            scroll={{x: 1500}}
                            locale={{emptyText: '当前账户上下文下没有匹配订单。'}}
                        />
                    )}
                </Card>

                <Card className="page-section" bordered={false} title="动作区">
                    <Space wrap>
                        <Button type="primary" disabled={!accountContextReady} onClick={() => setActiveAction('place')}>
                            下单前检查
                        </Button>
                        <Button disabled={!accountContextReady} onClick={() => setActiveAction('cancel')}>
                            撤单
                        </Button>
                        <Button disabled={!accountContextReady} onClick={() => setActiveAction('reconcile')}>
                            执行对账
                        </Button>
                        <Button disabled={!accountContextReady} onClick={() => setActiveAction('recovery')}>
                            执行恢复
                        </Button>
                    </Space>
                    {lastActionResult ? (
                        <Card size="small" style={{marginTop: 16}} title="最近动作反馈">
                            <Descriptions bordered size="small" column={1}>
                                <Descriptions.Item label="动作">{lastActionResult.action}</Descriptions.Item>
                                <Descriptions.Item label="Trace ID">{lastActionResult.traceId}</Descriptions.Item>
                                <Descriptions.Item label="结果摘要">{lastActionResult.detail}</Descriptions.Item>
                            </Descriptions>
                        </Card>
                    ) : null}
                </Card>
            </Space>

            <Drawer open={detailOpen} width={860} title="订单详情" onClose={() => setDetailOpen(false)} destroyOnClose>
                {detailQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载订单详情..."/>
                ) : detailQuery.error ? (
                    <Alert type="error" showIcon message="订单详情加载失败" description={formatApiError(detailQuery.error as AppApiError)}/>
                ) : detailQuery.data ? (
                    <OrderDetailContent result={detailQuery.data}/>
                ) : null}
            </Drawer>

            <Drawer open={activeAction === 'place'} width={640} title="下单前检查" onClose={() => setActiveAction(null)} destroyOnClose>
                <Alert
                    type={tradeEnv === 'LIVE' ? 'warning' : 'info'}
                    showIcon
                    style={{marginBottom: 16}}
                    message="风控摘要"
                    description={`当前账户 ${currentContextLabel}。提交后后端会执行账户启用、重复请求、最小名义金额、精度、限流、kill switch 等前置风控；当前 GateH-1 未提供独立 dry-run 风控 API，因此下单前只展示上下文摘要和服务端风控不可绕过状态。`}
                />
                <Form
                    form={placeForm}
                    layout="vertical"
                    initialValues={{accountId: selectedExchangeAccountId ?? undefined, venue: exchangeCode ?? 'OKX', side: 'BUY', orderType: 'LIMIT'}}
                    onFinish={(values) => placeOrderMutation.mutate(normalizePlaceOrder(values), {
                        onSuccess: (result) => handleActionSuccess(result, {close: true}),
                        onError: (error) => message.error(formatApiError(error as AppApiError)),
                    })}
                >
                    <OrderActionFields/>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={placeOrderMutation.isPending}>
                            确认提交
                        </Button>
                        <Button onClick={() => setActiveAction(null)}>取消</Button>
                    </Space>
                </Form>
            </Drawer>

            <Drawer open={activeAction === 'cancel'} width={560} title="撤单" onClose={() => setActiveAction(null)} destroyOnClose>
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
                        onError: (error) => message.error(formatApiError(error as AppApiError)),
                    })}
                >
                    <Form.Item label="订单 ID" name="orderId">
                        <Input placeholder="可空；为空时需填写 accountId + clientOrderId"/>
                    </Form.Item>
                    <Form.Item label="账户 ID" name="accountId">
                        <InputNumber style={{width: '100%'}} min={1}/>
                    </Form.Item>
                    <Form.Item label="Client Order ID" name="clientOrderId">
                        <Input placeholder="与 accountId 组合定位订单"/>
                    </Form.Item>
                    <Form.Item label="撤单原因" name="reason" rules={[{required: true, message: '请输入撤单原因'}]}>
                        <Input/>
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={cancelOrderMutation.isPending}>执行撤单</Button>
                        <Button onClick={() => setActiveAction(null)}>取消</Button>
                    </Space>
                </Form>
            </Drawer>

            <MaintenanceDrawer
                open={activeAction === 'reconcile'}
                title="执行对账"
                form={reconcileForm}
                defaultVenue={exchangeCode ?? 'OKX'}
                loading={reconcileMutation.isPending}
                onClose={() => setActiveAction(null)}
                onFinish={(values) => reconcileMutation.mutate({
                    venue: normalizeOptionalText(values.venue),
                    limit: values.limit,
                }, {
                    onSuccess: (result) => handleActionSuccess(result, {close: true, refetch: true}),
                    onError: (error) => message.error(formatApiError(error as AppApiError)),
                })}
            />
            <MaintenanceDrawer
                open={activeAction === 'recovery'}
                title="执行恢复"
                form={recoveryForm}
                defaultVenue={exchangeCode ?? 'OKX'}
                loading={recoveryMutation.isPending}
                onClose={() => setActiveAction(null)}
                onFinish={(values) => recoveryMutation.mutate({venue: normalizeOptionalText(values.venue)}, {
                    onSuccess: (result) => handleActionSuccess(result, {close: true, refetch: true}),
                    onError: (error) => message.error(formatApiError(error as AppApiError)),
                })}
            />
        </>
    );
}

function OrderDetailContent({result}: { result: TradingWorkbenchLookupResult }) {
    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Descriptions bordered column={2} size="small" title="订单">
                <Descriptions.Item label="订单 ID">{result.order.orderId}</Descriptions.Item>
                <Descriptions.Item label="账户">{result.order.accountId}</Descriptions.Item>
                <Descriptions.Item label="Venue">{result.order.venue}</Descriptions.Item>
                <Descriptions.Item label="SIM / LIVE"><Tag color={result.order.tradeEnv === 'LIVE' ? 'red' : 'blue'}>{result.order.tradeEnv}</Tag></Descriptions.Item>
                <Descriptions.Item label="交易对">{result.order.symbol}</Descriptions.Item>
                <Descriptions.Item label="状态">{result.order.status}</Descriptions.Item>
                <Descriptions.Item label="方向">{result.order.side}</Descriptions.Item>
                <Descriptions.Item label="类型">{result.order.type}</Descriptions.Item>
                <Descriptions.Item label="价格">{formatNumber(result.order.price, 8)}</Descriptions.Item>
                <Descriptions.Item label="数量">{formatNumber(result.order.quantity, 8)}</Descriptions.Item>
                <Descriptions.Item label="Client Order ID">{result.order.clientOrderId}</Descriptions.Item>
                <Descriptions.Item label="外部订单 ID">{result.order.externalOrderId || '-'}</Descriptions.Item>
                <Descriptions.Item label="创建时间">{formatDateTime(result.order.createdAt)}</Descriptions.Item>
                <Descriptions.Item label="更新时间">{formatDateTime(result.order.updatedAt)}</Descriptions.Item>
            </Descriptions>
            {result.latestTrade ? (
                <Descriptions bordered column={2} size="small" title="成交">
                    <Descriptions.Item label="成交 ID">{result.latestTrade.tradeId}</Descriptions.Item>
                    <Descriptions.Item label="交易所成交 ID">{result.latestTrade.exchangeTradeId || '-'}</Descriptions.Item>
                    <Descriptions.Item label="价格">{formatNumber(result.latestTrade.price, 8)}</Descriptions.Item>
                    <Descriptions.Item label="数量">{formatNumber(result.latestTrade.quantity, 8)}</Descriptions.Item>
                    <Descriptions.Item label="手续费">{formatNumber(result.latestTrade.fee, 8)}</Descriptions.Item>
                    <Descriptions.Item label="手续费币种">{result.latestTrade.feeCurrency || '-'}</Descriptions.Item>
                    <Descriptions.Item label="成交时间">{formatDateTime(result.latestTrade.tradeTs)}</Descriptions.Item>
                    <Descriptions.Item label="Trace ID">{result.latestTrade.traceId}</Descriptions.Item>
                </Descriptions>
            ) : (
                <Alert type="info" showIcon message="当前订单没有可展示的最新成交。"/>
            )}
            {result.account ? (
                <Card title="账户" size="small">
                    <Table
                        rowKey={(record) => `${record.currency}-${record.snapshotTs}`}
                        columns={balanceColumns}
                        dataSource={result.account.balances}
                        pagination={false}
                        size="small"
                        locale={{emptyText: '当前账户没有余额快照。'}}
                    />
                </Card>
            ) : (
                <Alert type="info" showIcon message="当前账户没有可展示的余额快照。"/>
            )}
            {result.position ? (
                <Descriptions bordered column={2} size="small" title="持仓">
                    <Descriptions.Item label="账户 ID">{result.position.accountId}</Descriptions.Item>
                    <Descriptions.Item label="Venue">{result.position.venue}</Descriptions.Item>
                    <Descriptions.Item label="交易对">{result.position.symbol}</Descriptions.Item>
                    <Descriptions.Item label="持仓数量">{formatNumber(result.position.quantity, 8)}</Descriptions.Item>
                    <Descriptions.Item label="可用数量">{formatNumber(result.position.availableQuantity, 8)}</Descriptions.Item>
                    <Descriptions.Item label="均价">{formatNumber(result.position.avgPrice, 8)}</Descriptions.Item>
                </Descriptions>
            ) : (
                <Alert type="info" showIcon message="当前账户和交易对没有可展示的持仓快照。"/>
            )}
        </Space>
    );
}

const balanceColumns: ColumnsType<AccountBalanceView> = [
    {title: '币种', dataIndex: 'currency', key: 'currency', width: 120},
    {title: '总余额', dataIndex: 'balance', key: 'balance', width: 140, render: (value: number) => formatNumber(value, 8)},
    {title: '可用', dataIndex: 'available', key: 'available', width: 140, render: (value: number) => formatNumber(value, 8)},
    {title: '冻结', dataIndex: 'frozen', key: 'frozen', width: 140, render: (value: number) => formatNumber(value, 8)},
    {title: '快照时间', dataIndex: 'snapshotTs', key: 'snapshotTs', width: 180, render: (value: string) => formatDateTime(value)},
];

function OrderActionFields() {
    return (
        <Row gutter={[16, 0]}>
            <Col span={12}>
                <Form.Item label="账户 ID" name="accountId" rules={[{required: true, message: '请输入 accountId'}]}>
                    <InputNumber style={{width: '100%'}} min={1}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label="策略运行 ID" name="strategyRunId">
                    <Input placeholder="可空"/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label="Venue" name="venue" rules={[{required: true, message: '请输入 venue'}]}>
                    <Select options={[{label: 'OKX', value: 'OKX'}, {label: 'BINANCE', value: 'BINANCE'}]}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label="Client Order ID" name="clientOrderId" rules={[{required: true, message: '请输入 clientOrderId'}]}>
                    <Input/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label="交易对" name="symbol" rules={[{required: true, message: '请输入 symbol'}]}>
                    <Input/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label="方向" name="side" rules={[{required: true, message: '请选择方向'}]}>
                    <Select options={[{label: 'BUY', value: 'BUY'}, {label: 'SELL', value: 'SELL'}]}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label="订单类型" name="orderType" rules={[{required: true, message: '请选择订单类型'}]}>
                    <Select options={[{label: 'LIMIT', value: 'LIMIT'}, {label: 'MARKET', value: 'MARKET'}]}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label="价格" name="price">
                    <InputNumber style={{width: '100%'}} min={0}/>
                </Form.Item>
            </Col>
            <Col span={12}>
                <Form.Item label="数量" name="quantity" rules={[{required: true, message: '请输入 quantity'}]}>
                    <InputNumber style={{width: '100%'}} min={0.00000001}/>
                </Form.Item>
            </Col>
        </Row>
    );
}

function MaintenanceDrawer<T extends ReconcileRunOnceRequest | RecoveryRunOnceRequest>({
    open,
    title,
    form,
    defaultVenue,
    loading,
    onClose,
    onFinish,
}: {
    open: boolean;
    title: string;
    form: FormInstance<T>;
    defaultVenue: string;
    loading: boolean;
    onClose: () => void;
    onFinish: (values: T) => void;
}) {
    return (
        <Drawer open={open} width={520} title={title} onClose={onClose} destroyOnClose>
            <Form form={form} layout="vertical" initialValues={{venue: defaultVenue, limit: 100}} onFinish={onFinish}>
                <Form.Item label="Venue" name="venue">
                    <Select options={[{label: 'OKX', value: 'OKX'}, {label: 'BINANCE', value: 'BINANCE'}]}/>
                </Form.Item>
                {title === '执行对账' ? (
                    <Form.Item label="扫描上限" name="limit">
                        <InputNumber style={{width: '100%'}} min={1}/>
                    </Form.Item>
                ) : null}
                <Space>
                    <Button type="primary" htmlType="submit" loading={loading}>{title}</Button>
                    <Button onClick={onClose}>取消</Button>
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
