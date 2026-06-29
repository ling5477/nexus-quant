import { useEffect, useMemo, useRef } from 'react';
import {
  HistogramSeries,
  createChart,
  type IChartApi,
} from 'lightweight-charts';

import { DataFreshness } from '../status/DataFreshness';
import { DEFAULT_MARKET_CONVENTION } from '../tokens/nq-tokens';
import { nqLwcOptions } from '../theme/nqLwcOptions';
import { chartErrorText, toHistogramData } from './chartData';
import type { NqChartBaseProps } from './types';

import './nq-charts.css';

/**
 * NqVolumeChart 渲染 K 线成交量柱。
 *
 * 约束：成交量柱颜色使用 market convention 的 up/down，不复用 success / danger。
 */
export function NqVolumeChart({
  bars,
  height = 160,
  loading = false,
  error = null,
  stale = false,
  staleDetail,
  sourceLabel = 'Volume source',
  convention = DEFAULT_MARKET_CONVENTION,
  title = 'Volume',
  emptyText = '暂无成交量数据',
  className,
}: NqChartBaseProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const data = useMemo(() => toHistogramData(bars, convention), [bars, convention]);
  const errorText = chartErrorText(error);

  useEffect(() => {
    const element = containerRef.current;

    if (!element || errorText || loading || data.length === 0) {
      return;
    }

    const chart = createChart(element, {
      ...nqLwcOptions(),
      height,
      width: Math.max(element.clientWidth, 1),
    });
    chartRef.current = chart;

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: {type: 'volume'},
      priceLineVisible: false,
      lastValueVisible: false,
    });
    volumeSeries.setData(data);
    chart.timeScale().fitContent();

    const observer = new ResizeObserver(([entry]) => {
      chart.resize(Math.max(Math.floor(entry.contentRect.width), 1), height);
    });
    observer.observe(element);

    return () => {
      observer.disconnect();
      chart.remove();
      chartRef.current = null;
    };
  }, [data, errorText, height, loading]);

  return (
    <div
      className={className ? `nq-chart ${className}` : 'nq-chart'}
      style={{height}}
      data-testid="nq-volume-chart"
    >
      <div className="nq-chart__header">
        <span className="nq-chart__title">{title}</span>
        {stale ? (
          <span className="nq-chart__stale">
            <DataFreshness source={sourceLabel} state="stale" detail={staleDetail} inline/>
          </span>
        ) : null}
      </div>
      <div ref={containerRef} className="nq-chart__canvas" style={{height: height - 34}}/>
      {loading ? <div className="nq-chart__state">成交量加载中</div> : null}
      {!loading && !errorText && data.length === 0 ? <div className="nq-chart__state">{emptyText}</div> : null}
      {errorText ? <div className="nq-chart__state nq-chart__state--error">{errorText}</div> : null}
    </div>
  );
}
