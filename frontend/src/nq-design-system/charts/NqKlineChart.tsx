import { useEffect, useMemo, useRef } from 'react';
import {
  CandlestickSeries,
  createChart,
  type IChartApi,
} from 'lightweight-charts';

import { DataFreshness } from '../status/DataFreshness';
import { DEFAULT_MARKET_CONVENTION } from '../tokens/nq-tokens';
import { nqCandleColors, nqLwcOptions } from '../theme/nqLwcOptions';
import { chartErrorText, toCandlestickData } from './chartData';
import type { NqChartBaseProps } from './types';

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
  sourceLabel = 'Kline source',
  convention = DEFAULT_MARKET_CONVENTION,
  title = 'K-line',
  emptyText = '暂无 K 线数据',
  className,
}: NqChartBaseProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const data = useMemo(() => toCandlestickData(bars), [bars]);
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

    const candleSeries = chart.addSeries(CandlestickSeries, {
      ...nqCandleColors(convention),
      priceLineVisible: false,
      lastValueVisible: false,
    });
    candleSeries.setData(data);
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
  }, [convention, data, errorText, height, loading]);

  return (
    <div
      className={className ? `nq-chart ${className}` : 'nq-chart'}
      style={{height}}
      data-testid="nq-kline-chart"
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
      {loading ? <div className="nq-chart__state">K 线加载中</div> : null}
      {!loading && !errorText && data.length === 0 ? <div className="nq-chart__state">{emptyText}</div> : null}
      {errorText ? <div className="nq-chart__state nq-chart__state--error">{errorText}</div> : null}
    </div>
  );
}
