import {useEffect, useRef} from 'react';

import {echarts} from '@/components/nq/charts/echarts-core';
import {nqTokens, registerNqEchartsTheme} from '@/nq-design-system';

export interface BacktestCurvePoint {
    /** 时间轴标签(category)。 */
    t: string;
    /** 数值(权益或回撤)。 */
    v: number;
}

interface BacktestCurveChartProps {
    /** 时间序列点;null/空表示后端未提供该序列(显示 unavailable,不编造曲线)。 */
    points: BacktestCurvePoint[] | null | undefined;
    height?: number;
    /** equity:primary 面积线;drawdown:danger 面积线(值通常 ≤ 0)。 */
    kind?: 'equity' | 'drawdown';
    /** 无数据时的说明文案。 */
    unavailableText?: string;
}

/**
 * BacktestCurveChart — 回测权益 / 回撤曲线(v2 ECharts 'nq' 主题)。
 *
 * 关键约束:
 * 1) 只渲染传入的真实序列;`points` 为空时显示明确 unavailable 占位,**不编造曲线**;
 * 2) 颜色取自 nq tokens(equity=primary,drawdown=danger),坐标轴/网格/tooltip 用 'nq' 主题,与界面同色板;
 * 3) 卸载时 dispose,ResizeObserver 自适应,避免泄漏。
 */
export function BacktestCurveChart({points, height = 260, kind = 'equity', unavailableText}: BacktestCurveChartProps) {
    const containerRef = useRef<HTMLDivElement | null>(null);
    const hasData = Array.isArray(points) && points.length > 0;

    useEffect(() => {
        if (!hasData || !containerRef.current || !points) {
            return;
        }

        registerNqEchartsTheme();
        const chart = echarts.init(containerRef.current, 'nq');
        const color = kind === 'drawdown' ? nqTokens.semantic.danger : nqTokens.semantic.primary;

        chart.setOption(
            {
                grid: {left: 8, right: 12, top: 16, bottom: 24, containLabel: true},
                tooltip: {trigger: 'axis'},
                xAxis: {type: 'category', boundaryGap: false, data: points.map((p) => p.t)},
                yAxis: {type: 'value', scale: kind === 'equity'},
                series: [
                    {
                        type: 'line',
                        showSymbol: false,
                        smooth: kind === 'equity',
                        data: points.map((p) => p.v),
                        lineStyle: {width: 1.5, color},
                        itemStyle: {color},
                        areaStyle: {color, opacity: kind === 'drawdown' ? 0.12 : 0.08},
                    },
                ],
            },
            {notMerge: true},
        );

        const observer = new ResizeObserver(() => chart.resize());
        observer.observe(containerRef.current);

        return () => {
            observer.disconnect();
            chart.dispose();
        };
    }, [points, hasData, kind]);

    if (!hasData) {
        return (
            <div
                role="note"
                style={{
                    height,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    textAlign: 'center',
                    padding: 16,
                    border: '1px dashed var(--nq-border-strong)',
                    borderRadius: 6,
                    color: 'var(--nq-text-tertiary)',
                    fontSize: 13,
                    background: 'var(--nq-bg-panel)',
                }}
            >
                {unavailableText ?? '暂无时间序列数据'}
            </div>
        );
    }

    return <div ref={containerRef} style={{width: '100%', height}}/>;
}
