import {Alert, Button, Space, Spin, Tag, Typography} from 'antd';
import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {readAccountPerformance} from '@/features/accounts/api/performance';
import {accountQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import {accountValuationCurrency, readCurrencyRegistry} from '../currencies';

/** 资产计价读取正式估值口径，不能从交易对名称推导，也不能把USDT改名成人民币或假装可配置汇率。 */
export function DefaultAssetCurrency({
    accountId,
    active,
    loading,
}: {
    accountId: number | null;
    active: boolean;
    loading: boolean;
}) {
    const {t} = useTranslation();
    const token = useAuthStore((state) => state.accessToken);
    const userId = useAuthStore((state) => state.currentUser?.userId);
    const registry = useQuery({
        queryKey: ['currency-registry', userId],
        enabled: Boolean(token),
        retry: false,
        queryFn: ({signal}) => readCurrencyRegistry(signal),
    });
    const period = useMemo(() => {
        const to = new Date();
        return {from: new Date(to.getTime() - 60000).toISOString(), to: to.toISOString()};
    }, [accountId]);
    const currency = useQuery({
        queryKey: accountQueryKeys.valuationCurrency(token, accountId, period.from, period.to),
        enabled: Boolean(token && accountId && active),
        // 账户身份或币种不匹配不能通过自动重试被掩盖；失败后由用户明确重读。
        retry: false,
        queryFn: async ({signal}) => {
            const result = await readAccountPerformance(accountId!, period.from, period.to, signal);
            if (
                result.summary.exchangeAccountId !== accountId ||
                result.summary.quoteCurrency !== 'USDT'
            )
                throw Error('ACCOUNT_VALUATION_CURRENCY_RESPONSE_MISMATCH');
            return accountValuationCurrency(result.summary);
        },
    });
    return (
        <Space direction="vertical" size={8} style={{width: '100%'}}>
            <Typography.Text strong>{t('defaultAssets.title')}</Typography.Text>
            {loading || (accountId && active && currency.isPending) ? (
                <Spin size="small" />
            ) : !accountId || !active ? (
                <Typography.Text type="secondary">{t('defaultAssets.noAccount')}</Typography.Text>
            ) : currency.error ? (
                <Alert
                    type="error"
                    message={t('defaultAssets.unavailable')}
                    action={
                        <Button
                            disabled={!token}
                            loading={currency.isFetching}
                            onClick={() => void currency.refetch()}
                        >
                            {t('preferences.reload')}
                        </Button>
                    }
                />
            ) : (
                <Tag>{currency.data ?? '—'}</Tag>
            )}
            <Typography.Text type="secondary">{t('defaultAssets.scope')}</Typography.Text>
            <details data-testid="currency-registry">
                <summary>{t('pages:currencyRegistry.title')}</summary>
                {registry.isPending ? (
                    <Spin size="small" />
                ) : registry.error ? (
                    <Alert
                        type="error"
                        message={t('pages:currencyRegistry.unavailable')}
                        action={
                            <Button
                                loading={registry.isFetching}
                                disabled={!token}
                                onClick={() => void registry.refetch()}
                            >
                                {t('preferences.reload')}
                            </Button>
                        }
                    />
                ) : (
                    <Space direction="vertical" size={8} style={{paddingTop: 8}}>
                        <Typography.Text type="secondary">
                            {t('pages:currencyRegistry.scope')}
                        </Typography.Text>
                        {registry.data?.currencies.map((item) => (
                            <Space wrap key={item.code}>
                                <Tag>{item.code}</Tag>
                                <Typography.Text>
                                    {t(`pages:currencyRegistry.kinds.${item.kind}`)}
                                </Typography.Text>
                                <Typography.Text
                                    type={item.valuationSupported ? undefined : 'secondary'}
                                >
                                    {t(
                                        item.valuationSupported
                                            ? 'pages:currencyRegistry.enabled'
                                            : item.displayOnly
                                              ? 'pages:currencyRegistry.displayOnly'
                                              : 'pages:currencyRegistry.reserved',
                                    )}
                                </Typography.Text>
                            </Space>
                        ))}
                    </Space>
                )}
            </details>
        </Space>
    );
}
