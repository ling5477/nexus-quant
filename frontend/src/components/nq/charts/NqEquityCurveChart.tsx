import {useMemo} from 'react';

import {useEChart} from '@/components/nq/charts/useEChart';
import {formatNqNumber} from '@/components/nq/NqNumericText';
import {buildNqLineChartBaseOption} from '@/theme/chart-theme';
import {nqColor} from '@/theme/tokens';
import type {EquityCurveSnapshotItem} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/**
 * NqEquityCurveChart — 权益曲线图（总权益 + 持仓市值）。
 *
 * 关键约束：
 * 1) 配色与坐标轴样式来自 Design System 图表主题（buildNqLineChartBaseOption），
 *    禁止在此处自定义第二套颜色；
 * 2) 数据为后端 equity-curve 快照原值，组件只做展示换算，不做业务计算。
 */
interface NqEquityCurveChartProps {
    data: EquityCurveSnapshotItem[];
    height?: number;
}

export function NqEquityCurveChart({data, height = 260}: NqEquityCurveChartProps) {
    const option = useMemo(() => {
        if (data.length === 0) {
            return null;
        }

        const sorted = [...data].sort(
            (left, right) => new Date(left.snapshotTime).getTime() - new Date(right.snapshotTime).getTime(),
        );

        return {
            ...buildNqLineChartBaseOption(),
            legend: {
                top: 0,
                right: 0,
                textStyle: {color: nqColor.textSecondary},
                itemWidth: 12,
                itemHeight: 8,
            },
            tooltip: {
                ...buildNqLineChartBaseOption().tooltip,
                valueFormatter: (value: unknown) => formatNqNumber(value as number, {precision: 2}),
            },
            xAxis: {
                ...buildNqLineChartBaseOption().xAxis,
                data: sorted.map((item) => formatDateTime(item.snapshotTime)),
            },
            series: [
                {
                    name: '总权益',
                    type: 'line',
                    showSymbol: false,
                    data: sorted.map((item) => Number(item.totalEquity)),
                    lineStyle: {width: 1.5, color: nqColor.primary},
                    itemStyle: {color: nqColor.primary},
                    areaStyle: {color: nqColor.primary, opacity: 0.08},
                },
                {
                    name: '持仓市值',
                    type: 'line',
                    showSymbol: false,
                    data: sorted.map((item) => Number(item.positionValue)),
                    lineStyle: {width: 1, color: nqColor.warning},
                    itemStyle: {color: nqColor.warning},
                },
            ],
        };
    }, [data]);

    const containerRef = useEChart(option);

    return <div ref={containerRef} className="nq-chart" style={{height}}/>;
}
