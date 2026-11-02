import {Alert, Button, Col, Collapse, Input, Row, Select, Space, Typography} from 'antd';
import {useEffect, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {accountsApi} from '@/features/accounts/api/accounts';
import {accountFeesApi, type AccountFeeObservation} from '@/features/accounts/api/fees';
import {formatAccountFeePercent} from '@/features/accounts/fee-display';
import {formatDateTimeMilliseconds} from '@/utils/formatters';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {
    accountFeeMode,
    directionalFeeVersion,
    discountAssetPolicy,
    discountAvailable,
    eligibleDiscountDatasets,
    executionObject,
    feePaymentPolicySpec,
    feeSourceMode,
    observedFeeSpec,
    receivedCommissionRates,
    validDiscountFraction,
} from './fee-source';
import {MarketdataDatasetPicker} from '@/features/marketdata/components/MarketdataDatasetPicker';
import {useDatasetDetailQuery} from '@/features/marketdata/hooks/useDatasetQueries';

const fields = [
    'feeRate',
    'slippageBps',
    'quantityStep',
    'priceTick',
    'minimumQuantity',
    'minimumNotional',
    'feeAssumptionVersion',
    'slippageAssumptionVersion',
] as const;
type Market = {symbol: string; exchangeCode: string; marketType: string};

/** 显式选择研究假设或当前观察模型；读取证据不会同步私有来源或自动选最新费率。 */
export function ExecutionSpecInput({
    value,
    onChange,
    market,
}: {
    value?: string;
    onChange?: (value: string) => void;
    market?: Market;
}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore((state) => state.currentUser?.userId);
    let spec: Record<string, unknown> = {};
    let invalid = false;
    try {
        spec = executionObject(value);
    } catch {
        invalid = true;
    }
    const actual = spec.costSource === accountFeeMode;
    const [picked, setPicked] = useState<AccountFeeObservation | null>(null),
        [before, setBefore] = useState<string>();
    const [now, setNow] = useState(Date.now);
    // 只更新有效期提示，关闭后释放计时器；不自动刷新或重新同步供应方费率。
    useEffect(() => {
        if (!actual) return;
        const timer = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(timer);
    }, [actual]);
    const accounts = useQuery({
        queryKey: ['fee-model-accounts', owner],
        queryFn: accountsApi.list,
        enabled: actual && Boolean(owner),
        retry: false,
    });
    const account = (accounts.data ?? []).find(
        (row) => String(row.exchangeAccountId) === spec.feeAccountId,
    );
    const supported = Boolean(
        market && ['OKX', 'BINANCE'].includes(market.exchangeCode) && market.marketType === 'SPOT',
    );
    const history = useQuery({
        queryKey: [
            'account-fees',
            owner,
            account?.exchangeAccountId,
            'model-selection',
            market?.symbol,
            before,
        ],
        queryFn: ({signal}) =>
            accountFeesApi.history(account!.exchangeAccountId, market!.symbol, before, signal),
        enabled: actual && supported && Boolean(account) && Boolean(owner),
        retry: false,
    });
    const selected =
        history.data?.items.find((row) => row.observationId === spec.feeObservationId) ??
        (picked?.observationId === spec.feeObservationId &&
        picked?.exchangeAccountId === account?.exchangeAccountId &&
        picked?.symbol === market?.symbol
            ? picked
            : null);
    const expired = Boolean(selected) && now >= Date.parse(selected!.expiresAt);
    const usesDiscount = actual && spec.feePaymentPolicy === discountAssetPolicy;
    const priceDatasets = useDatasetDetailQuery(
        typeof spec.discountPriceDatasetId === 'string' ? spec.discountPriceDatasetId : null,
        usesDiscount && supported,
    );
    // 精确详情仍执行原优惠来源资格判断，不能因对象存在而把失效来源认作可用。
    const selectedPriceDataset = eligibleDiscountDatasets(
        priceDatasets.data ? [priceDatasets.data] : [],
        market?.exchangeCode,
    )[0];
    const update = (next: Record<string, unknown>) => onChange?.(JSON.stringify(next));
    // Select先完成自身选项确认，再阻止浏览器把同一个Enter解释为外层表单提交；按钮键盘操作保持正常。
    return (
        <Space
            direction="vertical"
            style={{width: '100%'}}
            onKeyDown={(event) => {
                if (
                    event.key === 'Enter' &&
                    (event.target as HTMLElement).getAttribute('role') === 'combobox'
                )
                    event.preventDefault();
            }}
        >
            <Typography.Text>{t('feeModel.source')}</Typography.Text>
            <Select
                aria-label={t('feeModel.source')}
                style={{width: '100%'}}
                value={actual ? accountFeeMode : 'EXPERIMENT_ASSUMPTION'}
                disabled={invalid}
                options={[
                    {value: 'EXPERIMENT_ASSUMPTION', label: t('feeModel.assumption')},
                    {value: accountFeeMode, label: t('feeModel.current')},
                ]}
                onChange={(mode) => {
                    setPicked(null);
                    setBefore(undefined);
                    update(feeSourceMode(spec, mode));
                }}
            />
            {invalid && <Alert type="error" message={t('feeModel.invalidJson')} />}
            {actual && (
                <>
                    <Typography.Text type="secondary">{t('feeModel.meaning')}</Typography.Text>
                    {!supported && <Alert type="info" message={t('feeModel.chooseDataset')} />}
                    <Row gutter={[12, 12]} style={{width: '100%'}}>
                        <Col xs={24} sm={10}>
                            <Typography.Text>{t('feeModel.account')}</Typography.Text>
                            <Select
                                aria-label={t('feeModel.account')}
                                style={{width: '100%'}}
                                placeholder={t('feeModel.selectAccount')}
                                value={account?.exchangeAccountId}
                                loading={accounts.isFetching}
                                options={(accounts.data ?? [])
                                    .filter(
                                        (row) =>
                                            row.exchangeCode === market?.exchangeCode &&
                                            row.status === 'ACTIVE',
                                    )
                                    .map((row) => ({
                                        value: row.exchangeAccountId,
                                        label: row.accountAlias + ' · ' + row.tradeEnv,
                                    }))}
                                onChange={(id) => {
                                    setPicked(null);
                                    setBefore(undefined);
                                    update({
                                        ...feeSourceMode(spec, accountFeeMode),
                                        feeAccountId: String(id),
                                    });
                                }}
                            />
                        </Col>
                        <Col xs={24} sm={14}>
                            <Typography.Text>{t('feeModel.observation')}</Typography.Text>
                            <Select
                                aria-label={t('feeModel.observation')}
                                style={{width: '100%'}}
                                placeholder={t('feeModel.selectObservation')}
                                value={selected?.observationId}
                                loading={history.isFetching}
                                disabled={!supported || !account}
                                options={(history.data?.items ?? []).map((row) => ({
                                    value: row.observationId,
                                    disabled: now >= Date.parse(row.expiresAt),
                                    label:
                                        (row.commissionTerms
                                            ? t('feeModel.directional')
                                            : formatAccountFeePercent(row.takerCostRate)) +
                                        ' · ' +
                                        formatDateTimeMilliseconds(row.observedAt) +
                                        (now >= Date.parse(row.expiresAt)
                                            ? ' · ' + t('feeModel.expiredShort')
                                            : ''),
                                }))}
                                onChange={(id) => {
                                    const row = history.data?.items.find(
                                        (item) => item.observationId === id,
                                    );
                                    if (row) {
                                        setPicked(row);
                                        update(observedFeeSpec(spec, row));
                                    }
                                }}
                            />
                        </Col>
                    </Row>
                    {history.isError && (
                        <Alert
                            type="error"
                            message={formatApiError(history.error as AppApiError)}
                            action={
                                <Button onClick={() => void history.refetch()}>{t('retry')}</Button>
                            }
                        />
                    )}
                    {accounts.isError && (
                        <Alert
                            type="error"
                            message={formatApiError(accounts.error as AppApiError)}
                            action={
                                <Button onClick={() => void accounts.refetch()}>
                                    {t('retry')}
                                </Button>
                            }
                        />
                    )}
                    {supported &&
                        account &&
                        !history.isPending &&
                        !history.isError &&
                        history.data?.items.length === 0 && (
                            <Alert type="info" message={t('feeModel.empty')} />
                        )}
                    {expired && <Alert type="warning" message={t('feeModel.expired')} />}
                    {selected?.commissionTerms && (
                        <>
                            <Typography.Text>{t('feeModel.paymentPolicy')}</Typography.Text>
                            <Select<'RECEIVED_ASSET' | 'DISCOUNT_ASSET_IF_FUNDED'>
                                aria-label={t('feeModel.paymentPolicy')}
                                style={{width: '100%'}}
                                value={
                                    spec.feePaymentPolicy === discountAssetPolicy
                                        ? discountAssetPolicy
                                        : spec.feePaymentPolicy === 'RECEIVED_ASSET'
                                          ? 'RECEIVED_ASSET'
                                          : undefined
                                }
                                options={[
                                    {
                                        value: 'RECEIVED_ASSET',
                                        label: t('discountFee.receivedPolicy'),
                                    },
                                    {
                                        value: discountAssetPolicy,
                                        label: t('discountFee.fundedPolicy'),
                                        disabled: !discountAvailable(selected),
                                    },
                                ]}
                                onChange={(policy) => update(feePaymentPolicySpec(spec, policy))}
                            />
                            <Typography.Text type="secondary">
                                {t(
                                    usesDiscount
                                        ? 'discountFee.policyMeaning'
                                        : 'feeModel.receivedAssetPolicy',
                                )}
                            </Typography.Text>
                            {!discountAvailable(selected) && (
                                <Typography.Text type="secondary">
                                    {t('discountFee.conditionsUnavailable')}
                                </Typography.Text>
                            )}
                            {usesDiscount && (
                                <>
                                    <Row gutter={[12, 12]}>
                                        <Col xs={24} sm={8}>
                                            <Typography.Text>
                                                {t('discountFee.budgetFraction')}
                                            </Typography.Text>
                                            <Input
                                                aria-label={t('discountFee.budgetFraction')}
                                                inputMode="decimal"
                                                placeholder="0.10"
                                                value={String(
                                                    spec.discountAssetBudgetFraction ?? '',
                                                )}
                                                status={
                                                    spec.discountAssetBudgetFraction !==
                                                        undefined &&
                                                    !validDiscountFraction(
                                                        spec.discountAssetBudgetFraction,
                                                    )
                                                        ? 'error'
                                                        : undefined
                                                }
                                                onChange={(event) =>
                                                    update({
                                                        ...spec,
                                                        discountAssetBudgetFraction:
                                                            event.target.value,
                                                    })
                                                }
                                            />
                                            <Typography.Text type="secondary">
                                                {t('discountFee.fractionMeaning')}
                                            </Typography.Text>
                                        </Col>
                                        <Col xs={24} sm={16}>
                                            <Typography.Text>
                                                {t('discountFee.priceDataset')}
                                            </Typography.Text>
                                            <MarketdataDatasetPicker
                                                aria-label={t('discountFee.priceDataset')}
                                                active={usesDiscount && supported}
                                                value={
                                                    typeof spec.discountPriceDatasetId === 'string'
                                                        ? spec.discountPriceDatasetId
                                                        : undefined
                                                }
                                                constraints={{
                                                    exchangeCode: market?.exchangeCode,
                                                    marketType: 'SPOT',
                                                    symbol: 'BNB-USDT',
                                                    interval: '1m',
                                                    status: 'READY',
                                                    qualityStatus: 'OK',
                                                    source: 'marketdata_bars',
                                                    maximumGapCount: 0,
                                                }}
                                                onChange={(id) =>
                                                    update({...spec, discountPriceDatasetId: id})
                                                }
                                            />
                                        </Col>
                                    </Row>
                                    {selectedPriceDataset && (
                                        <Typography.Text type="secondary">
                                            {formatDateTimeMilliseconds(
                                                selectedPriceDataset.startTime,
                                            )}{' '}
                                            —{' '}
                                            {formatDateTimeMilliseconds(
                                                selectedPriceDataset.endTime,
                                            )}{' '}
                                            · {selectedPriceDataset.source}
                                        </Typography.Text>
                                    )}
                                    {priceDatasets.isError && (
                                        <Alert
                                            type="error"
                                            message={formatApiError(
                                                priceDatasets.error as AppApiError,
                                            )}
                                            action={
                                                <Button
                                                    onClick={() => void priceDatasets.refetch()}
                                                >
                                                    {t('retry')}
                                                </Button>
                                            }
                                        />
                                    )}
                                    {!priceDatasets.isFetching &&
                                        !priceDatasets.isError &&
                                        spec.discountPriceDatasetId &&
                                        !selectedPriceDataset && (
                                            <Alert
                                                type="warning"
                                                message={t('discountFee.datasetNoLongerEligible')}
                                            />
                                        )}
                                </>
                            )}
                            <Row gutter={[12, 8]}>
                                {(['buy', 'sell'] as const).map((side) => {
                                    const rates = receivedCommissionRates(selected);
                                    const asset =
                                        selected.symbol.split('-')[side === 'buy' ? 0 : 1];
                                    return (
                                        <Col key={side} xs={24} sm={12}>
                                            <Typography.Text>
                                                {t(
                                                    usesDiscount
                                                        ? `discountFee.fallback${side === 'buy' ? 'Buy' : 'Sell'}Rate`
                                                        : `feeModel.${side}Taker`,
                                                )}{' '}
                                                · {asset}
                                            </Typography.Text>
                                            <Typography.Text strong style={{display: 'block'}}>
                                                {formatAccountFeePercent(rates?.[side])}
                                            </Typography.Text>
                                        </Col>
                                    );
                                })}
                            </Row>
                            {usesDiscount && (
                                <Typography.Text type="secondary">
                                    {t('discountFee.actualPaymentMeaning')}
                                </Typography.Text>
                            )}
                        </>
                    )}
                    {account && supported && (
                        <Space wrap>
                            <Button size="small" onClick={() => void history.refetch()}>
                                {t('feeModel.refresh')}
                            </Button>
                            {before && (
                                <Button size="small" onClick={() => setBefore(undefined)}>
                                    {t('accountFees.latest')}
                                </Button>
                            )}
                            {history.data?.hasMore && (
                                <Button
                                    size="small"
                                    onClick={() => setBefore(history.data?.nextBefore ?? undefined)}
                                >
                                    {t('accountFees.next')}
                                </Button>
                            )}
                            <Typography.Text type="secondary">
                                {market?.symbol}
                                {selected?.tier ? ' · ' + selected.tier : ''}
                            </Typography.Text>
                        </Space>
                    )}
                </>
            )}
            <Row gutter={[12, 12]} style={{width: '100%'}}>
                {fields
                    .filter(
                        (key) =>
                            !(
                                actual &&
                                (market?.exchangeCode === 'BINANCE' ||
                                    spec.feeAssumptionVersion === directionalFeeVersion) &&
                                key === 'feeRate'
                            ),
                    )
                    .map((key) => (
                        <Col key={key} xs={24} sm={12}>
                            <Typography.Text>{t(`pages:cost_${key}`)}</Typography.Text>
                            <Input
                                aria-label={t(`pages:cost_${key}`)}
                                value={String(spec[key] ?? '')}
                                readOnly={
                                    actual && (key === 'feeRate' || key === 'feeAssumptionVersion')
                                }
                                disabled={invalid}
                                inputMode={key.endsWith('Version') ? 'text' : 'decimal'}
                                onChange={(e) => update({...spec, [key]: e.target.value})}
                            />
                        </Col>
                    ))}
            </Row>
            {!actual && (
                <Typography.Text type="secondary">{t('pages:runCostSource')}</Typography.Text>
            )}
            <Collapse
                style={{width: '100%'}}
                items={[
                    {
                        key: 'advanced',
                        label: t('pages:costAdvanced'),
                        children: (
                            <Input.TextArea
                                aria-label={t('pages:executionParameters')}
                                value={value}
                                readOnly={actual}
                                rows={5}
                                onChange={(e) => onChange?.(e.target.value)}
                            />
                        ),
                    },
                ]}
            />
        </Space>
    );
}
