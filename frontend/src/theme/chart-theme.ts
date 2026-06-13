import {nqColor, nqFont} from '@/theme/tokens';

/**
 * NQ Design System v1 — 图表同源主题。
 *
 * 职责：为 ECharts（以及后续 Lightweight Charts）提供与 UI 同源的配色与坐标轴样式。
 * 关键约束：
 * 1) 图表颜色必须来自 `@/theme/tokens`，禁止图表与 UI 出现两套割裂配色；
 * 2) 这里只提供基础骨架（grid/axis/tooltip/系列色），业务图表组件负责数据映射。
 */

/** 多系列默认取色顺序：品牌色优先，其后是语义色。 */
export const nqChartSeriesPalette: string[] = [
    nqColor.primary,
    nqColor.warning,
    nqColor.demo,
    nqColor.info,
    nqColor.success,
];

/** 折线/面积图共用的 ECharts 基础 option 片段（深色、高密度、1px 分割线）。 */
export function buildNqLineChartBaseOption() {
    return {
        backgroundColor: 'transparent',
        textStyle: {
            color: nqColor.textSecondary,
            fontFamily: nqFont.family,
            fontSize: nqFont.sizeSm,
        },
        grid: {
            left: 8,
            right: 12,
            top: 28,
            bottom: 8,
            containLabel: true,
        },
        tooltip: {
            trigger: 'axis' as const,
            backgroundColor: nqColor.bgElevated,
            borderColor: nqColor.border,
            borderWidth: 1,
            textStyle: {
                color: nqColor.text,
                fontSize: nqFont.sizeSm,
            },
            axisPointer: {
                lineStyle: {color: nqColor.border},
            },
        },
        xAxis: {
            type: 'category' as const,
            boundaryGap: false,
            axisLine: {lineStyle: {color: nqColor.border}},
            axisTick: {show: false},
            axisLabel: {color: nqColor.textTertiary, fontSize: nqFont.sizeXs},
            splitLine: {show: false},
        },
        yAxis: {
            type: 'value' as const,
            scale: true,
            axisLine: {show: false},
            axisTick: {show: false},
            axisLabel: {color: nqColor.textTertiary, fontSize: nqFont.sizeXs},
            splitLine: {lineStyle: {color: nqColor.borderSubtle, width: 1}},
        },
    };
}
