import {parseExactJson} from '@/api/exact-json';
import type {AccountFeeObservation} from '@/features/accounts/api/fees';
import {validCommissionTerms} from '@/features/accounts/api/fees';
import type {MarketdataDataset} from '@/types/marketdata';

export const accountFeeMode = 'CURRENT_ACCOUNT_OBSERVATION';
export const directionalFeeVersion = 'ACCOUNT_FEE_MODEL_V2';
const discountFields = [
    'discountAssetBudgetFraction',
    'discountPriceDatasetId',
    'discountPriceSnapshotId',
    'discountPriceSnapshotSha256',
];
const sourceFields = [
    'feeObservationId',
    'feeAccountId',
    'feeTradeEnv',
    'feeSymbol',
    'feeExchangeCode',
    'feePaymentPolicy',
    'feeSourceOwnerUserId',
    'feeSourceSnapshotJson',
    'feeSourceSnapshotSha256',
    ...discountFields,
];
const exactFields = new Set([
    'feeRate',
    'slippageBps',
    'quantityStep',
    'priceTick',
    'minimumQuantity',
    'minimumNotional',
    'discountAssetBudgetFraction',
]);
export const discountAssetPolicy = 'DISCOUNT_ASSET_IF_FUNDED';

/** 保留成本精度及未知高级字段；格式错误交给表单拒绝，不自动替换原合同。 */
export function executionObject(value?: string): Record<string, unknown> {
    const parsed = parseExactJson(value || '{}', exactFields);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed))
        throw new Error('FEE_SPEC_OBJECT_REQUIRED');
    return parsed as Record<string, unknown>;
}

/** 切换来源只清除本来源身份和费率，不丢失已选市场规则、滑点及高级字段。 */
export function feeSourceMode(
    spec: Record<string, unknown>,
    mode: string,
): Record<string, unknown> {
    const next: Record<string, unknown> = {...spec, costSource: mode};
    for (const field of [...sourceFields, 'feeRate', 'feeAssumptionVersion']) delete next[field];
    return next;
}

/** 仅提交来源引用；V2明确收到资产条件并禁止全局标量，完整快照和所有者仍由服务端冻结。 */
export function observedFeeSpec(
    spec: Record<string, unknown>,
    row: AccountFeeObservation,
): Record<string, unknown> {
    const reference = {
        ...feeSourceMode(spec, accountFeeMode),
        feeObservationId: row.observationId,
        feeAccountId: String(row.exchangeAccountId),
        feeTradeEnv: row.tradeEnv,
        feeSymbol: row.symbol,
    };
    if (row.commissionTerms) {
        if (
            row.exchangeCode !== 'BINANCE' ||
            !validCommissionTerms(row.commissionTerms) ||
            row.commissionTerms.nativeSymbol !== row.symbol.replace('-', '')
        )
            throw new Error('FEE_MODEL_DIRECTIONAL_SOURCE_INVALID');
        return {
            ...reference,
            feeExchangeCode: 'BINANCE',
            feePaymentPolicy: 'RECEIVED_ASSET',
            feeAssumptionVersion: directionalFeeVersion,
        };
    }
    if (row.takerCostRate == null) throw new Error('FEE_MODEL_SCALAR_SOURCE_INVALID');
    return {...reference, feeRate: row.takerCostRate, feeAssumptionVersion: 'ACCOUNT_FEE_MODEL_V1'};
}

/** 观察列表只预览未优惠的方向总费率，十二位整数运算避免IEEE754；不是已经发生的成交费用。 */
export function receivedCommissionRates(
    row: AccountFeeObservation,
): {buy: string; sell: string} | null {
    if (!row.commissionTerms || !validCommissionTerms(row.commissionTerms)) return null;
    const unit = 10n ** 12n;
    const decimal = (text: string) => {
        const negative = text.startsWith('-');
        const [integer, fraction = ''] = text.replace(/^-/, '').split('.');
        return (negative ? -1n : 1n) * (BigInt(integer) * unit + BigInt(fraction.padEnd(12, '0')));
    };
    const render = (value: bigint) => {
        const negative = value < 0n;
        const digits = (negative ? -value : value).toString().padStart(13, '0');
        return (negative ? '-' : '') + digits.slice(0, -12) + '.' + digits.slice(-12);
    };
    const components = [
        row.commissionTerms.standard,
        row.commissionTerms.tax,
        row.commissionTerms.special,
    ];
    return {
        buy: render(
            components.reduce(
                (sum, rates) => sum + decimal(rates.taker) + decimal(rates.buyer),
                0n,
            ),
        ),
        sell: render(
            components.reduce(
                (sum, rates) => sum + decimal(rates.taker) + decimal(rates.seller),
                0n,
            ),
        ),
    };
}

