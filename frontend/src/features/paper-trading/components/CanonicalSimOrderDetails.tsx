import {
    Alert,
    Button,
    Descriptions,
    Drawer,
    Popconfirm,
    Space,
    Table,
    Tabs,
    Typography,
} from 'antd';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {paperTradingQueryKeys, runQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import {NqAmountText} from '@/components/nq';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {useAuthStore} from '@/store/auth-store';
import {ledgerPostingRole} from '../ledger-posting-role';

/** 精确详情独立读取；列表窗口之外的订单也能定位，失败时不显示其他订单。 */
export function CanonicalSimOrderDetails({
    paperRunId,
    orderId,
    onClose,
}: {
    paperRunId: string;
    orderId: string | null;
    onClose: () => void;
}) {
    const {t} = useTranslation('pages');
    const cache = useQueryClient(),
        roles = useAuthStore((s) => s.currentUser?.roles ?? []);
    const canRefresh = roles.some((role) =>
        ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role.toUpperCase()),
    );
    const cancel = useMutation({
        mutationFn: (identity: {paperRunId: string; orderId: string}) =>
            paperTradingApi.cancelSimOrder(identity.paperRunId, identity.orderId),
        retry: false,
        onSettled: async (_, __, identity) => {
            await cache.invalidateQueries({
                queryKey: paperTradingQueryKeys.canonicalOrder(
                    identity.paperRunId,
                    identity.orderId,
                ),
            });
            await cache.invalidateQueries({
                queryKey: paperTradingQueryKeys.canonicalFacts(identity.paperRunId),
            });
            await cache.invalidateQueries({queryKey: runQueryKeys.all});
        },
    });
    const refresh = useMutation({
        mutationFn: (identity: {paperRunId: string; orderId: string}) =>
            paperTradingApi.refreshSimExecution(identity.paperRunId, identity.orderId),
        onSuccess: async (_, identity) => {
            await cache.invalidateQueries({
                queryKey: paperTradingQueryKeys.canonicalOrder(
                    identity.paperRunId,
                    identity.orderId,
                ),
            });
            await cache.invalidateQueries({queryKey: ['object-search']});
            await cache.invalidateQueries({queryKey: runQueryKeys.all});
        },
    });
    const query = useQuery({
        queryKey: paperTradingQueryKeys.canonicalOrder(paperRunId, orderId ?? ''),
        queryFn: () => paperTradingApi.strategySimOrder(paperRunId, orderId!),
        enabled: Boolean(orderId),
        retry: false,
    });
    const details =
        !query.isError &&
        query.data?.paperRunId === paperRunId &&
        query.data.order.order_id === orderId
            ? query.data
            : null;
    const currentCancel =
        cancel.variables?.paperRunId === paperRunId && cancel.variables.orderId === orderId;
    // 同订单未知结果期间只提供查询，不能因超时或重开详情自动再撤一次。
    const cancelUnknown =
        currentCancel &&
        cancel.isError &&
        (!(cancel.error as AppApiError).status ||
            [408, 409].includes((cancel.error as AppApiError).status) ||
            (cancel.error as AppApiError).status >= 500);
    const cancelConfirmed =
        details &&
        ['CANCELLED', 'FILLED', 'REJECTED', 'RISK_REJECTED'].includes(String(details.order.status));
    const cells = (value: unknown, key: string) =>
        key.startsWith('exact_') ? (
            <NqAmountText exact value={value as string | null} />
        ) : ['created_at', 'updated_at', 'ts'].includes(key) ? (
            formatDateTime(value as string)
        ) : (
            String(value ?? '—')
        );
    const groups = [
        ['trades', ['trade_id', 'exact_qty', 'exact_price', 'exact_fee', 'fee_currency', 'ts']],
        ['ledgerEntries', ['postingRole', 'currency', 'exact_delta', 'ref_id', 'ts']],
        ['riskEvents', ['risk_event_id', 'rule_id', 'decision', 'reason', 'created_at']],
    ] as const;
    return (
        <Drawer
            rootClassName="canonical-order-drawer"
            title={t('pages:orderDetails')}
            open={Boolean(orderId)}
            width={860}
            onClose={onClose}
            destroyOnHidden
        >
            {query.isPending && <Typography.Text>{t('pages:loading')}</Typography.Text>}
            {query.isError && (
                <Alert
                    type="error"
                    showIcon
                    message={formatApiError(query.error as AppApiError)}
                    action={!cancelUnknown || cancelConfirmed ? (
                        <Button onClick={() => void query.refetch()}>{t('pages:retry')}</Button>
                    ) : undefined}
                />
            )}
            {/* 原订单暂不可读时仍保留撤单未知说明，查询失败不能变成撤单失败。 */}
            {cancelUnknown && !cancelConfirmed && (
                <Alert
                    type="warning"
                    message={t('pages:simCancelUnknown')}
                    action={
                        <Button
                            loading={query.isFetching}
                            onClick={() => void query.refetch()}
                        >
                            {t('pages:querySimCancelResult')}
                        </Button>
                    }
                />
            )}
            {details && (
                <Space direction="vertical" style={{width: '100%'}} size={16}>
                    <Descriptions
                        bordered
                        size="small"
                        column={{xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2}}
                    >
                        {[
                            'order_id',
                            'symbol',
                            'side',
                            'type',
                            'status',
                            'exact_qty',
                            'exact_price',
                            'reason',
                            'created_at',
                            'updated_at',
                            'strategy_run_id',
                            'strategy_run_status',
                        ].map((key) => (
                            <Descriptions.Item
                                key={key}
                                label={t(`pages:simColumn_${key.replace('exact_', '')}`)}
                                span={
                                    key.endsWith('_id')
                                        ? {xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2}
                                        : 1
                                }
                            >
                                <Typography.Text copyable={key.endsWith('_id')}>
                                    {cells(details.order[key], key)}
                                </Typography.Text>
                            </Descriptions.Item>
                        ))}
                    </Descriptions>
                    {canRefresh &&
                        ['ACCEPTED', 'SENT', 'PARTIALLY_FILLED', 'CANCEL_REJECTED'].includes(
                            String(details.order.status),
                        ) &&
                        (!cancelUnknown || cancelConfirmed) && (
                            <Popconfirm
                                title={t('pages:confirmCancelSimOrder')}
                                onConfirm={() =>
                                    cancel.mutateAsync({paperRunId, orderId: orderId!})
                                }
                            >
                                <Button danger loading={currentCancel && cancel.isPending}>
                                    {t('pages:cancelOrder')}
                                </Button>
                            </Popconfirm>
                        )}
                    {currentCancel && cancel.isError && !cancelUnknown && (
                        <Alert type="error" message={formatApiError(cancel.error as AppApiError)} />
                    )}
                    {canRefresh &&
                    details.order.strategy_run_id &&
                    ['FILLED', 'RISK_REJECTED', 'REJECTED', 'CANCELLED'].includes(
                        String(details.order.status),
                    ) &&
                    !['SUCCEEDED', 'FAILED'].includes(String(details.order.strategy_run_status)) ? (
                        <Button
                            loading={refresh.isPending}
                            onClick={() => refresh.mutate({paperRunId, orderId: orderId!})}
                        >
                            {t('pages:refreshSimExecution')}
                        </Button>
                    ) : null}
                    {refresh.isError && refresh.variables?.orderId === orderId && (
                        <Alert
                            type="error"
                            message={formatApiError(refresh.error as AppApiError)}
                        />
                    )}
                    {details.relatedRecordsLimited && (
                        <Alert type="warning" message={t('pages:simReadBounds')} />
                    )}
                    <Tabs
                        items={groups.map(([kind, keys]) => ({
                            key: kind,
                            label: t(`pages:simTable_${kind}`),
                            children: (
                                <>
                                    {kind === 'ledgerEntries' && (
                                        <Typography.Paragraph type="secondary">
                                            {t('pages:ledgerCashMeaning')}
                                        </Typography.Paragraph>
                                    )}
                                    <Table
                                        size="small"
                                        dataSource={details[kind]}
                                        rowKey={(row) =>
                                            String(
                                                row.trade_id ?? row.entry_id ?? row.risk_event_id,
                                            )
                                        }
                                        scroll={{x: 750}}
                                        pagination={{pageSize: 10}}
                                        columns={keys.map((key) => ({
                                            key,
                                            dataIndex: key,
                                            title: t(
                                                `pages:simColumn_${key.replace('exact_', '')}`,
                                            ),
                                            render: (
                                                value: unknown,
                                                row: Record<string, unknown>,
                                            ) =>
                                                key === 'postingRole'
                                                    ? t(
                                                          `pages:ledgerRole_${ledgerPostingRole(row)}`,
                                                      )
                                                    : cells(value, key),
                                        }))}
                                    />
                                </>
                            ),
                        }))}
                    />
                </Space>
            )}
        </Drawer>
    );
}
