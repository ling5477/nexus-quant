import {t} from '@/i18n';
import {useTranslation} from 'react-i18next';
import {useEffect, useMemo, useRef, useState} from 'react';
import {
    CandlestickSeries,
    LineSeries,
    createChart,
    type IChartApi,
    type ISeriesApi,
} from 'lightweight-charts';

import {DataFreshness} from '../status/DataFreshness';
import {DEFAULT_MARKET_CONVENTION} from '../tokens/nq-tokens';
import {nqCandleColors, nqLwcOptions} from '../theme/nqLwcOptions';
import {chartErrorText, toCandlestickData, toChartTime} from './chartData';
import type {NqChartBaseProps, NqKlineBar} from './types';
import {formatNumber} from '@/utils/formatters';
import {formatNqNumber} from '../format/nqFormat';
import {formatChartTime, chartTimeDate} from './chartTime';
import {movingAverage, candleChange} from './marketIndicators';
import {nqColor} from '@/theme/tokens';

import './nq-charts.css';

/**
 * NqKlineChart 是 NQ Design System 的静态 K 线基础组件。
 *
 * 约束：
 * 1) 只接收调用方传入的稳定内部 bar 类型，不直接绑定后端 DTO；
 * 2) 组件内不发请求、不读取 credential、不处理 order / trade / position；
 * 3) 涨跌色来自 market convention，和 success / danger 解耦。
 */
