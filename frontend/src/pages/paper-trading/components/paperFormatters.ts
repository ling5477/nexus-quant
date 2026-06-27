/**
 * paperFormatters —— Paper Trading 页面共享纯函数。
 *
 * K5-A 行为保持型组件抽取：这些纯取值 / 格式化函数来源于旧 all-in-one Paper Trading 页，
 * 现在由拆分后的 dashboard 组件共享。实现保留原语义，不改变任何取值或格式化行为。
 */

/** 解析为有限数值；空串 / null / undefined / 非有限值统一返回 null（不伪造 0）。 */
export function toNullableNumber(value: string | number | null | undefined): number | null {
    if (value === null || value === undefined || value === '') {
        return null;
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
}

/** PnL 语义色调：0 / null 视为中性（muted），正为 up，负为 down。 */
export function pnlTone(value: number | null): 'up' | 'down' | 'muted' {
    if (value === null || value === 0) {
        return 'muted';
    }
    return value > 0 ? 'up' : 'down';
}
