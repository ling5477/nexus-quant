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
import {useEffect, useState} from 'react';

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
import {
    useCancelOrderMutation,
    usePlaceOrderMutation,
    useReconcileMutation,
    useRecoveryMutation,
    useTradeValidationLookupQuery,
} from '@/hooks/useTradeValidation';
import type {AppApiError} from '@/types/api';
import {useAccountContextStore} from '@/store/account-context-store';
import type {
    AccountBalanceView,
    OperationTriggerResponse,
    OrderCancelRequestBody,
    OrderSubmitRequest,
    OrderView,
    ReconcileRunOnceRequest,
    RecoveryRunOnceRequest,
    TradeValidationLookupRequest,
} from '@/types/trade-validation';
import {formatDateTime, formatNumber, normalizeOptionalText} from '@/utils/formatters';

type ActionDrawer = 'place' | 'cancel' | 'reconcile' | 'recovery' | null;

interface TradeValidationQueryForm {
    orderId: string;
    accountId?: number;
    symbol?: string;
}

function extractOrderId(detail: string): string | null {
    const matched = /order_id=([^,]+)/.exec(detail);
    return matched?.[1] ?? null;
}

const balanceColumns: ColumnsType<AccountBalanceView> = [
    {
        title: '币种',
        dataIndex: 'currency',
        key: 'currency',
        width: 120,
    },
    {
        title: '总余额',
        dataIndex: 'balance',
        key: 'balance',
        width: 140,
        render: (value: number) => formatNumber(value, 8),
    },
    {
        title: '可用',
        dataIndex: 'available',
        key: 'available',
        width: 140,
        render: (value: number) => formatNumber(value, 8),
    },
    {
        title: '冻结',
        dataIndex: 'frozen',
        key: 'frozen',
        width: 140,
        render: (value: number) => formatNumber(value, 8),
    },
    {
        title: '快照时间',
        dataIndex: 'snapshotTs',
        key: 'snapshotTs',
        width: 180,
        render: (value: string) => formatDateTime(value),
    },
];

