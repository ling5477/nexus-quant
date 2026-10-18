import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    App,
    Button,
    Card,
    Col,
    Collapse,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    Row,
    Select,
    Space,
    Table,
    Tag,
    Typography,
    Tooltip,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useMemo, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {accountsApi} from '@/features/accounts/api/accounts';
import {useAuthStore} from '@/store/auth-store';
import {CanonicalSimFacts} from '@/features/paper-trading/components/CanonicalSimFacts';
import {TradingPreflightPanel} from '@/features/trading/components/TradingPreflightPanel';
import {ManualOrderWorkspace} from '@/features/trading/components/ManualOrderWorkspace';
import {MaintenanceOperationDrawer} from '@/features/trading/components/MaintenanceOperationDrawer';
import {OrderCancellationWorkspace} from '@/features/trading/components/OrderCancellationWorkspace';
import {canRequestCancellation} from '@/features/trading/order-cancellation';
import {
    tradingOperationFeedback,
    tradingOrderStatuses,
} from '@/features/trading/trading-presentation';

import {formatApiError} from '@/api/errors';
import {RuntimeGuardBanner, NqAmountText} from '@/components/nq';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {
    useTradingOrderListQuery,
    useTradingWorkbenchLookupQuery,
} from '@/features/trading/hooks/useTradingWorkbench';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {
    AccountBalanceView,
    OperationTriggerResponse,
    OrderView,
    TradingOrderListRequest,
    TradingWorkbenchLookupRequest,
    TradingWorkbenchLookupResult,
} from '@/features/trading/types/trading-workbench';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type ActionDrawer = 'preflight' | 'reconcile' | 'recovery' | null;

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
    const [searchParams, setSearchParams] = useSearchParams();
    const canonicalPaperRunId = searchParams.get('paperRunId');
    const routedAccount = searchParams.get('exchangeAccountId');
    const routedAccountId =
        routedAccount &&
        /^\d+$/.test(routedAccount) &&
        Number.isSafeInteger(Number(routedAccount)) &&
        Number(routedAccount) > 0
            ? Number(routedAccount)
            : null;
    const userId = useAuthStore((state) => state.currentUser?.userId);
    const canTrade = useAuthStore(
        (state) =>
            !!state.currentUser?.roles.some((role) =>
                ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
            ),
    );
    const [cancelTarget, setCancelTarget] = useState<OrderView | null>(null);
    const setSelectedAccount = useAccountContextStore((state) => state.setSelectedAccount);
    const routedAccountQuery = useQuery({
        queryKey: ['trading-routed-account', userId, routedAccountId],
        queryFn: () => accountsApi.detail(routedAccountId!),
        enabled: Boolean(userId && routedAccountId),
    });
    useEffect(() => {
        if (routedAccountQuery.data?.status === 'ACTIVE')
            setSelectedAccount(routedAccountQuery.data);
    }, [routedAccountQuery.data, setSelectedAccount]);
    const [listForm] = useLocalizedForm<TradingOrderListForm>();
    const selectedExchangeAccountId = useAccountContextStore(
        (state) => state.selectedExchangeAccountId,
    );
    const exchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const tradeEnv = useAccountContextStore((state) => state.tradeEnv);
    const accountAlias = useAccountContextStore((state) => state.accountAlias);
    const legacyAccountId = useAccountContextStore((state) => state.legacyAccountId);
    const [submittedListRequest, setSubmittedListRequest] =
        useState<TradingOrderListRequest | null>(null);
    const [listSearchVersion, setListSearchVersion] = useState(0);
    const [detailRequest, setDetailRequest] = useState<TradingWorkbenchLookupRequest | null>(null);
    const [detailSearchVersion, setDetailSearchVersion] = useState(0);
    const [detailOpen, setDetailOpen] = useState(false);
    const closeOrderDetail = () => {
        setDetailOpen(false);
        // 关闭深链接详情同时清除地址身份，避免刷新重开；保留账户范围供列表返回使用。
        if (searchParams.has('orderId')) {
            const next = new URLSearchParams(searchParams);
            next.delete('orderId');
            setSearchParams(next);
        }
    };
    const [activeAction, setActiveAction] = useState<ActionDrawer>(null);
    const [lastActionResult, setLastActionResult] = useState<OperationTriggerResponse | null>(null);

    const orderListQuery = useTradingOrderListQuery(
        canonicalPaperRunId ? null : submittedListRequest,
        listSearchVersion,
    );
    const detailQuery = useTradingWorkbenchLookupQuery(
        canonicalPaperRunId ? null : detailRequest,
        detailSearchVersion,
    );

    const accountContextReady = Boolean(
        selectedExchangeAccountId &&
            exchangeCode &&
            tradeEnv &&
            (!routedAccount ||
                (routedAccountQuery.data?.exchangeAccountId === selectedExchangeAccountId &&
                    routedAccountQuery.data.status === 'ACTIVE')),
    );
    const currentContextLabel = accountContextReady
        ? `${accountAlias} · ${exchangeCode} / ${tradeEnv}`
        : t('pages:noCanonicalAccountContextSelected');

    useEffect(() => {
        if (canonicalPaperRunId || !accountContextReady || !selectedExchangeAccountId) {
            setSubmittedListRequest(null);
            return;
        }
        setLastActionResult(null);
        setActiveAction(null);
        const nextRequest: TradingOrderListRequest = {
            accountId: selectedExchangeAccountId,
            venue: exchangeCode ?? undefined,
            environment: tradeEnv ?? undefined,
            page: 0,
            size: 20,
        };
        listForm.setFieldsValue({orderId: undefined, symbol: undefined, status: undefined});
        setSubmittedListRequest(nextRequest);
        setListSearchVersion((value) => value + 1);
    }, [
        accountContextReady,
        canonicalPaperRunId,
        exchangeCode,
        listForm,
        selectedExchangeAccountId,
        tradeEnv,
    ]);

    useEffect(() => {
        const orderId = searchParams.get('orderId');
        if (!canonicalPaperRunId && accountContextReady && orderId && selectedExchangeAccountId) {
            setDetailRequest({accountId: selectedExchangeAccountId, orderId});
            setDetailSearchVersion((value) => value + 1);
            setDetailOpen(true);
        } else {
            setDetailRequest(null);
            setDetailOpen(false);
        }
    }, [
        searchParams,
        canonicalPaperRunId,
        accountContextReady,
        selectedExchangeAccountId,
        exchangeCode,
        tradeEnv,
    ]);

    const orderColumns = useMemo<ColumnsType<OrderView>>(
        () => [
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
                render: (value: string) => (
                    <Tag color={value === 'LIVE' ? 'red' : 'blue'}>{value}</Tag>
                ),
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
                render: (value: string | null) => <NqAmountText exact value={value} />,
            },
            {
                title: t('pages:quantity'),
                dataIndex: 'quantity',
                key: 'quantity',
                width: 120,
                render: (value: string) => <NqAmountText exact value={value} />,
            },
            {
                title: t('pages:status'),
                dataIndex: 'status',
                key: 'status',
                width: 130,
                render: (value: string) => (
                    <Tag color={value === 'CANCEL_REQUESTED' ? 'orange' : 'blue'} title={value}>
                        {t(`pages:tradingActions.orderStatuses.${value}`, {defaultValue: value})}
                    </Tag>
                ),
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
                width: 190,
                render: (_, record) => (
                    <Space size={0}>
                        <Button type="link" onClick={() => openDetail(record)}>
                            {t('pages:viewDetails')}
                        </Button>
                        <Tooltip
                            title={
                                !canTrade
                                    ? t('pages:orderCancellation.readOnly')
                                    : !canRequestCancellation(record)
                                      ? t('pages:orderCancellation.stateUnavailable')
                                      : undefined
                            }
                        >
                            <span>
                                <Button
                                    type="link"
                                    disabled={
                                        !canTrade ||
                                        !accountContextReady ||
                                        record.accountId !== legacyAccountId ||
                                        record.venue !== exchangeCode ||
                                        !canRequestCancellation(record)
                                    }
                                    onClick={() => setCancelTarget(record)}
                                >
                                    {t('pages:cancelOrder')}
                                </Button>
                            </span>
                        </Tooltip>
                    </Space>
                ),
            },
        ],
        [
            pageI18n.resolvedLanguage,
            canTrade,
            accountContextReady,
            selectedExchangeAccountId,
            legacyAccountId,
            exchangeCode,
            tradeEnv,
        ],
    );

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

    const handleActionSuccess = (
        result: OperationTriggerResponse,
        options?: {close?: boolean; refetch?: boolean},
    ) => {
        // 请求结束前切换账户时，不把原账户回执显示到新账户，也不据此刷新新账户订单。
        if (
            useAccountContextStore.getState().selectedExchangeAccountId !==
            selectedExchangeAccountId
        )
            return;
        setLastActionResult(result);
        const feedback = tradingOperationFeedback(result);
        message[feedback.tone](t(`pages:tradingActions.feedback.${feedback.key}`));
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

    const lastFeedback = lastActionResult ? tradingOperationFeedback(lastActionResult) : null;

    if (canonicalPaperRunId)
        return (
            <NqPageScaffold>
                <NqPageHeader
                    title={t('pages:simReadOnlyContext')}
                    description={t('pages:simCanonical')}
                />
                <Card>
                    <CanonicalSimFacts paperRunId={canonicalPaperRunId} details />
                </Card>
            </NqPageScaffold>
        );

    return (
        <>
            <NqPageScaffold>
                {routedAccount && !routedAccountId && (
                    <Alert type="error" message={t('objectSearch.invalidIdentity')} />
                )}
                {routedAccountQuery.error && (
                    <Alert
                        type="error"
                        message={formatApiError(routedAccountQuery.error as AppApiError)}
                    />
                )}
                {routedAccountQuery.data?.status === 'DISABLED' && (
                    <Alert type="error" message={t('objectSearch.unavailableAccount')} />
                )}
                {legacyAlias ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:tradeValidationIsATransitionalEntry')}
                        description={t(
                            'pages:theTradingWorkbenchIsAvailableAtTradingTheLegacyPathRemainsForCompatibility',
                        )}
                    />
                ) : null}

                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('pages:tradingWorkbench')}
                        description={t(
                            'pages:queryOrdersAndViewDetailsInTheExchangeaccountidContextWithSimLiveAndPrerequisiteRiskControlsVisible',
                        )}
                        badge="Trading"
                    />
                </Card>

                <Alert
                    type="warning"
                    showIcon
                    message={t('boundary.runtime')}
                    description={t('pages:tradingActions.liveBoundary')}
                    action={
                        <Button
                            size="small"
                            onClick={() => setActiveAction('preflight')}
                            disabled={!accountContextReady}
                        >
                            {t('pages:preOrderChecks')}
                        </Button>
                    }
                />

                <Card className="page-section" bordered={false} title={t('pages:accountContext')}>
                    {accountContextReady ? (
                        <Descriptions
                            bordered
                            size="small"
                            column={{xs: 1, sm: 2}}
                            styles={{content: {overflowWrap: 'anywhere'}}}
                        >
                            <Descriptions.Item label={t('pages:currentAccount')}>
                                {currentContextLabel}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:venue')}>
                                {exchangeCode}
                            </Descriptions.Item>
                            <Descriptions.Item label="SIM / LIVE">
                                <Tag color={tradeEnv === 'LIVE' ? 'red' : 'blue'}>{tradeEnv}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:scope')}>
                                {t('pages:tradingActions.accountScope')}
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Alert
                            type="warning"
                            showIcon
                            message={t('pages:noCanonicalAccountContextSelected')}
                            description={t(
                                'pages:selectAnExchangeAccountInTheHeaderOrAccountSettingsFirstQueriesAndWritesRequireAnAccountContext',
                            )}
                        />
                    )}
                </Card>

                <ManualOrderWorkspace
                    contextReady={accountContextReady}
                    onRecorded={(order) => {
                        setLastActionResult(null);
                        if (!selectedExchangeAccountId) return;
                        setSubmittedListRequest({
                            accountId: selectedExchangeAccountId,
                            orderId: order.orderId,
                            venue: exchangeCode ?? undefined,
                            environment: tradeEnv ?? undefined,
                            page: 0,
                            size: 20,
                        });
                        setListSearchVersion((value) => value + 1);
                    }}
                    onView={(orderId) => {
                        if (!selectedExchangeAccountId) return;
                        setDetailRequest({accountId: selectedExchangeAccountId, orderId});
                        setDetailSearchVersion((value) => value + 1);
                        setDetailOpen(true);
                    }}
                />

                <OrderCancellationWorkspace
                    target={cancelTarget}
                    contextReady={accountContextReady}
                    onClose={() => setCancelTarget(null)}
                    onUpdated={() => {
                        setListSearchVersion((value) => value + 1);
                        setDetailSearchVersion((value) => value + 1);
                    }}
                    onView={(orderId) => {
                        if (!selectedExchangeAccountId) return;
                        setDetailRequest({accountId: selectedExchangeAccountId, orderId});
                        setDetailSearchVersion((value) => value + 1);
                        setDetailOpen(true);
                    }}
                />

                <Card
                    className="page-section"
                    bordered={false}
                    title={t('pages:orderSearch')}
                    extra={
                        <Space>
                            <Button
                                type="primary"
                                disabled={!accountContextReady}
                                onClick={() => listForm.submit()}
                            >
                                {t('pages:search')}
                            </Button>
                            <Button disabled={!accountContextReady} onClick={handleListReset}>
                                {t('pages:reset')}
                            </Button>
                        </Space>
                    }
                >
                    {/* 多张表单含同名字段时，用独立名称隔离控件ID，避免标签跨表单关联。 */}
                    <Form
                        name="trading-order-search"
                        form={listForm}
                        layout="vertical"
                        onFinish={handleListSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:orderId')} name="orderId">
                                    <Input placeholder={t('pages:optionalExactMatch')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:tradingPair')} name="symbol">
                                    <Input placeholder={t('pages:forExampleBtcUsdt')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label={t('pages:orderStatus')} name="status">
                                    <Select
                                        allowClear
                                        options={tradingOrderStatuses.map((value) => ({
                                            label: t(`pages:tradingActions.orderStatuses.${value}`),
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
                    extra={
                        orderListQuery.data ? (
                            <Typography.Text type="secondary">
                                {t('pages:total')}
                                {orderListQuery.data.total} {t('pages:records')}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {!accountContextReady ? (
                        <Empty
                            description={t(
                                'pages:ordersLoadAutomaticallyAfterAnAccountContextIsSelected',
                            )}
                        />
                    ) : orderListQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:failedToQueryOrders')}
                            description={formatApiError(orderListQuery.error as AppApiError)}
                            action={
                                <Button
                                    size="small"
                                    onClick={() => setListSearchVersion((value) => value + 1)}
                                >
                                    {t('pages:retry')}
                                </Button>
                            }
                        />
                    ) : (
                        <Table
                            rowKey="orderId"
                            columns={orderColumns}
                            dataSource={orderListQuery.data?.items ?? []}
                            loading={orderListQuery.isFetching}
                            pagination={{
                                current: (submittedListRequest?.page ?? 0) + 1,
                                pageSize: submittedListRequest?.size ?? 20,
                                total: orderListQuery.data?.total ?? 0,
                                showSizeChanger: true,
                                pageSizeOptions: [20, 50, 100],
                                onChange: (page, size) => {
                                    if (!submittedListRequest) return;
                                    // 翻页沿用已提交筛选，未提交的编辑值不能悄悄改变查询范围。
                                    setSubmittedListRequest({
                                        ...submittedListRequest,
                                        page: size === submittedListRequest.size ? page - 1 : 0,
                                        size,
                                    });
                                    setListSearchVersion((value) => value + 1);
                                },
                            }}
                            scroll={{x: 1500}}
                            locale={{
                                emptyText: t('pages:noMatchingOrdersInTheCurrentAccountContext'),
                            }}
                        />
                    )}
                </Card>

                <Card className="page-section" bordered={false} title={t('pages:actions2')}>
                    <Typography.Paragraph type="secondary">
                        {t('pages:tradingActions.maintenanceScope')}
                    </Typography.Paragraph>
                    <Space wrap>
                        <Button
                            disabled={!accountContextReady || !canTrade}
                            onClick={() => setActiveAction('reconcile')}
                        >
                            {t('pages:runReconciliation')}
                        </Button>
                        <Button
                            disabled={!accountContextReady || !canTrade}
                            onClick={() => setActiveAction('recovery')}
                        >
                            {t('pages:runRecovery')}
                        </Button>
                    </Space>
                    {lastActionResult && lastFeedback ? (
                        <Card
                            size="small"
                            style={{marginTop: 16}}
                            title={t('pages:latestActionFeedback')}
                        >
                            <Alert
                                showIcon
                                type={lastFeedback.tone}
                                data-testid="trading-operation-feedback"
                                message={t(`pages:tradingActions.feedback.${lastFeedback.key}`)}
                                description={
                                    lastFeedback.status
                                        ? t(
                                              `pages:tradingActions.orderStatuses.${lastFeedback.status}`,
                                          )
                                        : t('pages:tradingActions.verifyCurrentOrder')
                                }
                            />
                            <Collapse
                                size="small"
                                style={{marginTop: 12}}
                                items={[
                                    {
                                        key: 'receipt',
                                        label: t('pages:tradingActions.originalReceipt'),
                                        children: (
                                            <Descriptions
                                                bordered
                                                size="small"
                                                column={1}
                                                styles={{content: {overflowWrap: 'anywhere'}}}
                                            >
                                                <Descriptions.Item label={t('pages:action')}>
                                                    {lastActionResult.action}
                                                </Descriptions.Item>
                                                <Descriptions.Item label={t('pages:traceId')}>
                                                    {lastActionResult.traceId}
                                                </Descriptions.Item>
                                                <Descriptions.Item label={t('pages:resultSummary')}>
                                                    {lastActionResult.detail}
                                                </Descriptions.Item>
                                            </Descriptions>
                                        ),
                                    },
                                ]}
                            />
                        </Card>
                    ) : null}
                </Card>
                <Collapse
                    size="small"
                    items={[
                        {
                            key: 'runtime',
                            label: t('pages:tradingActions.runtimeDetails'),
                            children: (
                                <Space direction="vertical" style={{display: 'flex'}}>
                                    <Descriptions bordered size="small" column={1}>
                                        <Descriptions.Item label={t('pages:accountId')}>
                                            {selectedExchangeAccountId ?? '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:compatibleLegacyaccountid')}
                                        >
                                            {legacyAccountId ?? '—'}
                                        </Descriptions.Item>
                                    </Descriptions>
                                    <RuntimeGuardBanner variant="trading-workbench" />
                                </Space>
                            ),
                        },
                    ]}
                />
            </NqPageScaffold>

            <Drawer
                open={detailOpen}
                width={860}
                title={t('pages:orderDetails')}
                onClose={closeOrderDetail}
                destroyOnClose
            >
                {detailQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingOrderDetails')} />
                ) : detailQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadOrderDetails')}
                        description={formatApiError(detailQuery.error as AppApiError)}
                    />
                ) : detailQuery.data ? (
                    <OrderDetailContent result={detailQuery.data} />
                ) : null}
            </Drawer>

            <Drawer
                open={activeAction === 'preflight'}
                width={640}
                title={t('pages:preOrderChecks')}
                onClose={() => setActiveAction(null)}
                destroyOnClose
            >
                {accountContextReady && selectedExchangeAccountId && exchangeCode ? (
                    <TradingPreflightPanel
                        accountId={selectedExchangeAccountId}
                        exchangeCode={exchangeCode}
                    />
                ) : (
                    <Alert
                        type="warning"
                        message={t('pages:selectACanonicalAccountContextFirst')}
                    />
                )}
            </Drawer>

            <MaintenanceOperationDrawer
                kind={activeAction === 'reconcile' ? 'RECONCILE' : activeAction === 'recovery' ? 'RECOVERY' : null}
                defaultVenue={exchangeCode ?? 'OKX'}
                onClose={() => setActiveAction(null)}
                onCompleted={receipt => handleActionSuccess({
                    action: receipt.kind === 'RECONCILE' ? 'reconcileOnce' : 'recoveryRunOnce',
                    traceId: receipt.traceId,
                    detail: receipt.detail!,
                }, {refetch: true})}
            />
        </>
    );
}

function OrderDetailContent({result}: {result: TradingWorkbenchLookupResult}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Descriptions
                bordered
                column={{xs: 1, sm: 2}}
                size="small"
                styles={{content: {overflowWrap: 'anywhere'}}}
                title={t('pages:order')}
            >
                <Descriptions.Item label={t('pages:orderId')}>
                    {result.order.orderId}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:account')}>
                    {result.order.accountId}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:venue')}>{result.order.venue}</Descriptions.Item>
                <Descriptions.Item label="SIM / LIVE">
                    <Tag color={result.order.tradeEnv === 'LIVE' ? 'red' : 'blue'}>
                        {result.order.tradeEnv}
                    </Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:tradingPair')}>
                    {result.order.symbol}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:status')}>
                    <span title={result.order.status}>
                        {t(`pages:tradingActions.orderStatuses.${result.order.status}`, {
                            defaultValue: result.order.status,
                        })}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:side')}>
                    <span title={result.order.side}>
                        {t(`pages:tradingActions.sides.${result.order.side}`, {
                            defaultValue: result.order.side,
                        })}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:type')}>
                    <span title={result.order.type}>
                        {t(`pages:tradingActions.orderTypes.${result.order.type}`, {
                            defaultValue: result.order.type,
                        })}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:price')}>
                    {<NqAmountText exact value={result.order.price} />}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:quantity')}>
                    {<NqAmountText exact value={result.order.quantity} />}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:clientOrderId')}>
                    {result.order.clientOrderId}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:externalOrderId')}>
                    {result.order.externalOrderId || '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:createdAt')}>
                    {formatDateTime(result.order.createdAt)}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:updatedAt')}>
                    {formatDateTime(result.order.updatedAt)}
                </Descriptions.Item>
            </Descriptions>
            {result.latestTrade ? (
                <Descriptions
                    bordered
                    column={{xs: 1, sm: 2}}
                    size="small"
                    styles={{content: {overflowWrap: 'anywhere'}}}
                    title={t('pages:trade')}
                >
                    <Descriptions.Item label={t('pages:tradeId')}>
                        {result.latestTrade.tradeId}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:exchangeTradeId')}>
                        {result.latestTrade.exchangeTradeId || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:price')}>
                        {<NqAmountText exact value={result.latestTrade.price} />}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:quantity')}>
                        {<NqAmountText exact value={result.latestTrade.quantity} />}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:fee')}>
                        {<NqAmountText exact value={result.latestTrade.fee} />}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:feeCurrency')}>
                        {result.latestTrade.feeCurrency || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:tradeTime')}>
                        {formatDateTime(result.latestTrade.tradeTs)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:traceId')}>
                        {result.latestTrade.traceId}
                    </Descriptions.Item>
                </Descriptions>
            ) : (
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:noLatestTradeIsAvailableForThisOrder')}
                />
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
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:noBalanceSnapshotIsAvailableForThisAccount')}
                />
            )}
            {result.position ? (
                <Descriptions
                    bordered
                    column={{xs: 1, sm: 2}}
                    size="small"
                    styles={{content: {overflowWrap: 'anywhere'}}}
                    title={t('pages:position')}
                >
                    <Descriptions.Item label={t('pages:accountId')}>
                        {result.position.accountId}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:venue')}>
                        {result.position.venue}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:tradingPair')}>
                        {result.position.symbol}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:positionQuantity')}>
                        {<NqAmountText exact value={result.position.quantity} />}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:availableQuantity')}>
                        {<NqAmountText exact value={result.position.availableQuantity} />}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('pages:averagePrice')}>
                        {<NqAmountText exact value={result.position.avgPrice} />}
                    </Descriptions.Item>
                </Descriptions>
            ) : (
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:noPositionSnapshotIsAvailableForThisAccountAndTradingPair')}
                />
            )}
        </Space>
    );
}

const balanceColumns: ColumnsType<AccountBalanceView> = [
    {
        get title() {
            return t('pages:currency');
        },
        dataIndex: 'currency',
        key: 'currency',
        width: 120,
    },
    {
        get title() {
            return t('pages:totalBalance');
        },
        dataIndex: 'balance',
        key: 'balance',
        width: 140,
        render: (value: string) => <NqAmountText exact value={value} />,
    },
    {
        get title() {
            return t('pages:available');
        },
        dataIndex: 'available',
        key: 'available',
        width: 140,
        render: (value: string) => <NqAmountText exact value={value} />,
    },
    {
        get title() {
            return t('pages:frozen');
        },
        dataIndex: 'frozen',
        key: 'frozen',
        width: 140,
        render: (value: string) => <NqAmountText exact value={value} />,
    },
    {
        get title() {
            return t('pages:snapshotTime');
        },
        dataIndex: 'snapshotTs',
        key: 'snapshotTs',
        width: 180,
        render: (value: string) => formatDateTime(value),
    },
];

function extractOrderId(detail: string): string | null {
    const matched = /order_id=([^,]+)/.exec(detail);
    return matched?.[1] ?? null;
}
