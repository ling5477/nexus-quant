import {t} from '@/i18n/index';
import {useTranslation} from 'react-i18next';
import {useEffect, useRef} from 'react';

import {echarts} from '@/nq-design-system/charts/echarts-core';
import {nqTokens, registerNqEchartsTheme} from '@/nq-design-system/index';
import {formatDateTime, formatNumber} from '@/utils/formatters';
import {formatChartTime} from '@/nq-design-system/charts/chartTime';
import type {SimPnlSnapshotItem} from './backtestsTypes';

export interface BacktestCurvePoint {
    /** 时间轴标签(category)。 */
    t: string;
    /** 数值(权益或回撤)。 */
    v: number | null;
    /** 悬停展示后端词法金额，图表坐标才使用浮点。 */
    exact?: string;
    discountAssetCurrency?: string | null;
    discountAssetBalance?: string | null;
    discountAssetQuoteValue?: string | null;
    /** 同一可见时点的原估值顺序；旧快照未记录时保持缺失。 */
    valuationSequence?: string | null;
}

/** 只有绘图坐标转换浮点；未知权益保留断点，优惠币余额与估值的原金额供悬停解释。 */
export function backtestCurvePointFromPnl(row: SimPnlSnapshotItem): BacktestCurvePoint {
    const coordinate = row.equity == null ? null : Number(row.equity);
    return {
        t: row.snapshotTime,
        v: coordinate != null && Number.isFinite(coordinate) ? coordinate : null,
        exact: row.equity == null ? undefined : String(row.equity),
        discountAssetCurrency: row.discountAssetCurrency,
        discountAssetBalance: row.discountAssetBalance,
        discountAssetQuoteValue: row.discountAssetQuoteValue,
        valuationSequence: row.valuationSequence,
    };
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
export function BacktestCurveChart({
    points,
    height = 260,
    kind = 'equity',
    unavailableText,
}: BacktestCurveChartProps) {
    useTranslation();
    const containerRef = useRef<HTMLDivElement | null>(null);
    const hasData =
        Array.isArray(points) &&
        points.some((point) => point.v != null && Number.isFinite(point.v));

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
                tooltip: {
                    trigger: 'axis',
                    confine: true,
                    backgroundColor: 'rgba(12,28,43,.55)',
                    borderColor: 'rgba(80,120,160,.3)',
                    formatter: (params: unknown) => {
                        const first = Array.isArray(params)
                            ? (params[0] as {dataIndex?: number})
                            : null;
                        const point = first?.dataIndex != null ? points[first.dataIndex] : null;
                        if (!point) return '—';
                        const escape = (value: string) =>
                            value.replace(
                                /[&<>"']/g,
                                (char) =>
                                    ({
                                        '&': '&amp;',
                                        '<': '&lt;',
                                        '>': '&gt;',
                                        '"': '&quot;',
                                        "'": '&#39;',
                                    })[char]!,
                            );
                        return (
                            escape(formatDateTime(point.t)) +
                            '<br/>' +
                            escape(formatNumber(point.exact ?? point.v, 8)) +
                            (point.discountAssetCurrency
                                ? '<br/>' +
                                  escape(t('pages:discountFee.balance')) +
                                  ': ' +
                                  escape(formatNumber(point.discountAssetBalance, 8)) +
                                  ' ' +
                                  escape(point.discountAssetCurrency) +
                                  '<br/>' +
                                  escape(t('pages:discountFee.quoteValue')) +
                                  ': ' +
                                  escape(formatNumber(point.discountAssetQuoteValue, 8)) +
                                  ' USDT'
                                : '')
                        );
                    },
                },
                xAxis: {
                    type: 'category',
                    boundaryGap: false,
                    data: points.map((p) => p.t),
                    axisLabel: {formatter: (time: string) => formatChartTime(time, true)},
                },
                yAxis: {type: 'value', scale: kind === 'equity'},
                series: [
                    {
                        type: 'line',
                        showSymbol: false,
                        smooth: false,
                        connectNulls: false,
                        data: points.map((p) => (p.v != null && Number.isFinite(p.v) ? p.v : null)),
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
        const canvas = containerRef.current;
        const hideTip = () => chart.dispatchAction({type: 'hideTip'});
        canvas.addEventListener('mouseleave', hideTip);

        return () => {
            observer.disconnect();
            canvas.removeEventListener('mouseleave', hideTip);
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
                {unavailableText ?? t('chart.empty')}
            </div>
        );
    }

    return <div ref={containerRef} style={{width: '100%', height}} />;
}
