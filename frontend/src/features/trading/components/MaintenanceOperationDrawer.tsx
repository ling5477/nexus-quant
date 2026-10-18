import {useEffect, useRef, useState} from 'react';
import {
    Alert,
    Button,
    Descriptions,
    Drawer,
    Form,
    InputNumber,
    Select,
    Space,
    Typography,
} from 'antd';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {
    acknowledgeMaintenance,
    lookupMaintenance,
    maintenanceChanged,
    readMaintenance,
    submitMaintenance,
    type MaintenanceKind,
    type MaintenanceReceipt,
    type MaintenanceRequest,
    type PendingMaintenance,
} from '../maintenance-operations';

/** 刷新与切换账户只恢复原命令身份，不触发维护；旧异步结果不得跨登录主体展示。 */
export function MaintenanceOperationDrawer({
    kind,
    defaultVenue,
    onClose,
    onCompleted,
}: {
    kind: MaintenanceKind | null;
    defaultVenue: string;
    onClose: () => void;
    onCompleted: (receipt: MaintenanceReceipt) => void;
}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const token = useAuthStore((state) => state.accessToken);
    const [form] = useLocalizedForm<MaintenanceRequest>();
    const [record, setRecord] = useState<{
        owner?: number;
        pending: PendingMaintenance | null;
        error?: unknown;
    }>({pending: null});
    const [outcome, setOutcome] = useState<{
        owner?: number;
        token: string | null;
        receipt?: MaintenanceReceipt;
        error?: unknown;
    }>({token: null});
    const [busy, setBusy] = useState(false);
    const panel = useRef<HTMLDivElement>(null);
    const current = record.owner === owner ? record : {pending: null};
    const visibleOutcome = outcome.owner === owner && outcome.token === token ? outcome : undefined;
    const canMutate = useAuthStore((state) =>
        Boolean(
            state.currentUser?.roles.some((role) =>
                ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
            ),
        ),
    );
    useEffect(() => {
        const refresh = () => {
            try {
                setRecord({owner, pending: readMaintenance(owner)});
            } catch (error) {
                setRecord({owner, pending: null, error});
            }
        };
        refresh();
        window.addEventListener('storage', refresh);
        window.addEventListener(maintenanceChanged, refresh);
        return () => {
            window.removeEventListener('storage', refresh);
            window.removeEventListener(maintenanceChanged, refresh);
        };
    }, [owner]);
    const perform = async (work: () => Promise<MaintenanceReceipt | void>) => {
        if (!owner || busy || !canMutate) return;
        setBusy(true);
        setOutcome({owner, token});
        try {
            const receipt = await work();
            if (
                useAuthStore.getState().currentUser?.userId === owner &&
                useAuthStore.getState().accessToken === token
            ) {
                setOutcome({owner, token, receipt: receipt || undefined});
                if (receipt?.state === 'COMPLETED') onCompleted(receipt);
            }
        } catch (error) {
            setOutcome({owner, token, error});
        } finally {
            setBusy(false);
        }
    };
    const error = current.error || visibleOutcome?.error;
    const code = (error as {code?: string})?.code;
    const errorText = code?.startsWith('MAINTENANCE_')
        ? t(`maintenanceOperations.errors.${code}`, {
              defaultValue: t('maintenanceOperations.unconfirmed'),
          })
        : error
          ? formatApiError(error as AppApiError)
          : undefined;
    const receipt = visibleOutcome?.receipt;
    const pending = current.pending;
    useEffect(() => {
        // 原操作按钮在提交或确认后会卸载；焦点落到body时将其交还当前操作，保持Esc和键盘可用。
        if (kind && !busy && document.activeElement === document.body) {
            panel.current
                ?.querySelector<HTMLButtonElement>('button[data-maintenance-primary]')
                ?.focus();
        }
    }, [kind, busy, pending?.operationId]);
    const title = kind === 'RECOVERY' ? t('runRecovery') : t('runReconciliation');
    return (
        <Drawer open={kind !== null} title={title} width={520} onClose={onClose}>
            <div ref={panel}>
                <Space direction="vertical" style={{display: 'flex'}} size={16}>
                    <Alert
                        type="warning"
                        showIcon
                        message={t('tradingActions.maintenanceScope')}
                        description={t('tradingActions.maintenanceBoundary')}
                    />
                    {errorText && <Alert type="error" showIcon message={errorText} />}
                    {pending ? (
                        <>
                            <Alert
                                type={receipt?.state === 'COMPLETED' ? 'info' : 'warning'}
                                showIcon
                                message={
                                    pending.rejected
                                        ? t('maintenanceOperations.rejected')
                                        : receipt?.state === 'COMPLETED'
                                          ? t('maintenanceOperations.completed')
                                          : t('maintenanceOperations.unconfirmed')
                                }
                                description={t('maintenanceOperations.noRetry')}
                            />
                            <Descriptions
                                bordered
                                size="small"
                                column={1}
                                styles={{content: {overflowWrap: 'anywhere'}}}
                            >
                                <Descriptions.Item
                                    label={t('maintenanceOperations.originalRequest')}
                                >
                                    <Typography.Text copyable>
                                        {pending.operationId}
                                    </Typography.Text>
                                </Descriptions.Item>
                                <Descriptions.Item label={t('type')}>
                                    {pending.request.kind === 'RECONCILE'
                                        ? t('runReconciliation')
                                        : t('runRecovery')}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('venue')}>
                                    {pending.request.venue}
                                </Descriptions.Item>
                                {pending.request.limit != null && (
                                    <Descriptions.Item label={t('scanLimit')}>
                                        {pending.request.limit}
                                    </Descriptions.Item>
                                )}
                                {receipt && (
                                    <Descriptions.Item label={t('status')}>
                                        {receipt.state}
                                    </Descriptions.Item>
                                )}
                                {receipt?.detail && (
                                    <Descriptions.Item label={t('maintenanceOperations.result')}>
                                        {receipt.detail}
                                    </Descriptions.Item>
                                )}
                                {receipt?.traceId && (
                                    <Descriptions.Item label="Trace ID">
                                        {receipt.traceId}
                                    </Descriptions.Item>
                                )}
                            </Descriptions>
                            <Space wrap>
                                <Button
                                    data-maintenance-primary
                                    disabled={busy || !canMutate}
                                    loading={busy}
                                    onClick={() => void perform(() => lookupMaintenance(pending))}
                                >
                                    {t('maintenanceOperations.queryOriginal')}
                                </Button>
                                {(receipt?.state === 'COMPLETED' || pending.rejected) && (
                                    <Button
                                        disabled={busy || !canMutate}
                                        onClick={() =>
                                            void perform(() => acknowledgeMaintenance(pending))
                                        }
                                    >
                                        {t('maintenanceOperations.acknowledge')}
                                    </Button>
                                )}
                            </Space>
                        </>
                    ) : (
                        <Form
                            key={`${owner}:${kind}:${defaultVenue}`}
                            form={form}
                            layout="vertical"
                            initialValues={{venue: defaultVenue, limit: 100}}
                            onFinish={(values) =>
                                void perform(() =>
                                    submitMaintenance(owner!, {
                                        kind: kind!,
                                        venue: values.venue,
                                        ...(kind === 'RECONCILE' ? {limit: values.limit} : {}),
                                    }),
                                )
                            }
                        >
                            <Form.Item label={t('venue')} name="venue" rules={[{required: true}]}>
                                <Select
                                    options={[
                                        {label: 'OKX', value: 'OKX'},
                                        {label: 'BINANCE', value: 'BINANCE'},
                                    ]}
                                />
                            </Form.Item>
                            {kind === 'RECONCILE' && (
                                <Form.Item
                                    label={t('scanLimit')}
                                    name="limit"
                                    rules={[{required: true, type: 'integer', min: 1, max: 1000}]}
                                >
                                    <InputNumber
                                        min={1}
                                        max={1000}
                                        precision={0}
                                        style={{width: '100%'}}
                                    />
                                </Form.Item>
                            )}
                            <Space>
                                <Button
                                    type="primary"
                                    data-maintenance-primary
                                    htmlType="submit"
                                    disabled={!canMutate || !owner || Boolean(current.error)}
                                    loading={busy}
                                >
                                    {title}
                                </Button>
                                <Button onClick={onClose}>{t('cancel')}</Button>
                            </Space>
                        </Form>
                    )}
                </Space>
            </div>
        </Drawer>
    );
}
