/**
 * Nq 数字排版组件族 — 价格 / 金额 / 百分比统一展示。
 *
 * 数字排版规范（Design System v1）：
 * 1) 所有数字使用 tabular-nums（.nq-num），表格数字列右对齐（.nq-col-num）；
 * 2) 大数字千分位、小数位统一；收益率带正负号；
 * 3) 盈亏配色遵循国内习惯：正=红（up），负=绿（down）。
 */

import {formatNqNumber} from '@/nq-design-system/format/nqFormat';

// 保留原出口，现有组件与页面共用设计系统的相同数字合同。
export {formatNqNumber};
export type {NqNumberFormatOptions} from '@/nq-design-system/format/nqFormat';

interface NqNumericTextProps {
    value: string | number | null | undefined;
    precision?: number;
    signed?: boolean;
    /** 按正负着色：正=up（红涨），负=down（绿跌）。 */
    colorBySign?: boolean;
    suffix?: string;
}

function signClassName(value: string | number | null | undefined, colorBySign: boolean): string {
    if (!colorBySign) {
        return '';
    }

    const numeric = Number(value);

    if (!Number.isFinite(numeric) || numeric === 0) {
        return '';
    }

    return numeric > 0 ? ' nq-text-up' : ' nq-text-down';
}

function NqNumericText({value, precision = 2, signed = false, colorBySign = false, suffix = ''}: NqNumericTextProps) {
    const text = formatNqNumber(value, {precision, signed});

    return (
        <span className={`nq-num${signClassName(value, colorBySign)}`}>
            {text === '-' ? text : `${text}${suffix}`}
        </span>
    );
}

/** 价格字段：默认 4 位小数，不带符号。 */
export function NqPriceText(props: Omit<NqNumericTextProps, 'suffix'>) {
    return <NqNumericText precision={4} {...props}/>;
}

/** 金额/数量字段：默认 2 位小数；盈亏类传 signed + colorBySign。 */
export function NqAmountText(props: Omit<NqNumericTextProps, 'suffix'>) {
    return <NqNumericText precision={2} {...props}/>;
}

interface NqPercentTextProps extends Omit<NqNumericTextProps, 'suffix' | 'signed'> {
    /** 后端为比例值（如 0.0123）时设为 true，展示前 ×100。 */
    ratio?: boolean;
    signed?: boolean;
}

/** 百分比字段：默认带正负号、2 位小数；ratio=true 时输入按比例值换算。 */
export function NqPercentText({value, ratio = false, signed = true, ...rest}: NqPercentTextProps) {
    const numeric = Number(value);
    const display = ratio && value !== null && value !== undefined && value !== '' && Number.isFinite(numeric)
        ? numeric * 100
        : value;

    return <NqNumericText value={display} precision={2} signed={signed} suffix="%" {...rest}/>;
}
