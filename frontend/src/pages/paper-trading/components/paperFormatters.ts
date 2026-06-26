/**
 * paperFormatters —— Paper Trading 页面共享纯函数。
 *
 * K5-A 行为保持型组件抽取：把同时被 PaperTradingPage 与抽出的 dashboard 组件使用的纯取值/格式化函数
 * 集中到此文件。实现与原 PaperTradingPage.tsx 内定义逐字一致，只改变定义位置，不改变任何取值或格式化语义。
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