export function NqKlineChart({
    bars,
    height = 280,
    loading = false,
    error = null,
    stale = false,
    staleDetail,
    sourceLabel = t('chart.klineSource'),
    convention = DEFAULT_MARKET_CONVENTION,
    title = t('chart.kline'),
    emptyText = t('chart.klineEmpty'),
    className,
    intervalSeconds,
    movingAverageWindow,
    visibleRange,
    onVisibleRangeChange,
}: NqChartBaseProps) {
    useTranslation();
    const containerRef = useRef<HTMLDivElement | null>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const maRef = useRef<ISeriesApi<'Line'> | null>(null);
    const rangeChangeRef = useRef(onVisibleRangeChange);
    rangeChangeRef.current = onVisibleRangeChange;
    const data = useMemo(() => toCandlestickData(bars), [bars]);
    const errorText = chartErrorText(error);
    const [hovered, setHovered] = useState<NqKlineBar | null>(null);
    const [hoverPoint, setHoverPoint] = useState<{x: number; y: number} | null>(null);
    const [inspectionIndex, setInspectionIndex] = useState<number | null>(null);
    const indexedBars = useMemo(
        () => new Map(bars.map((bar) => [JSON.stringify(toChartTime(bar.time)), bar])),
        [bars],
    );
    const inspected =
        hovered ?? (inspectionIndex === null ? null : (bars[inspectionIndex] ?? null));
    const canvasHeight = Math.max(height - 34, 120);

    useEffect(() => {
        const element = containerRef.current;

        if (!element || errorText || loading || data.length === 0) {
            return;
        }

        const chart = createChart(element, {
            ...nqLwcOptions(),
            height: canvasHeight,
            width: Math.max(element.clientWidth, 1),
        });
        chartRef.current = chart;

        const candleSeries = chart.addSeries(CandlestickSeries, {
            ...nqCandleColors(convention),
            priceLineVisible: false,
            lastValueVisible: false,
        });
        candleSeries.setData(data);
        maRef.current = chart.addSeries(LineSeries, {
            color: nqColor.warning,
            lineWidth: 1,
            priceLineVisible: false,
            lastValueVisible: false,
            crosshairMarkerVisible: false,
        });
        let fittedVisibleContent = element.clientWidth > 0;
        if (fittedVisibleContent) chart.timeScale().fitContent();
        setHovered(null);
        setInspectionIndex(null);
        const onCrosshair = (
            event: Parameters<IChartApi['subscribeCrosshairMove']>[0] extends (
                event: infer E,
            ) => void
                ? E
                : never,
        ) => {
            setHovered(
                event.time !== undefined &&
                    event.point &&
                    event.point.x >= 0 &&
                    event.point.y >= 0 &&
                    event.point.x < element.clientWidth &&
                    event.point.y < canvasHeight
                    ? (indexedBars.get(JSON.stringify(event.time)) ?? null)
                    : null,
            );
            setHoverPoint(
                event.point
                    ? {
                          x: Math.max(8, Math.min(event.point.x + 12, element.clientWidth - 220)),
                          y: Math.max(36, Math.min(event.point.y + 44, canvasHeight - 100)),
                      }
                    : null,
            );
        };
        chart.subscribeCrosshairMove(onCrosshair);
        const onRangeChange = (range: {from: number; to: number} | null) =>
            rangeChangeRef.current?.(range);
        chart.timeScale().subscribeVisibleLogicalRangeChange(onRangeChange);

        const observer = new ResizeObserver(([entry]) => {
            const width = Math.floor(entry.contentRect.width);
            // 隐藏分区的零宽度不能改变时间轴；首次可见时再适配数据，后续保留用户缩放。
            if (width <= 0) return;
            chart.resize(width, canvasHeight);
            if (!fittedVisibleContent) {
                chart.timeScale().fitContent();
                fittedVisibleContent = true;
            }
        });
        observer.observe(element);

        return () => {
            observer.disconnect();
            chart.unsubscribeCrosshairMove(onCrosshair);
            chart.timeScale().unsubscribeVisibleLogicalRangeChange(onRangeChange);
            chart.remove();
            chartRef.current = null;
            maRef.current = null;
        };
    }, [convention, data, errorText, canvasHeight, loading, indexedBars]);

    useEffect(() => {
        maRef.current?.setData(
            movingAverageWindow && intervalSeconds
                ? movingAverage(bars, movingAverageWindow, intervalSeconds)
                : [],
        );
    }, [
        bars,
        data,
        movingAverageWindow,
        intervalSeconds,
        errorText,
        loading,
        canvasHeight,
        convention,
    ]);
    useEffect(() => {
        const scale = chartRef.current?.timeScale(),
            current = scale?.getVisibleLogicalRange();
        if (
            visibleRange &&
            scale &&
            (!current ||
                Math.abs(current.from - visibleRange.from) > 1e-6 ||
                Math.abs(current.to - visibleRange.to) > 1e-6)
        ) {
            scale.setVisibleLogicalRange(visibleRange);
        }
    }, [visibleRange, data, loading, errorText, canvasHeight, convention]);

    return (
        <div
            className={className ? `nq-chart ${className}` : 'nq-chart'}
            style={{minHeight: height}}
            data-testid="nq-kline-chart"
        >
            <div className="nq-chart__header">
                <span className="nq-chart__title">{title}</span>
                <button
                    type="button"
                    className="nq-chart__reset"
                    onClick={() => chartRef.current?.timeScale().fitContent()}
                >
                    {t('markets.resetZoom')}
                </button>
                {stale ? (
                    <span className="nq-chart__stale">
                        <DataFreshness
                            source={sourceLabel}
                            state="stale"
                            detail={staleDetail}
                            inline
                        />
                    </span>
                ) : null}
            </div>
            <div
                ref={containerRef}
                className="nq-chart__canvas"
                style={{height: canvasHeight}}
                onPointerLeave={() => setHovered(null)}
                onPointerCancel={() => setHovered(null)}
            />
            {hovered && hoverPoint && (
                <div
                    className="nq-chart__tooltip"
                    data-testid="kline-hover-tooltip"
                    style={{left: hoverPoint.x, top: hoverPoint.y}}
                >
                    <time>{formatChartTime(toChartTime(hovered.time)!)}</time>
                    {(['open', 'high', 'low', 'close', 'volume'] as const).map((key) => (
                        <div key={key}>
                            <span>{t(`chart.${key}`)}</span>
                            <strong>
                                {hovered.exact?.[key]
                                    ? formatNqNumber(hovered.exact[key], {exact: true})
                                    : formatNumber(hovered[key], 8)}
                            </strong>
                        </div>
                    ))}
                    <div>
                        <span>{t('markets.candleChange')}</span>
                        <strong>
                            {candleChange(hovered.open, hovered.close) === null
                                ? '—'
                                : `${formatNqNumber(candleChange(hovered.open, hovered.close), {signed: true})}%`}
                        </strong>
                    </div>
                </div>
            )}
            {!loading && !errorText && data.length > 0 && (
                <div className="nq-chart__inspection" data-testid="kline-inspection">
                    <span>
                        {inspected
                            ? t(hovered ? 'chart.hoveredBar' : 'chart.selectedBar')
                            : t('chart.inspectHint')}
                    </span>
                    {inspected && (
                        <>
                            <time>{formatChartTime(toChartTime(inspected.time)!)}</time>
                            {(['open', 'high', 'low', 'close', 'volume'] as const).map((key) => (
                                <span key={key}>
                                    {t(`chart.${key}`)}:{' '}
                                    <strong>
                                        {inspected.exact?.[key]
                                            ? formatNqNumber(inspected.exact[key], {exact: true})
                                            : typeof inspected[key] === 'number' &&
                                                Number.isFinite(inspected[key])
                                              ? formatNumber(inspected[key], 8)
                                              : '—'}
                                    </strong>
                                </span>
                            ))}
                            <span>
                                {t('markets.candleChange')}:{' '}
                                <strong>
                                    {candleChange(inspected.open, inspected.close) === null
                                        ? '—'
                                        : `${formatNqNumber(candleChange(inspected.open, inspected.close), {signed: true})}%`}
                                </strong>
                            </span>
                            {inspected.closeTime &&
                                Date.parse(inspected.closeTime) >= Date.now() && (
                                    <span>{t('markets.forming')}</span>
                                )}
                        </>
                    )}
                    <label>
                        {t('chart.selectBar')}{' '}
                        <input
                            type="range"
                            min={0}
                            max={Math.max(bars.length - 1, 0)}
                            aria-label={t('chart.selectBar')}
                            value={inspectionIndex ?? 0}
                            onChange={(event) => {
                                setHovered(null);
                                setInspectionIndex(Number(event.target.value));
                            }}
                        />
                    </label>
                    {inspectionIndex !== null && (
                        <button
                            type="button"
                            className="nq-chart__reset"
                            onClick={() => {
                                setHovered(null);
                                setInspectionIndex(null);
                            }}
                        >
                            {t('markets.clearSelection')}
                        </button>
                    )}
                </div>
            )}
            {movingAverageWindow && (
                <div className="nq-chart__indicator-note">
                    {t('markets.maDefinition', {window: movingAverageWindow})}
                </div>
            )}
            {loading ? <div className="nq-chart__state">{t('chart.klineLoading')}</div> : null}
            {!loading && !errorText && data.length === 0 ? (
                <div className="nq-chart__state">{emptyText}</div>
            ) : null}
            {errorText ? (
                <div className="nq-chart__state nq-chart__state--error">{errorText}</div>
            ) : null}
        </div>
    );
}

/** 使用明确的 UTC 口径，不把图表日历日期当作浏览器本地时间。 */
export function chartUtcTime(time: NqKlineBar['time']): string {
    const date = chartTimeDate(time);
    return date && Number.isFinite(date.getTime())
        ? `${date.toISOString().replace('T', ' ').replace('.000Z', '')} UTC`
        : '—';
}
