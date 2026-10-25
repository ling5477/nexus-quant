import {compareLegacyDecimals} from './legacy-analysis';

type DecimalInput = string | number | null | undefined;
interface ScaledValue {
    quantity: bigint;
    scale: number;
}

/** 仅用于历史展示排序和启发式分，不能参与订单、资金或账务计算。无效值保持不可比。 */
export function legacyDecimalAvailable(value: DecimalInput): boolean {
    return compareLegacyDecimals(value, null, 'asc') < 0;
}

export function legacyAbsDecimal(value: DecimalInput): string | null {
    return legacyDecimalAvailable(value) ? String(value).replace(/^[-+]/, '') : null;
}

function scaled(value: DecimalInput): ScaledValue | null {
    if (!legacyDecimalAvailable(value)) return null;
    const match = /^([+-]?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(String(value));
    if (!match) return null;
    const fraction = match[3] ?? '';
    return {
        quantity: BigInt((match[1] === '-' ? '-' : '') + match[2] + fraction),
        scale: fraction.length - Number(match[4] ?? 0),
    };
}

function subtract(left: ScaledValue, right: ScaledValue): ScaledValue {
    const scale = Math.max(left.scale, right.scale);
    return {
        quantity:
            left.quantity * 10n ** BigInt(scale - left.scale) -
            right.quantity * 10n ** BigInt(scale - right.scale),
        scale,
    };
}

function serialize(value: ScaledValue): string | null {
    if (value.quantity === 0n) return '0';
    const sign = value.quantity < 0n ? '-' : '';
    const digits = (value.quantity < 0n ? -value.quantity : value.quantity).toString();
    if (Math.abs(value.scale) + digits.length > 2000) return null;
    let text: string;
    if (value.scale <= 0) text = digits + '0'.repeat(-value.scale);
    else {
        const padded = digits.padStart(value.scale + 1, '0');
        text = padded.slice(0, -value.scale) + '.' + padded.slice(-value.scale);
        text = text.replace(/0+$/, '').replace(/\.$/, '');
    }
    return text.length > 511 ? null : sign + text;
}

/** 保留既有排序公式的十进制语义；计数必须是非负安全整数，未知不当作零惩罚。 */
export function exactLegacyRiskScore(
    totalReturn: DecimalInput,
    drawdown: DecimalInput,
    riskBlockedCount: number,
    openAlertCount: number,
    insufficientCount: number,
): string | null {
    const profit = scaled(totalReturn),
        loss = scaled(drawdown);
    if (
        !profit ||
        !loss ||
        ![riskBlockedCount, openAlertCount, insufficientCount].every(
            (value) => Number.isSafeInteger(value) && value >= 0,
        )
    )
        return null;
    const absolute = {...loss, quantity: loss.quantity < 0n ? -loss.quantity : loss.quantity};
    const penalty = {
        quantity:
            BigInt(riskBlockedCount) * 5n + BigInt(openAlertCount) + BigInt(insufficientCount) * 2n,
        scale: 2,
    };
    return serialize(subtract(subtract(profit, absolute), penalty));
}

/** 阈值边界按负回撤精确比较；正值违反回撤合同，不能落入正常分桶。 */
export function legacyDrawdownBucket(value: DecimalInput): number | null {
    if (!legacyDecimalAvailable(value) || compareLegacyDecimals(value, '0', 'asc') > 0) return null;
    for (const [index, boundary] of ['-0.05', '-0.1', '-0.2'].entries())
        if (compareLegacyDecimals(value, boundary, 'asc') > 0) return index;
    return 3;
}

/** 极值选择保留原行身份和输入顺序；所有无效或缺失值都不参加名次。 */
export function topLegacyRow<T>(
    rows: T[],
    valueOf: (row: T) => DecimalInput,
    preferMax = true,
): T | null {
    let best: T | null = null;
    for (const row of rows) {
        const value = valueOf(row);
        if (!legacyDecimalAvailable(value)) continue;
        if (
            best === null ||
            compareLegacyDecimals(value, valueOf(best), preferMax ? 'desc' : 'asc') < 0
        )
            best = row;
    }
    return best;
}
