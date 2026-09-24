import {t} from '@/i18n/index';
import {useTranslation} from 'react-i18next';
import {useMemo} from 'react';

import {useEChart} from '@/nq-design-system/charts/useEChart';
import {formatNqNumber} from '@/components/nq/NqNumericText';
import {buildNqLineChartBaseOption} from '@/nq-design-system/theme/nqChartTheme';
import {nqColor} from '@/theme/tokens';
import type {PaperPortfolioCurvePoint} from '@/features/paper-trading/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/**
 * 组合资金 / 回撤曲线图（Loop-15）。
 *
 * 关键约束：
 * 1) 复用 Design System 图表主题（buildNqLineChartBaseOption）与 useEChart 封装，配色取自 tokens，
 *    禁止自定义第二套颜色；不修改单 run 的 NqEquityCurveChart / NqDrawdownChart，避免影响其它页面。
 * 2) 数据为后端 portfolioCurve.points 原值，组件只做展示换算（回撤取绝对值 ×100），不做业务计算。
 * 3) 仅展示 Paper 模拟、简化组合资金合计曲线，不代表真实时间加权收益，也不代表 LIVE 或真实交易表现。
 */
interface PortfolioCurveChartProps {
    points: PaperPortfolioCurvePoint[];
    height?: number;
}

/** 把 string|number|null 安全转为 number|null（非有限值视为缺数据，ECharts 渲染为断点）。 */
function toChartNumber(value: string | number | null): number | null {
    if (value === null || value === undefined || value === '') {
        return null;
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
}

/** 比例值（如 -0.2353）→ 带符号百分比文案（如 -23.53%）；缺数据显示「数据不足」。 */
function ratioToPercentText(value: string | number | null): string {
    const numeric = toChartNumber(value);
    return numeric === null ? t('chart.insufficient') : `${formatNqNumber(numeric * 100, {precision: 2, signed: true})}%`;
}

function sortByTime(points: PaperPortfolioCurvePoint[]): PaperPortfolioCurvePoint[] {
    return [...points].sort(
        (left, right) => new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime(),
    );
}

/** axis 触发的 tooltip 中取当前 hover 点（params 为该 x 轴位置的系列点数组）。 */
function tooltipPoint(params: unknown, sorted: PaperPortfolioCurvePoint[]): PaperPortfolioCurvePoint | null {
    const list = Array.isArray(params) ? params as Array<{dataIndex: number}> : [];
    const index = list[0]?.dataIndex ?? -1;
    return sorted[index] ?? null;
}

/**
 * NqPortfolioEquityChart — 组合资金合计曲线。
 * series：组合权益（实线 + 面积）+ 初始资金合计基线（虚线）。
 * tooltip：组合权益 / 初始资金 / 组合 PnL / 收益率 / 在册 run · 缺失 run。
 */
export function NqPortfolioEquityChart({points, height = 260}: PortfolioCurveChartProps) {
    const {i18n} = useTranslation();
    const option = useMemo(() => {
        if (points.length === 0) {
            return null;
        }
        const sorted = sortByTime(points);
        const base = buildNqLineChartBaseOption();
        return {
            ...base,
            tooltip: {
                ...base.tooltip,
                formatter: (params: unknown) => {
                    const point = tooltipPoint(params, sorted);
                    if (!point) {
                        return '';
                    }
                    return [
                        formatDateTime(point.timestamp),
                        t('chart.equityValue', {value: formatNqNumber(point.totalEquity, {precision: 2})}),
                        t('chart.initialValue', {value: formatNqNumber(point.totalInitialEquity, {precision: 2})}),
                        t('chart.pnlValue', {value: formatNqNumber(point.totalPnl, {precision: 2, signed: true})}),
                        t('chart.returnValue', {value: ratioToPercentText(point.totalReturn)}),
                        t('chart.runCounts', {source: point.sourceRunCount, missing: point.missingRunCount}),
                    ].join('<br/>');
                },
            },
            xAxis: {
                ...base.xAxis,
                data: sorted.map((point) => formatDateTime(point.timestamp)),
            },
            series: [
                {
                    name: t('chart.portfolioEquity'),
                    type: 'line',
                    showSymbol: false,
                    data: sorted.map((point) => toChartNumber(point.totalEquity)),
                    lineStyle: {width: 1.5, color: nqColor.primary},
                    itemStyle: {color: nqColor.primary},
                    areaStyle: {color: nqColor.primary, opacity: 0.08},
                },
                {
                    name: t('chart.initial'),
                    type: 'line',
                    showSymbol: false,
                    data: sorted.map((point) => toChartNumber(point.totalInitialEquity)),
                    lineStyle: {width: 1, color: nqColor.textTertiary, type: 'dashed'},
                    itemStyle: {color: nqColor.textTertiary},
                },
            ],
        };
    }, [points, i18n.resolvedLanguage]);

    const containerRef = useEChart(option);
    return <div ref={containerRef} className="nq-chart" style={{height}}/>;
}

/**
 * NqPortfolioDrawdownChart — 组合回撤曲线。
 * 后端 drawdown 为负比例（0 表示无回撤，越负越深），展示统一取绝对值 ×100 并将 y 轴反向（回撤向下），
 * 与单 run NqDrawdownChart 阅读习惯一致。tooltip：回撤 / 资金峰值 / 组合权益。
 */
export function NqPortfolioDrawdownChart({points, height = 180}: PortfolioCurveChartProps) {
    const {i18n} = useTranslation();
    const option = useMemo(() => {
        if (points.length === 0) {
            return null;
        }
        const sorted = sortByTime(points);
        const base = buildNqLineChartBaseOption();
        return {
            ...base,
            tooltip: {
                ...base.tooltip,
                formatter: (params: unknown) => {
                    const point = tooltipPoint(params, sorted);
                    if (!point) {
                        return '';
                    }
                    return [
                        formatDateTime(point.timestamp),
                        t('chart.drawdownValue', {value: ratioToPercentText(point.drawdown)}),
                        t('chart.peakValue', {value: formatNqNumber(point.peakEquity, {precision: 2})}),
                        t('chart.equityValue', {value: formatNqNumber(point.totalEquity, {precision: 2})}),
                    ].join('<br/>');
                },
            },
            xAxis: {
                ...base.xAxis,
                data: sorted.map((point) => formatDateTime(point.timestamp)),
            },
            yAxis: {
                ...base.yAxis,
                inverse: true,
                min: 0,
                axisLabel: {
                    ...base.yAxis.axisLabel,
                    formatter: (value: number) => `${value}%`,
                },
            },
            series: [
                {
                    name: t('chart.portfolioDrawdown'),
                    type: 'line',
                    showSymbol: false,
                    data: sorted.map((point) => {
                        const drawdown = toChartNumber(point.drawdown);
                        return drawdown === null ? null : Math.abs(drawdown) * 100;
                    }),
                    lineStyle: {width: 1.5, color: nqColor.danger},
                    itemStyle: {color: nqColor.danger},
                    areaStyle: {color: nqColor.danger, opacity: 0.1},
                },
            ],
        };
    }, [points, i18n.resolvedLanguage]);

    const containerRef = useEChart(option);
    return <div ref={containerRef} className="nq-chart" style={{height}}/>;
}