export function TradeValidationPage() {
    const {message} = App.useApp();
    const [queryForm] = Form.useForm<TradeValidationQueryForm>();
    const [placeForm] = Form.useForm<OrderSubmitRequest>();
    const [cancelForm] = Form.useForm<OrderCancelRequestBody>();
    const [reconcileForm] = Form.useForm<ReconcileRunOnceRequest>();
    const [recoveryForm] = Form.useForm<RecoveryRunOnceRequest>();
    const [submittedRequest, setSubmittedRequest] = useState<TradeValidationLookupRequest | null>(null);
    const [searchVersion, setSearchVersion] = useState(0);
    const [detailOpen, setDetailOpen] = useState(false);
    const [activeAction, setActiveAction] = useState<ActionDrawer>(null);
    const [lastActionResult, setLastActionResult] = useState<OperationTriggerResponse | null>(null);
    const legacyAccountId = useAccountContextStore((state) => state.legacyAccountId);
    const exchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const tradeEnv = useAccountContextStore((state) => state.tradeEnv);
    const accountAlias = useAccountContextStore((state) => state.accountAlias);

    const lookupQuery = useTradeValidationLookupQuery(submittedRequest, searchVersion);
    const placeOrderMutation = usePlaceOrderMutation();
    const cancelOrderMutation = useCancelOrderMutation();
    const reconcileMutation = useReconcileMutation();
    const recoveryMutation = useRecoveryMutation();
    const hasSearched = searchVersion > 0;

    useEffect(() => {
        if (legacyAccountId) {
            queryForm.setFieldValue('accountId', legacyAccountId);
            placeForm.setFieldValue('accountId', legacyAccountId);
            cancelForm.setFieldValue('accountId', legacyAccountId);
        }
        if (exchangeCode) {
            placeForm.setFieldValue('venue', exchangeCode);
            reconcileForm.setFieldValue('venue', exchangeCode);
            recoveryForm.setFieldValue('venue', exchangeCode);
        }
    }, [cancelForm, exchangeCode, legacyAccountId, placeForm, queryForm, reconcileForm, recoveryForm]);

    const orderColumns: ColumnsType<OrderView> = [
        {
            title: '订单 ID',
            dataIndex: 'orderId',
            key: 'orderId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '账户',
            dataIndex: 'accountId',
            key: 'accountId',
            width: 120,
        },
        {
            title: 'Venue',
            dataIndex: 'venue',
            key: 'venue',
            width: 120,
        },
        {
            title: '交易对',
            dataIndex: 'symbol',
            key: 'symbol',
            width: 140,
        },
        {
            title: 'Client Order ID',
            dataIndex: 'clientOrderId',
            key: 'clientOrderId',
            width: 220,
        },
        {
            title: '外部订单 ID',
            dataIndex: 'externalOrderId',
            key: 'externalOrderId',
            width: 220,
            render: (value: string | null) => value || '-',
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
            width: 120,
            render: (value: string) => <Tag color="blue">{value}</Tag>,
        },
        {
            title: 'Trace',
            dataIndex: 'traceId',
            key: 'traceId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 120,
            render: () => (
                <Button type="link" onClick={() => setDetailOpen(true)}>
                    查看详情
                </Button>
            ),
        },
    ];

    const handleLookup = (values: TradeValidationQueryForm) => {
        setSubmittedRequest({
            orderId: normalizeOptionalText(values.orderId),
            accountId: values.accountId ?? legacyAccountId ?? undefined,
            symbol: normalizeOptionalText(values.symbol),
        });
        setSearchVersion((value) => value + 1);
    };

    const handleLookupReset = () => {
        queryForm.resetFields();
        setSubmittedRequest(null);
        setSearchVersion(0);
    };

    const handleActionSuccess = (result: OperationTriggerResponse, options?: { close?: boolean; refetch?: boolean }) => {
        setLastActionResult(result);
        message.success(`${result.action} 已执行。`);

        const orderId = extractOrderId(result.detail);

        if (orderId) {
            queryForm.setFieldValue('orderId', orderId);
            setSubmittedRequest((current) => ({
                orderId,
                accountId: current?.accountId ?? legacyAccountId ?? undefined,
                symbol: current?.symbol,
            }));
            setSearchVersion((value) => value + 1);
        } else if (options?.refetch && submittedRequest) {
            setSearchVersion((value) => value + 1);
        }

        if (options?.close) {
            setActiveAction(null);
        }
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <PageHero
                        title="交易验证"
                        description="当前页面已对接 `/api/trading/**` 的真实查询与动作接口。RC1 起账户上下文成为默认模式，详情抽屉继续聚合成交、账户和持仓信息，动作区收口下单、撤单、对账与恢复。"
                        badge="RC1-4"
                    />
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="查询区"
                    extra={(
                        <Space>
                            <Button type="primary" onClick={() => queryForm.submit()}>
                                查询
                            </Button>
                            <Button onClick={handleLookupReset}>
                                重置
                            </Button>
                        </Space>
                    )}
                >
                    {legacyAccountId ? (
                        <Alert
                            type="info"
                            showIcon
                            style={{marginBottom: 16}}
                            message={`当前账户上下文：${exchangeCode} / ${tradeEnv} / ${accountAlias}（legacyAccountId=${legacyAccountId}）`}
                        />
                    ) : (
                        <Alert
                            type="warning"
                            showIcon
                            style={{marginBottom: 16}}
                            message="当前未选择账户上下文；可先到“账户管理”页选择一个默认账户。"
                        />
                    )}
                    <Form
                        form={queryForm}
                        layout="vertical"
                        initialValues={{orderId: ''}}
                        onFinish={handleLookup}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item
                                    label="订单 ID"
                                    name="orderId"
                                    rules={[{required: true, message: '请输入 orderId'}]}
                                >
                                    <Input placeholder="真实查询主键，必填"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="账户 ID（默认当前上下文）" name="accountId">
                                    <InputNumber style={{width: '100%'}} min={1} placeholder="未填写时默认使用当前上下文账户"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="交易对" name="symbol">
                                    <Input placeholder="可空，与 accountId 组合查询持仓"/>
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="动作区"
                >
                    <Space wrap>
                        <Button type="primary" onClick={() => setActiveAction('place')}>
                            下单
                        </Button>
                        <Button onClick={() => setActiveAction('cancel')}>
                            撤单
                        </Button>
                        <Button onClick={() => setActiveAction('reconcile')}>
                            执行对账
                        </Button>
                        <Button onClick={() => setActiveAction('recovery')}>
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
                <Card
                    className="page-section"
                    bordered={false}
                    title="订单结果"
                    extra={hasSearched && lookupQuery.data?.order ? <Typography.Text type="secondary">共 1 条记录</Typography.Text> : null}
                >
                    {!hasSearched ? (
                        <Empty description="输入 orderId 后执行查询。"/>
                    ) : lookupQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="交易验证查询失败"
                            description={formatApiError(lookupQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="orderId"
                            columns={orderColumns}
                            dataSource={lookupQuery.data?.order ? [lookupQuery.data.order] : []}
                            loading={lookupQuery.isFetching}
                            pagination={false}
                            scroll={{x: 1800}}
                            locale={{
                                emptyText: '当前查询未返回订单记录。',
                            }}
                        />
                    )}
                </Card>
            </Space>

            <Drawer
                open={detailOpen}
                width={860}
                title="交易验证详情"
                onClose={() => setDetailOpen(false)}
                destroyOnClose
            >
                {lookupQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载交易详情..."/>
                ) : lookupQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message="交易详情加载失败"
                        description={formatApiError(lookupQuery.error as AppApiError)}
                    />
                ) : lookupQuery.data ? (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small" title="订单详情">
                            <Descriptions.Item label="订单 ID">{lookupQuery.data.order.orderId}</Descriptions.Item>
                            <Descriptions.Item label="账户">{lookupQuery.data.order.accountId}</Descriptions.Item>
                            <Descriptions.Item label="Venue">{lookupQuery.data.order.venue}</Descriptions.Item>
                            <Descriptions.Item label="交易对">{lookupQuery.data.order.symbol}</Descriptions.Item>
                            <Descriptions.Item label="Client Order ID">{lookupQuery.data.order.clientOrderId}</Descriptions.Item>
                            <Descriptions.Item label="外部订单 ID">{lookupQuery.data.order.externalOrderId || '-'}</Descriptions.Item>
                            <Descriptions.Item label="价格">{formatNumber(lookupQuery.data.order.price, 8)}</Descriptions.Item>
                            <Descriptions.Item label="数量">{formatNumber(lookupQuery.data.order.quantity, 8)}</Descriptions.Item>
                            <Descriptions.Item label="状态">{lookupQuery.data.order.status}</Descriptions.Item>
                            <Descriptions.Item label="Trace ID">{lookupQuery.data.order.traceId}</Descriptions.Item>
                        </Descriptions>

                        {lookupQuery.data.latestTrade ? (
                            <Descriptions bordered column={2} size="small" title="最新成交">
                                <Descriptions.Item label="成交 ID">{lookupQuery.data.latestTrade.tradeId}</Descriptions.Item>
                                <Descriptions.Item label="交易所成交 ID">{lookupQuery.data.latestTrade.exchangeTradeId || '-'}</Descriptions.Item>
                                <Descriptions.Item label="价格">{formatNumber(lookupQuery.data.latestTrade.price, 8)}</Descriptions.Item>
                                <Descriptions.Item label="数量">{formatNumber(lookupQuery.data.latestTrade.quantity, 8)}</Descriptions.Item>
                                <Descriptions.Item label="手续费">{formatNumber(lookupQuery.data.latestTrade.fee, 8)}</Descriptions.Item>
                                <Descriptions.Item label="手续费币种">{lookupQuery.data.latestTrade.feeCurrency || '-'}</Descriptions.Item>
                                <Descriptions.Item label="成交时间">{formatDateTime(lookupQuery.data.latestTrade.tradeTs)}</Descriptions.Item>
                                <Descriptions.Item label="Trace ID">{lookupQuery.data.latestTrade.traceId}</Descriptions.Item>
                            </Descriptions>
                        ) : (
                            <Alert type="info" showIcon message="当前订单尚未查询到最新成交，或后端返回 404。"/>
                        )}

                        {lookupQuery.data.account ? (
                            <Card title="账户快照" size="small">
                                <Descriptions bordered size="small" column={2} style={{marginBottom: 16}}>
                                    <Descriptions.Item label="账户 ID">{lookupQuery.data.account.accountId}</Descriptions.Item>
                                    <Descriptions.Item label="Venue">{lookupQuery.data.account.venue}</Descriptions.Item>
                                    <Descriptions.Item label="Trace ID" span={2}>{lookupQuery.data.account.traceId}</Descriptions.Item>
                                </Descriptions>
                                <Table
                                    rowKey={(record) => `${record.currency}-${record.snapshotTs}`}
                                    columns={balanceColumns}
                                    dataSource={lookupQuery.data.account.balances}
                                    pagination={false}
                                    size="small"
                                    locale={{emptyText: '当前账户快照没有余额记录。'}}
                                />
                            </Card>
                        ) : (
                            <Alert type="info" showIcon message="当前未返回账户快照。填写 accountId 后可在查询链路中一并拉取。"/>
                        )}

                        {lookupQuery.data.position ? (
                            <Descriptions bordered column={2} size="small" title="持仓快照">
                                <Descriptions.Item label="账户 ID">{lookupQuery.data.position.accountId}</Descriptions.Item>
                                <Descriptions.Item label="交易所">{lookupQuery.data.position.venue}</Descriptions.Item>
                                <Descriptions.Item label="交易对">{lookupQuery.data.position.symbol}</Descriptions.Item>
                                <Descriptions.Item label="持仓数量">{formatNumber(lookupQuery.data.position.quantity, 8)}</Descriptions.Item>
                                <Descriptions.Item label="可用数量">{formatNumber(lookupQuery.data.position.availableQuantity, 8)}</Descriptions.Item>
                                <Descriptions.Item label="均价">{formatNumber(lookupQuery.data.position.avgPrice, 8)}</Descriptions.Item>
                                <Descriptions.Item label="Trace ID" span={2}>{lookupQuery.data.position.traceId}</Descriptions.Item>
                            </Descriptions>
                        ) : (
                            <Alert type="info" showIcon message="当前未返回持仓快照。填写 accountId + symbol 后可在查询链路中一并拉取。"/>
                        )}
                    </Space>
                ) : null}
            </Drawer>

            <Drawer
                open={activeAction === 'place'}
                width={620}
                title="下单"
                onClose={() => setActiveAction(null)}
                destroyOnClose
            >
                <Form
                    form={placeForm}
                    layout="vertical"
                    initialValues={{accountId: legacyAccountId ?? undefined, venue: exchangeCode ?? 'OKX', side: 'BUY', orderType: 'LIMIT'}}
                    onFinish={(values) => {
                        placeOrderMutation.mutate(
                            {
                                ...values,
                                strategyRunId: normalizeOptionalText(values.strategyRunId),
                                venue: normalizeOptionalText(values.venue),
                                clientOrderId: normalizeOptionalText(values.clientOrderId),
                                symbol: normalizeOptionalText(values.symbol),
                            },
                            {
                                onSuccess: (result) => handleActionSuccess(result, {close: true}),
                                onError: (error) => message.error(formatApiError(error as AppApiError)),
                            },
                        );
                    }}
                >
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
                    <Space>
                        <Button type="primary" htmlType="submit" loading={placeOrderMutation.isPending}>
                            执行下单
                        </Button>
                        <Button onClick={() => setActiveAction(null)}>
                            取消
                        </Button>
                    </Space>
                </Form>
            </Drawer>

            <Drawer
                open={activeAction === 'cancel'}
                width={560}
                title="撤单"
                onClose={() => setActiveAction(null)}
                destroyOnClose
            >
                <Form
                    form={cancelForm}
                    layout="vertical"
                    initialValues={{accountId: legacyAccountId ?? undefined, reason: 'manual cancel'}}
                    onFinish={(values) => {
                        cancelOrderMutation.mutate(
                            {
                                orderId: normalizeOptionalText(values.orderId),
                                accountId: values.accountId,
                                clientOrderId: normalizeOptionalText(values.clientOrderId),
                                reason: normalizeOptionalText(values.reason),
                            },
                            {
                                onSuccess: (result) => handleActionSuccess(result, {close: true}),
                                onError: (error) => message.error(formatApiError(error as AppApiError)),
                            },
                        );
                    }}
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
                        <Button type="primary" htmlType="submit" loading={cancelOrderMutation.isPending}>
                            执行撤单
                        </Button>
                        <Button onClick={() => setActiveAction(null)}>
                            取消
                        </Button>
                    </Space>
                </Form>
            </Drawer>

            <Drawer
                open={activeAction === 'reconcile'}
                width={520}
                title="执行对账"
                onClose={() => setActiveAction(null)}
                destroyOnClose
            >
                <Form
                    form={reconcileForm}
                    layout="vertical"
                    initialValues={{venue: exchangeCode ?? 'OKX', limit: 100}}
                    onFinish={(values) => {
                        reconcileMutation.mutate(
                            {
                                venue: normalizeOptionalText(values.venue),
                                limit: values.limit,
                            },
                            {
                                onSuccess: (result) => handleActionSuccess(result, {close: true, refetch: true}),
                                onError: (error) => message.error(formatApiError(error as AppApiError)),
                            },
                        );
                    }}
                >
                    <Form.Item label="Venue" name="venue">
                        <Select options={[{label: 'OKX', value: 'OKX'}, {label: 'BINANCE', value: 'BINANCE'}]}/>
                    </Form.Item>
                    <Form.Item label="扫描上限" name="limit">
                        <InputNumber style={{width: '100%'}} min={1}/>
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={reconcileMutation.isPending}>
                            执行对账
                        </Button>
                        <Button onClick={() => setActiveAction(null)}>
                            取消
                        </Button>
                    </Space>
                </Form>
            </Drawer>

            <Drawer
                open={activeAction === 'recovery'}
                width={520}
                title="执行恢复"
                onClose={() => setActiveAction(null)}
                destroyOnClose
            >
                <Form
                    form={recoveryForm}
                    layout="vertical"
                    initialValues={{venue: exchangeCode ?? 'OKX'}}
                    onFinish={(values) => {
                        recoveryMutation.mutate(
                            {
                                venue: normalizeOptionalText(values.venue),
                            },
                            {
                                onSuccess: (result) => handleActionSuccess(result, {close: true, refetch: true}),
                                onError: (error) => message.error(formatApiError(error as AppApiError)),
                            },
                        );
                    }}
                >
                    <Form.Item label="Venue" name="venue">
                        <Select options={[{label: 'OKX', value: 'OKX'}, {label: 'BINANCE', value: 'BINANCE'}]}/>
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={recoveryMutation.isPending}>
                            执行恢复
                        </Button>
                        <Button onClick={() => setActiveAction(null)}>
                            取消
                        </Button>
                    </Space>
                </Form>
            </Drawer>
        </>
    );
}
