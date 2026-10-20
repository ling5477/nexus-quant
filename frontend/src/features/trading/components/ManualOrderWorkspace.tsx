import {useEffect, useRef, useState} from 'react';
import {useMutation} from '@tanstack/react-query';
import {
    Alert,
    App,
    Button,
    Card,
    Collapse,
    Col,
    Descriptions,
    Drawer,
    Form,
    Input,
    InputNumber,
    Row,
    Select,
    Space,
    Typography,
} from 'antd';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {OrderView} from '../types/trading-workbench';
import {
    orderSubmission,
    orderSubmissionChanged,
    readOrderSubmission,
    type ManualOrderInput,
} from '../order-submission';

/** 刷新只读取本主体原请求；跨标签页同步后仍不自动查询或重发交易。 */
function useSavedSubmission(owner: number | undefined) {
    const [state, setState] = useState(() => ({owner, value: readOrderSubmission(owner)}));
    useEffect(() => {
        const refresh = () => setState({owner, value: readOrderSubmission(owner)});
        refresh();
        window.addEventListener(orderSubmissionChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(orderSubmissionChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    return state.owner === owner ? state.value : readOrderSubmission(owner);
}

/** 人工委托不接受策略内部身份；UNKNOWN保留原输入，正式查询确认后才允许清理本地记录。 */
export function ManualOrderWorkspace({
    contextReady,
    onRecorded,
    onView,
}: {
    contextReady: boolean;
    onRecorded: (order: OrderView) => void;
    onView: (orderId: string) => void;
}) {
    const {t} = useTranslation('pages');
    const {modal} = App.useApp();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const token = useAuthStore(selectSessionIdentity);
    const roles = useAuthStore((state) => state.currentUser?.roles);
    const account = useAccountContextStore((state) => state.selectedExchangeAccountId);
    const alias = useAccountContextStore((state) => state.accountAlias);
    const venue = useAccountContextStore((state) => state.exchangeCode);
    const environment = useAccountContextStore((state) => state.tradeEnv);
    const allowed = Boolean(
        roles?.some((role) => ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role)),
    );
    const writable = contextReady && environment === 'SIM' && allowed;
    const saved = useSavedSubmission(owner);
    const pending = saved.kind === 'saved' ? saved.pending : null;
    const sameAccount =
        pending?.request.accountId === account &&
        pending.request.venue === venue &&
        environment === 'SIM';
    const [open, setOpen] = useState(false);
    const [form] = useLocalizedForm<ManualOrderInput>();
    const formRegionRef = useRef<HTMLDivElement>(null);
    const orderType = Form.useWatch('orderType', form) ?? 'LIMIT';
    const isCurrent = () =>
        useAuthStore.getState().currentUser?.userId === owner &&
        selectSessionIdentity(useAuthStore.getState()) === token &&
        useAccountContextStore.getState().selectedExchangeAccountId === account;
    const accept = (order: OrderView) => {
        if (!isCurrent()) return;
        setOpen(false);
        onRecorded(order);
    };
    const submit = useMutation({
        mutationFn: orderSubmission.submit,
        retry: false,
        onSuccess: accept,
    });
    const lookup = useMutation({
        mutationFn: orderSubmission.lookup,
        retry: false,
        onSuccess: accept,
    });
    const resume = useMutation({
        mutationFn: orderSubmission.resume,
        retry: false,
        onSuccess: accept,
    });
    const acknowledge = useMutation({mutationFn: orderSubmission.acknowledge, retry: false});
    const busy =
        submit.isPending ||
        lookup.isPending ||
        resume.isPending ||
        acknowledge.isPending ||
        pending?.phase === 'SUBMITTING';
    useEffect(() => {
        // 提交按钮禁用会令浏览器焦点掉回body；只接回丢失焦点，保留关闭按钮等现有焦点。
        if (open && document.activeElement === document.body) formRegionRef.current?.focus();
    }, [open, busy, submit.error]);
    const missing = lookup.isError && (lookup.error as AppApiError).status === 404;
    const confirmed = pending?.phase === 'CONFIRMED';
    const latestOrder = [lookup.data, resume.data, submit.data].find(
        (order) => order?.orderId === pending?.orderId,
    );
    useEffect(() => {
        setOpen(false);
        submit.reset();
        lookup.reset();
        resume.reset();
        acknowledge.reset();
    }, [owner, token, account, submit.reset, lookup.reset, resume.reset, acknowledge.reset]);
    useEffect(() => {
        lookup.reset();
        resume.reset();
        acknowledge.reset();
    }, [pending?.request.clientOrderId, lookup.reset, resume.reset, acknowledge.reset]);
    const describe = (failure: unknown) => {
        const problem = failure as AppApiError;
        const messages: Record<string, string> = {
            ORDER_SUBMISSION_AMOUNT_INVALID: 'invalidAmount',
            ORDER_SUBMISSION_INPUT_INVALID: 'invalidInput',
            ORDER_SUBMISSION_PENDING: 'pendingHelp',
            ORDER_SUBMISSION_STORAGE_UNAVAILABLE: 'storageUnavailable',
            ORDER_SUBMISSION_COORDINATION_UNAVAILABLE: 'coordinationUnavailable',
            ORDER_SUBMISSION_IDENTITY_UNAVAILABLE: 'identityUnavailable',
            ORDER_SUBMISSION_SCOPE_UNAVAILABLE: 'readOnly',
            ORDER_SUBMISSION_SCOPE_CHANGED: 'scopeChanged',
            ORDER_SUBMISSION_ACCOUNT_UNAVAILABLE: 'accountUnavailable',
            ORDER_SUBMISSION_LOOKUP_REQUIRED: 'missing',
            ORDER_SUBMISSION_RESPONSE_INVALID: 'invalidResponse',
        };
        return messages[problem.code]
            ? t(`orderSubmission.${messages[problem.code]}`)
            : formatApiError(problem);
    };
    const failures = (
        confirmed
            ? [acknowledge.error]
            : [submit.error, missing ? null : lookup.error, resume.error, acknowledge.error]
    ).filter(Boolean);
    const canNew = writable && saved.kind === 'none' && !busy;
    return (
        <>
            <Card
                className="page-section"
                bordered={false}
                title={t('orderSubmission.title')}
                extra={
                    <Button
                        type="primary"
                        disabled={!canNew}
                        onClick={() => {
                            form.resetFields();
                            submit.reset();
                            setOpen(true);
                        }}
                    >
                        {t('tradingActions.placeOrder')}
                    </Button>
                }
            >
                <Space direction="vertical" size={12} style={{width: '100%'}}>
                    <Typography.Text type="secondary">{t('orderSubmission.help')}</Typography.Text>
                    {!writable && (
                        <Typography.Text type="warning">
                            {t(
                                environment === 'LIVE'
                                    ? 'tradingActions.liveBoundary'
                                    : !allowed
                                      ? 'orderSubmission.readOnly'
                                      : 'selectACanonicalAccountContextFirst',
                            )}
                        </Typography.Text>
                    )}
                    {saved.kind === 'unavailable' && (
                        <Alert
                            type="error"
                            showIcon
                            message={t('orderSubmission.storageUnavailable')}
                        />
                    )}
                    {pending && (
                        <Alert
                            showIcon
                            type={confirmed ? 'info' : 'warning'}
                            message={t(
                                confirmed ? 'orderSubmission.recorded' : 'orderSubmission.unknown',
                            )}
                            description={
                                <Space direction="vertical" size={12} style={{width: '100%'}}>
                                    <Typography.Text>
                                        {t(
                                            confirmed
                                                ? 'orderSubmission.recordedHelp'
                                                : 'orderSubmission.pendingHelp',
                                        )}
                                    </Typography.Text>
                                    {confirmed && sameAccount && latestOrder && (
                                        <Typography.Text>
                                            {t('status')}：
                                            {t(
                                                `tradingActions.orderStatuses.${latestOrder.status}`,
                                            )}
                                        </Typography.Text>
                                    )}
                                    <Descriptions
                                        size="small"
                                        column={{xs: 1, sm: 2}}
                                        styles={{content: {overflowWrap: 'anywhere'}}}
                                    >
                                        <Descriptions.Item label={t('currentAccount')}>
                                            {pending.accountAlias} · {pending.request.venue} / SIM
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('tradingPair')}>
                                            {pending.request.symbol}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('side')}>
                                            {t(`tradingActions.sides.${pending.request.side}`)}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('orderType')}>
                                            {t(
                                                `tradingActions.orderTypes.${pending.request.orderType}`,
                                            )}
                                        </Descriptions.Item>
                                        {pending.request.price !== undefined && (
                                            <Descriptions.Item label={t('price')}>
                                                {pending.request.price}
                                            </Descriptions.Item>
                                        )}
                                        <Descriptions.Item label={t('quantity')}>
                                            {pending.request.quantity}
                                        </Descriptions.Item>
                                    </Descriptions>
                                    <Collapse
                                        size="small"
                                        items={[
                                            {
                                                key: 'identity',
                                                label: t('orderSubmission.identity'),
                                                children: (
                                                    <Space
                                                        direction="vertical"
                                                        style={{overflowWrap: 'anywhere'}}
                                                    >
                                                        <Typography.Text copyable>
                                                            {pending.request.clientOrderId}
                                                        </Typography.Text>
                                                        {pending.orderId && (
                                                            <Typography.Text copyable>
                                                                {pending.orderId}
                                                            </Typography.Text>
                                                        )}
                                                    </Space>
                                                ),
                                            },
                                        ]}
                                    />
                                    {!sameAccount && (
                                        <Typography.Text type="warning">
                                            {t('orderSubmission.switchAccount')}
                                        </Typography.Text>
                                    )}
                                    {missing && !confirmed && (
                                        <Typography.Text>
                                            {t('orderSubmission.missing')}
                                        </Typography.Text>
                                    )}
                                    <Space wrap>
                                        <Button
                                            loading={lookup.isPending}
                                            disabled={busy || !sameAccount || !contextReady}
                                            onClick={() => lookup.mutate(pending)}
                                        >
                                            {t('orderSubmission.lookup')}
                                        </Button>
                                        {confirmed && (
                                            <>
                                                <Button
                                                    disabled={busy || !sameAccount}
                                                    onClick={() =>
                                                        pending.orderId && onView(pending.orderId)
                                                    }
                                                >
                                                    {t('orderSubmission.view')}
                                                </Button>
                                                <Button
                                                    loading={acknowledge.isPending}
                                                    disabled={busy || !sameAccount}
                                                    onClick={() =>
                                                        acknowledge.mutate(pending, {
                                                            onSuccess: () => {
                                                                if (isCurrent()) {
                                                                    submit.reset();
                                                                    lookup.reset();
                                                                    resume.reset();
                                                                }
                                                            },
                                                        })
                                                    }
                                                >
                                                    {t('orderSubmission.acknowledge')}
                                                </Button>
                                            </>
                                        )}
                                        {!confirmed && (
                                            <Button
                                                loading={resume.isPending}
                                                disabled={
                                                    busy || !sameAccount || !writable || !missing
                                                }
                                                onClick={() =>
                                                    modal.confirm({
                                                        title: t('orderSubmission.resumeTitle'),
                                                        content: t('orderSubmission.resumeHelp'),
                                                        okText: t('orderSubmission.resume'),
                                                        cancelText: t('cancel'),
                                                        onOk: () => resume.mutateAsync(pending),
                                                    })
                                                }
                                            >
                                                {t('orderSubmission.resume')}
                                            </Button>
                                        )}
                                    </Space>
                                </Space>
                            }
                        />
                    )}
                    {failures.map((failure, index) => (
                        <Alert key={index} type="error" showIcon message={describe(failure)} />
                    ))}
                </Space>
            </Card>
            <Drawer
                open={open}
                width={640}
                title={t('tradingActions.placeOrder')}
                onClose={() => setOpen(false)}
                destroyOnClose
            >
                <div
                    ref={formRegionRef}
                    tabIndex={-1}
                    role="region"
                    aria-label={t('tradingActions.placeOrder')}
                >
                    <Alert
                        type="info"
                        showIcon
                        message={`${alias} · ${venue} / ${environment}`}
                        description={t('orderSubmission.help')}
                        style={{marginBottom: 16}}
                    />
                    <Form
                        name="trading-manual-order"
                        form={form}
                        layout="vertical"
                        initialValues={{side: 'BUY', orderType: 'LIMIT'}}
                        onFinish={(values) => submit.mutate(values)}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} sm={12}>
                                <Form.Item
                                    label={t('tradingPair')}
                                    name="symbol"
                                    rules={[{required: true, message: t('enterASymbol')}]}
                                >
                                    <Input maxLength={64} placeholder={t('forExampleBtcUsdt')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} sm={12}>
                                <Form.Item
                                    label={t('side')}
                                    name="side"
                                    rules={[{required: true, message: t('selectASide')}]}
                                >
                                    <Select
                                        options={['BUY', 'SELL'].map((value) => ({
                                            value,
                                            label: t(`tradingActions.sides.${value}`),
                                        }))}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} sm={12}>
                                <Form.Item
                                    label={t('orderType')}
                                    name="orderType"
                                    rules={[{required: true, message: t('selectAnOrderType')}]}
                                >
                                    <Select
                                        options={['LIMIT', 'MARKET'].map((value) => ({
                                            value,
                                            label: t(`tradingActions.orderTypes.${value}`),
                                        }))}
                                    />
                                </Form.Item>
                            </Col>
                            {orderType === 'LIMIT' && (
                                <Col xs={24} sm={12}>
                                    <Form.Item
                                        label={t('price')}
                                        name="price"
                                        preserve={false}
                                        rules={[
                                            {
                                                required: true,
                                                message: t('orderSubmission.invalidAmount'),
                                            },
                                        ]}
                                    >
                                        <InputNumber<string>
                                            stringMode
                                            min="0.00000001"
                                            step="0.00000001"
                                            style={{width: '100%'}}
                                        />
                                    </Form.Item>
                                </Col>
                            )}
                            <Col xs={24} sm={12}>
                                <Form.Item
                                    label={t('quantity')}
                                    name="quantity"
                                    rules={[{required: true, message: t('enterAQuantity')}]}
                                >
                                    <InputNumber<string>
                                        stringMode
                                        min="0.00000001"
                                        step="0.00000001"
                                        style={{width: '100%'}}
                                    />
                                </Form.Item>
                            </Col>
                        </Row>
                        {submit.error && (
                            <Alert
                                style={{marginBottom: 16}}
                                type="error"
                                showIcon
                                message={describe(submit.error)}
                            />
                        )}
                        {pending && !confirmed && (
                            <Alert
                                style={{marginBottom: 16}}
                                type="warning"
                                showIcon
                                message={t('orderSubmission.unknown')}
                                description={t('orderSubmission.pendingHelp')}
                            />
                        )}
                        <Space wrap>
                            <Button
                                type="primary"
                                htmlType="submit"
                                loading={submit.isPending}
                                disabled={!canNew}
                            >
                                {t('tradingActions.submitOrder')}
                            </Button>
                            <Button onClick={() => setOpen(false)}>{t('cancel')}</Button>
                        </Space>
                    </Form>
                </div>
            </Drawer>
        </>
    );
}
