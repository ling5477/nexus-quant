import {useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@tanstack/react-query';
import {
    Alert,
    App,
    Button,
    Card,
    Collapse,
    Descriptions,
    Drawer,
    Form,
    Input,
    Space,
    Typography,
} from 'antd';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {tradingWorkbenchQueryKeys} from '@/api/query-keys';
import {NqAmountText} from '@/components/nq';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import {tradingWorkbenchApi} from '../api/trading-workbench';
import {
    cancellationCanBeAcknowledged,
    canRequestCancellation,
    orderCancellation,
    orderCancellationChanged,
    prepareOrderCancellation,
    readOrderCancellations,
    type PendingOrderCancellation,
} from '../order-cancellation';
import type {OrderView} from '../types/trading-workbench';

/** 只订阅本主体的有界原操作列表；刷新不触发任何交易请求。 */
function useSavedCancellations(owner?: number) {
    const [state, setState] = useState(() => ({owner, value: readOrderCancellations(owner)}));
    useEffect(() => {
        const refresh = () => setState({owner, value: readOrderCancellations(owner)});
        refresh();
        window.addEventListener(orderCancellationChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(orderCancellationChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    return state.owner === owner ? state.value : readOrderCancellations(owner);
}
function useCancellationError() {
    const {t} = useTranslation('pages');
    return (failure: unknown) => {
        const problem = failure as AppApiError;
        const messages: Record<string, string> = {
            ORDER_CANCELLATION_STORAGE_UNAVAILABLE: 'storageUnavailable',
            ORDER_CANCELLATION_ORIGINAL_CHANGED: 'originalChanged',
            ORDER_CANCELLATION_SCOPE_UNAVAILABLE: 'readOnly',
            ORDER_CANCELLATION_SCOPE_CHANGED: 'scopeChanged',
            ORDER_CANCELLATION_ACCOUNT_UNAVAILABLE: 'accountUnavailable',
            ORDER_CANCELLATION_STATE_UNAVAILABLE: 'stateUnavailable',
            ORDER_CANCELLATION_REASON_INVALID: 'reasonInvalid',
            ORDER_CANCELLATION_COORDINATION_UNAVAILABLE: 'coordinationUnavailable',
            ORDER_CANCELLATION_BUSY: 'busy',
            ORDER_CANCELLATION_PENDING: 'unknownHelp',
            ORDER_CANCELLATION_CAPACITY: 'capacity',
            ORDER_CANCELLATION_RESPONSE_INVALID: 'invalidResponse',
            ORDER_CANCELLATION_UNRESOLVED: 'unknownHelp',
        };
        return messages[problem.code]
            ? t(`orderCancellation.${messages[problem.code]}`)
            : formatApiError(problem);
    };
}

/** 单条原操作只允许查原订单；清理前再次正式读取，不能凭本地显示或404释放。 */
function CancellationRecord({
    pending,
    onUpdated,
    onView,
}: {
    pending: PendingOrderCancellation;
    onUpdated: (order: OrderView) => void;
    onView: (orderId: string) => void;
}) {
    const {t} = useTranslation('pages');
    const describe = useCancellationError();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const token = useAuthStore(selectSessionIdentity);
    const account = useAccountContextStore((state) => state.selectedExchangeAccountId);
    const venue = useAccountContextStore((state) => state.exchangeCode);
    const environment = useAccountContextStore((state) => state.tradeEnv);
    const sameAccount =
        owner === pending.ownerUserId &&
        account === pending.request.accountId &&
        venue === pending.target.venue &&
        environment === 'SIM';
    const accept = (order: OrderView) => {
        if (
            useAuthStore.getState().currentUser?.userId === owner &&
            selectSessionIdentity(useAuthStore.getState()) === token &&
            useAccountContextStore.getState().selectedExchangeAccountId === account
        )
            onUpdated(order);
    };
    const lookup = useMutation({
        mutationFn: () => orderCancellation.lookup(pending),
        retry: false,
        onSuccess: accept,
    });
    const acknowledge = useMutation({
        mutationFn: () => orderCancellation.acknowledge(pending),
        retry: false,
        onSuccess: accept,
    });
    useEffect(() => {
        lookup.reset();
        acknowledge.reset();
    }, [owner, token, account, lookup.reset, acknowledge.reset]);
    const busy = lookup.isPending || acknowledge.isPending || pending.phase === 'SUBMITTING';
    const order = lookup.data;
    const releasable = !!order && cancellationCanBeAcknowledged(pending, order);
    return (
        <Space direction="vertical" size={12} style={{width: '100%'}}>
            <Typography.Text>
                {t(releasable ? 'orderCancellation.checkedHelp' : 'orderCancellation.unknownHelp')}
            </Typography.Text>
            <Descriptions
                size="small"
                column={{xs: 1, sm: 2}}
                styles={{content: {overflowWrap: 'anywhere'}}}
            >
                <Descriptions.Item label={t('currentAccount')}>
                    {pending.accountAlias} · {pending.target.venue} / SIM
                </Descriptions.Item>
                <Descriptions.Item label={t('tradingPair')}>
                    {pending.target.symbol}
                </Descriptions.Item>
                <Descriptions.Item label={t('cancellationReason')}>
                    {pending.request.reason}
                </Descriptions.Item>
                {order && (
                    <Descriptions.Item label={t('orderCancellation.latestStatus')}>
                        {t(`tradingActions.orderStatuses.${order.status}`)}
                    </Descriptions.Item>
                )}
            </Descriptions>
            {order && (
                <Alert
                    showIcon
                    type={order.status === 'CANCELLED' ? 'success' : 'info'}
                    message={t(
                        order.status === 'CANCELLED'
                            ? 'orderCancellation.cancelled'
                            : releasable
                              ? 'orderCancellation.closed'
                              : 'orderCancellation.waiting',
                    )}
                />
            )}
            {!sameAccount && (
                <Typography.Text type="warning">
                    {t('orderCancellation.switchAccount')}
                </Typography.Text>
            )}
            <Collapse
                size="small"
                items={[
                    {
                        key: 'identity',
                        label: t('orderSubmission.identity'),
                        children: (
                            <Space direction="vertical">
                                <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                                    {pending.target.orderId}
                                </Typography.Text>
                                <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                                    {pending.target.clientOrderId}
                                </Typography.Text>
                                {pending.receipt && (
                                    <Typography.Text style={{overflowWrap: 'anywhere'}}>
                                        {pending.receipt.detail} · {pending.receipt.traceId}
                                    </Typography.Text>
                                )}
                            </Space>
                        ),
                    },
                ]}
            />
            {lookup.error && (
                <Alert
                    type="error"
                    showIcon
                    message={
                        (lookup.error as AppApiError).status === 404
                            ? t('orderCancellation.notFound')
                            : describe(lookup.error)
                    }
                />
            )}
            {acknowledge.error && (
                <Alert type="error" showIcon message={describe(acknowledge.error)} />
            )}
            <Space wrap>
                <Button
                    loading={lookup.isPending}
                    disabled={busy || !sameAccount}
                    onClick={() => lookup.mutate()}
                >
                    {t('orderCancellation.lookup')}
                </Button>
                <Button
                    disabled={busy || !sameAccount}
                    onClick={() => onView(pending.target.orderId)}
                >
                    {t('orderSubmission.view')}
                </Button>
                <Button
                    loading={acknowledge.isPending}
                    disabled={busy || !sameAccount || !releasable}
                    onClick={() => acknowledge.mutate()}
                >
                    {t('orderCancellation.acknowledge')}
                </Button>
            </Space>
        </Space>
    );
}

/** 列表选择稳定订单，确认前展示业务事实；发送、响应和当前状态分别表达，避免把200当撤单成功。 */
export function OrderCancellationWorkspace({
    target,
    contextReady,
    onClose,
    onUpdated,
    onView,
}: {
    target: OrderView | null;
    contextReady: boolean;
    onClose: () => void;
    onUpdated: (order: OrderView) => void;
    onView: (orderId: string) => void;
}) {
    const {t} = useTranslation('pages');
    const {modal} = App.useApp();
    const describe = useCancellationError();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const token = useAuthStore(selectSessionIdentity);
    const roles = useAuthStore((state) => state.currentUser?.roles);
    const account = useAccountContextStore((state) => state.selectedExchangeAccountId);
    const alias = useAccountContextStore((state) => state.accountAlias);
    const legacy = useAccountContextStore((state) => state.legacyAccountId);
    const venue = useAccountContextStore((state) => state.exchangeCode);
    const environment = useAccountContextStore((state) => state.tradeEnv);
    const writable =
        contextReady &&
        environment === 'SIM' &&
        !!roles?.some((role) =>
            ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
        );
    const saved = useSavedCancellations(owner);
    const records = saved.kind === 'saved' ? saved.records : [];
    const alreadyPending =
        !!target && records.some((item) => item.target.orderId === target.orderId);
    const [form] = useLocalizedForm<{reason: string}>();
    const region = useRef<HTMLDivElement>(null);
    const lookup = useQuery({
        queryKey: tradingWorkbenchQueryKeys.cancelTarget(owner, account, target?.orderId),
        queryFn: () => tradingWorkbenchApi.getOrder(target!.orderId),
        enabled: !!target && contextReady,
        retry: false,
        staleTime: 0,
        refetchOnMount: 'always',
    });
    const order = lookup.data;
    const submit = useMutation({
        mutationFn: orderCancellation.submit,
        retry: false,
        onSuccess: (value) => {
            if (
                useAuthStore.getState().currentUser?.userId === owner &&
                selectSessionIdentity(useAuthStore.getState()) === token &&
                useAccountContextStore.getState().selectedExchangeAccountId === account
            ) {
                onUpdated(value);
                onClose();
            }
        },
    });
    useEffect(() => {
        onClose();
        submit.reset();
    }, [owner, token, account, submit.reset]);
    useEffect(() => {
        form.resetFields();
        submit.reset();
    }, [target?.orderId, form, submit.reset]);
    useEffect(() => {
        if (target && document.activeElement === document.body) region.current?.focus();
    }, [target, lookup.isFetching, submit.isPending, submit.error, alreadyPending]);
    const canSubmit =
        writable &&
        !lookup.isFetching &&
        !submit.isPending &&
        !!order &&
        order.orderId === target?.orderId &&
        order.accountId === legacy &&
        order.venue === venue &&
        canRequestCancellation(order) &&
        !alreadyPending &&
        saved.kind === 'saved';
    return (
        <>
            {(records.length > 0 || saved.kind === 'unavailable') && (
                <Card
                    className="page-section"
                    bordered={false}
                    title={t('orderCancellation.pendingTitle', {count: records.length})}
                >
                    {saved.kind === 'unavailable' ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('orderCancellation.storageUnavailable')}
                        />
                    ) : (
                        <Collapse
                            defaultActiveKey={
                                records.length === 1 ? [records[0].target.orderId] : []
                            }
                            items={records.map((pending) => ({
                                key: pending.target.orderId,
                                label: `${pending.accountAlias} · ${pending.target.symbol} · …${pending.target.orderId.slice(-8)}`,
                                children: (
                                    <CancellationRecord
                                        pending={pending}
                                        onUpdated={onUpdated}
                                        onView={onView}
                                    />
                                ),
                            }))}
                        />
                    )}
                </Card>
            )}
            <Drawer
                open={!!target}
                width={600}
                title={t('cancelOrder')}
                onClose={onClose}
                destroyOnClose
            >
                <div ref={region} role="region" aria-label={t('cancelOrder')} tabIndex={-1}>
                    {lookup.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message={describe(lookup.error)}
                            action={
                                <Button
                                    loading={lookup.isFetching}
                                    onClick={() => void lookup.refetch()}
                                >
                                    {t('retry')}
                                </Button>
                            }
                        />
                    ) : order ? (
                        <Space direction="vertical" size={16} style={{width: '100%'}}>
                            <Alert
                                type="warning"
                                showIcon
                                message={t('orderCancellation.confirmHelp')}
                            />
                            <Descriptions
                                bordered
                                size="small"
                                column={{xs: 1, sm: 2}}
                                styles={{content: {overflowWrap: 'anywhere'}}}
                            >
                                <Descriptions.Item label={t('currentAccount')}>
                                    {alias} · {order.venue} / {order.tradeEnv}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('tradingPair')}>
                                    {order.symbol}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('side')}>
                                    {t(`tradingActions.sides.${order.side}`, {
                                        defaultValue: order.side,
                                    })}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('status')}>
                                    {t(`tradingActions.orderStatuses.${order.status}`)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('price')}>
                                    <NqAmountText exact value={order.price} />
                                </Descriptions.Item>
                                <Descriptions.Item label={t('quantity')}>
                                    <NqAmountText exact value={order.quantity} />
                                </Descriptions.Item>
                            </Descriptions>
                            {alreadyPending ? (
                                <Alert
                                    type="warning"
                                    showIcon
                                    message={t('orderCancellation.unknownHelp')}
                                />
                            ) : !writable ? (
                                <Alert
                                    type="warning"
                                    showIcon
                                    message={t('orderCancellation.readOnly')}
                                />
                            ) : (
                                !canRequestCancellation(order) && (
                                    <Alert
                                        type="info"
                                        showIcon
                                        message={t('orderCancellation.stateUnavailable')}
                                    />
                                )
                            )}
                            <Form
                                name="trading-order-cancellation"
                                form={form}
                                layout="vertical"
                                initialValues={{reason: ''}}
                                onFinish={({reason}) => {
                                    if (!order || !canSubmit) return;
                                    try {
                                        const prepared = prepareOrderCancellation(order, reason);
                                        // 错误由mutation保留并在抽屉展示；关闭确认框后只允许查原单，避免把确认按钮变成盲重试入口。
                                        modal.confirm({
                                            title: t('orderCancellation.confirmTitle'),
                                            content: t('orderCancellation.confirmScope', {
                                                account: alias,
                                                symbol: order.symbol,
                                            }),
                                            okText: t('submitCancellation'),
                                            cancelText: t('cancel'),
                                            // 原提交按钮在UNKNOWN时禁用；避免Modal恢复到已失效按钮后把焦点丢回body。
                                            focusTriggerAfterClose: false,
                                            onOk: async () => {
                                                await submit
                                                    .mutateAsync(prepared)
                                                    .catch(() => undefined);
                                            },
                                            afterClose: () => {
                                                // HookModal在afterClose后才卸载焦点节点；下一帧只接回丢失焦点，不覆盖用户的新焦点。
                                                requestAnimationFrame(() => {
                                                    if (document.activeElement === document.body)
                                                        region.current?.focus({
                                                            preventScroll: true,
                                                        });
                                                });
                                            },
                                        });
                                    } catch (failure) {
                                        modal.error({
                                            title: t('cancelOrder'),
                                            content: describe(failure),
                                        });
                                    }
                                }}
                            >
                                <Form.Item
                                    name="reason"
                                    label={t('cancellationReason')}
                                    rules={[
                                        {
                                            required: true,
                                            whitespace: true,
                                            message: t('enterACancellationReason'),
                                        },
                                    ]}
                                >
                                    <Input.TextArea
                                        maxLength={255}
                                        autoSize={{minRows: 2, maxRows: 4}}
                                        disabled={!canSubmit}
                                    />
                                </Form.Item>
                                {submit.error && (
                                    <Alert
                                        style={{marginBottom: 12}}
                                        type="error"
                                        showIcon
                                        message={describe(submit.error)}
                                    />
                                )}
                                <Space wrap>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={submit.isPending}
                                        disabled={!canSubmit}
                                    >
                                        {t('submitCancellation')}
                                    </Button>
                                    <Button onClick={onClose}>{t('cancel')}</Button>
                                </Space>
                            </Form>
                        </Space>
                    ) : (
                        <Typography.Text>{t('orderCancellation.loading')}</Typography.Text>
                    )}
                </div>
            </Drawer>
        </>
    );
}