/** 两个观察开关只能证明收费条件，不能证明SIM已有优惠币；预算和来源仍须用户声明并由服务端冻结。 */
export function discountAvailable(row?: AccountFeeObservation | null): boolean {
    return Boolean(
        row?.exchangeCode === 'BINANCE' &&
            row.commissionTerms &&
            validCommissionTerms(row.commissionTerms) &&
            row.commissionTerms.discount.enabledForAccount &&
            row.commissionTerms.discount.enabledForSymbol &&
            row.commissionTerms.discount.asset === 'BNB',
    );
}

/** 改支付条件后丢弃旧预算及封存来源，保留观察引用和市场规则，避免沿用另一政策的资金身份。 */
export function feePaymentPolicySpec(
    spec: Record<string, unknown>,
    policy: 'RECEIVED_ASSET' | 'DISCOUNT_ASSET_IF_FUNDED',
): Record<string, unknown> {
    const next: Record<string, unknown> = {...spec, feePaymentPolicy: policy};
    for (const key of [
        ...discountFields,
        'feeSourceSnapshotJson',
        'feeSourceSnapshotSha256',
        'feeSourceOwnerUserId',
    ])
        delete next[key];
    return next;
}

/** 八位十进制字符串严格在(0,1)内；不通过Number把微小份额或边界舍入成零/一。 */
export function validDiscountFraction(value: unknown): value is string {
    return typeof value === 'string' && /^0\.\d{1,8}$/.test(value) && /[1-9]/.test(value);
}

/** 数据目录只提供真实历史来源；不自动建数据、采集行情或扩大BNB下单能力。 */
export function eligibleDiscountDatasets(
    rows: MarketdataDataset[],
    exchange?: string,
): MarketdataDataset[] {
    return rows.filter(
        (row) =>
            row.exchangeCode === exchange &&
            row.marketType === 'SPOT' &&
            row.symbol === 'BNB-USDT' &&
            row.interval === '1m' &&
            row.status === 'READY' &&
            row.qualityStatus === 'OK' &&
            row.gapCount === 0 &&
            row.source === 'marketdata_bars',
    );
}

/** 只有正式成交支付状态才能显示“已使用”；配置中的优惠政策不能冒充已发生的优惠。 */
export function feePaymentStatusKey(status: unknown): string | null {
    return typeof status === 'string' &&
        [
            'DISCOUNT_ASSET_USED',
            'DISCOUNT_ASSET_INSUFFICIENT_FALLBACK',
            'RECEIVED_ASSET',
            'QUOTE_NOTIONAL',
        ].includes(status)
        ? `discountFee.payment_${status}`
        : null;
}

/** 创建前检查同市场及完整引用；新鲜度、凭据和所有者仍由后端权威校验。 */
export function feeSelectionError(
    value?: string,
    symbol?: string,
    exchangeCode?: string,
): string | null {
    try {
        const spec = executionObject(value);
        if (spec.costSource !== accountFeeMode) return null;
        if (
            [
                'feeSourceOwnerUserId',
                'feeSourceSnapshotJson',
                'feeSourceSnapshotSha256',
                'discountPriceSnapshotId',
                'discountPriceSnapshotSha256',
            ].some((key) => spec[key] !== undefined)
        )
            return 'discountFee.serverFieldsForbidden';
        const directional = spec.feeAssumptionVersion === directionalFeeVersion;
        if (
            spec.feePaymentPolicy !== discountAssetPolicy &&
            discountFields.some((key) => spec[key] !== undefined)
        )
            return 'discountFee.policyFieldsInvalid';
        if (directional && spec.feePaymentPolicy === discountAssetPolicy) {
            if (!validDiscountFraction(spec.discountAssetBudgetFraction))
                return 'discountFee.fractionInvalid';
            if (
                typeof spec.discountPriceDatasetId !== 'string' ||
                !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(
                    spec.discountPriceDatasetId,
                )
            )
                return 'discountFee.datasetRequired';
        }
        return typeof spec.feeObservationId === 'string' &&
            typeof spec.feeAccountId === 'string' &&
            spec.feeSymbol === symbol &&
            (!exchangeCode || (spec.feeExchangeCode ?? 'OKX') === exchangeCode) &&
            (directional
                ? spec.feeExchangeCode === 'BINANCE' &&
                  ['RECEIVED_ASSET', discountAssetPolicy].includes(String(spec.feePaymentPolicy)) &&
                  !('feeRate' in spec)
                : typeof spec.feeRate === 'string')
            ? null
            : 'feeModel.selectRequired';
    } catch {
        return 'feeModel.invalidJson';
    }
}
