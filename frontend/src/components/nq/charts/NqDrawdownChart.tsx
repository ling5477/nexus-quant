import {useMemo} from 'react';

import {useEChart} from '@/components/nq/charts/useEChart';
import {formatNqNumber} from '@/components/nq/NqNumericText';
import {buildNqLineChartBaseOption} from '@/theme/chart-theme';
import {nqColor} from '@/theme/tokens';
import type {EquityCurveSnapshotItem} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/**
 * NqDrawdownChart — 回撤曲线图。
 *
 * 关键约束：
 * 1) 后端 drawdown 为比例值（0..1，正值越大回撤越深，见 DrawdownCalculator），
 *    展示统一换算为百分比并将 y 轴反向（回撤向下），符合行业阅读习惯；
 * 2) 配色来自 Design System tokens（danger 系），禁止自定义颜色。
 */
interface NqDrawdownChartProps {
    data: EquityCurveSnapshotItem[];
    height?: number;
}

export function NqDrawdownChart({data, height = 180}: NqDrawdownChartProps) {
    const option = useMemo(() => {
        if (data.length === 0) {
            return null;
        }

        const sorted = [...data].sort(
            (left, right) => new Date(left.snapshotTime).getTime() - new Date(right.snapshotTime).getTime(),
        );
        const base = buildNqLineChartBaseOption();

        return {
            ...base,
            tooltip: {
                ...base.tooltip,
                valueFormatter: (value: unknown) => `${formatNqNumber(value as number, {precision: 2})}%`,
            },
            xAxis: {
                ...base.xAxis,
                data: sorted.map((item) => formatDateTime(item.snapshotTime)),
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
                    name: '回撤',
                    type: 'line',
                    showSymbol: false,
                    data: sorted.map((item) => Number(item.drawdown) * 100),
                    lineStyle: {width: 1.5, color: nqColor.danger},
                    itemStyle: {color: nqColor.danger},
                    areaStyle: {color: nqColor.danger, opacity: 0.1},
                },
            ],
        };
    }, [data]);

    const containerRef = useEChart(option);

    return <div ref={containerRef} className="nq-chart" style={{height}}/>;
}
