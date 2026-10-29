/** 只在展示层做有定义的十进制四舍五入；金额不经IEEE754，原值由明细保留。 */
export function decimalDisplay(
    value: string | null | undefined,
    digits = 8,
    shift = 0,
    signed = false,
): string {
    if (value == null) return '—';
    const match = /^([+-]?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(value);
    if (!match || value.length > 200 || digits < 0 || digits > 16) return value;
    const exponent = Number(match[4] ?? 0) + shift;
    if (Math.abs(exponent) > 100) return value;
    const fraction = match[3] ?? '';
    let quantity = BigInt(match[2] + fraction);
    const scale = fraction.length - exponent - digits;
    if (scale > 0) {
        const divisor = 10n ** BigInt(scale);
        quantity = (quantity + divisor / 2n) / divisor;
    } else quantity *= 10n ** BigInt(-scale);
    const raw = quantity.toString().padStart(digits + 1, '0');
    const integer = (digits ? raw.slice(0, -digits) : raw).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    const decimal = digits ? raw.slice(-digits).replace(/0+$/, '') : '';
    const sign = quantity === 0n ? '' : match[1] === '-' ? '-' : signed ? '+' : '';
    return sign + integer + (decimal ? '.' + decimal : '');
}
