// nqFormat.ts — NQ Console 数字 / 金额 / 百分比 / 涨跌方向的纯格式化函数。
// 约束:空值统一显示 "-";数字用 zh-CN 千分位;颜色由消费层按 direction 读 var(--nq-*),此处不决定颜色。

export interface NqNumberFormatOptions {
    /** 小数位数(价格常用 4,金额/百分比常用 2)。 */
    precision?: number;
    /** 保留服务端十进制有效位，不对经济事实固定小数位舍入。 */
    exact?: boolean;
    /** 正数是否加 "+"(盈亏 / 涨跌幅等带符号字段)。 */
    signed?: boolean;
    /** 是否使用千分位分组(默认 true)。 */
    thousands?: boolean;
}

/** 空值判定:null / undefined / 空串 视为缺失。 */
function isNqEmpty(value: string | number | null | undefined): boolean {
    return value === null || value === undefined || value === '';
}

/**
 * 统一数字格式化。显示舍入采用有界十进制HALF_UP；缺失与不可解析值分别保留，不先转浮点丢失金额。
 */
export function formatNqNumber(
    value: string | number | null | undefined,
    {precision = 2, signed = false, thousands = true, exact = false}: NqNumberFormatOptions = {},
): string {
    if (isNqEmpty(value)) {
        return '-';
    }

    if (exact) {
        let raw = String(value).trim();
        const scientific = /^([+-]?)(\d+)(?:\.(\d+))?[eE]([+-]?\d+)$/.exec(raw);
        if (scientific) {
            const exponent = Number(scientific[4]);
            // 仅移动十进制小数点，不经浮点；限制展开规模，异常输入保留原文。
            if (raw.length > 200 || Math.abs(exponent) > 100) return raw;
            const coefficient = scientific[2] + (scientific[3] ?? '');
            const point = scientific[2].length + exponent;
            raw =
                scientific[1] +
                (point <= 0
                    ? '0.' + '0'.repeat(-point) + coefficient
                    : point >= coefficient.length
                      ? coefficient + '0'.repeat(point - coefficient.length)
                      : coefficient.slice(0, point) + '.' + coefficient.slice(point));
        }
        const match = /^([+-]?)(\d+)(?:\.(\d+))?$/.exec(raw);
        if (!match) return raw;
        const integer = match[2].replace(/^0+(?=\d)/, '');
        const fraction = (match[3] ?? '').replace(/0+$/, '');
        const nonzero = /[1-9]/.test(integer + fraction);
        const sign = nonzero ? (match[1] === '-' ? '-' : signed ? '+' : '') : '';
        const grouped = thousands ? integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : integer;
        return sign + grouped + (fraction ? '.' + fraction : '');
    }

    const parts = decimalParts(value);
    if (!parts) return String(value);
    if (!Number.isInteger(precision) || precision < 0 || precision > 20)
        throw new RangeError('Decimal display precision must be between0 and20');
    let quantity = BigInt(parts.integer + parts.fraction);
    const shift = parts.fraction.length - precision;
    if (shift > 0) {
        const divisor = 10n ** BigInt(shift);
        quantity = (quantity + divisor / 2n) / divisor;
    } else quantity *= 10n ** BigInt(-shift);
    const digits = quantity.toString().padStart(precision + 1, '0');
    const integer = precision ? digits.slice(0, -precision) : digits;
    const grouped = thousands ? integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : integer;
    const sign = quantity === 0n ? '' : parts.negative ? '-' : signed ? '+' : '';
    return sign + grouped + (precision ? '.' + digits.slice(-precision) : '');
}

/** exact分支仅移动小数点并限制展开规模；此处复用它，保证舍入、比例换算和颜色共用相同输入语义。 */
function decimalParts(value: string | number | null | undefined) {
    const raw = formatNqNumber(value, {exact: true, thousands: false});
    const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(raw);
    if (!match || raw.length > 400) return null;
    return {negative: match[1] === '-', integer: match[2], fraction: match[3] ?? '', raw};
}

export interface NqMoneyFormatOptions extends NqNumberFormatOptions {
    /** 货币 / 单位后缀,例如 "USDT" / "¥";为空则只显示数字。 */
    currency?: string;
}

/** 金额格式化:默认 2 位小数 + 千分位,可选货币后缀。 */
export function formatNqMoney(
    value: string | number | null | undefined,
    {
        precision = 2,
        signed = false,
        currency,
        exact = false,
        thousands = true,
    }: NqMoneyFormatOptions = {},
): string {
    const text = formatNqNumber(value, {precision, signed, exact, thousands});

    if (text === '-' || !currency) {
        return text;
    }

    return `${text} ${currency}`;
}

export interface NqPercentFormatOptions extends NqNumberFormatOptions {
    /** 后端为比例值(0.0123 = 1.23%)时设 true,展示前 ×100。 */
    ratio?: boolean;
}

/** 百分比格式化:默认带符号 + 2 位小数 + "%";ratio=true 时按比例值换算。 */
export function formatNqPercent(
    value: string | number | null | undefined,
    {
        precision = 2,
        signed = true,
        ratio = false,
        exact = false,
        thousands = true,
    }: NqPercentFormatOptions = {},
): string {
    if (isNqEmpty(value)) {
        return '-';
    }

    const parts = decimalParts(value);
    if (!parts) return String(value);
    // 比例通过十进制指数移动两位；原始尾数不经过IEEE754，exact仍保留服务端有效位。
    const scaled = ratio ? parts.raw + 'e2' : parts.raw;
    return `${formatNqNumber(scaled, {precision, signed, exact, thousands})}%`;
}

type NqDirection = 'up' | 'down' | 'flat';

/**
 * 由数值符号判定涨跌方向(与行情惯例无关:>0=up,<0=down,0/缺失=flat)。
 * 具体颜色由消费层映射到 var(--nq-up/--nq-down/--nq-flat),从而随惯例开关翻转。
 */
export function nqDirectionOf(value: string | number | null | undefined): NqDirection {
    if (isNqEmpty(value)) {
        return 'flat';
    }

    const parts = decimalParts(value);
    if (!parts || !/[1-9]/.test(parts.integer + parts.fraction)) {
        return 'flat';
    }
    return parts.negative ? 'down' : 'up';
}

/** 涨跌方向对应的 CSS 变量(随行情惯例翻转,不复用 success/danger)。 */
export const NQ_DIRECTION_VAR: Record<NqDirection, string> = {
    up: 'var(--nq-up)',
    down: 'var(--nq-down)',
    flat: 'var(--nq-flat)',
};
