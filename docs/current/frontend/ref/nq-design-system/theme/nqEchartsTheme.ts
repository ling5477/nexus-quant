// nqEchartsTheme.ts — ECharts 主题(权益/PnL/回撤/监控),从唯一来源派生(B0)
import * as echarts from 'echarts';
import { nqTokens as t, marketColors, DEFAULT_MARKET_CONVENTION, type MarketConvention } from '../tokens/nq-tokens';

export function registerNqEchartsTheme(convention: MarketConvention = DEFAULT_MARKET_CONVENTION): void {
  const m = marketColors(convention);
  echarts.registerTheme('nq', {
    backgroundColor: 'transparent',
    textStyle: { fontFamily: t.font.ui, color: t.text.secondary },
    color: [t.semantic.primary, t.semantic.info, t.semantic.warning, m.up, m.down, '#9b8cff'],
    title: { textStyle: { color: t.text.primary } },
    legend: { textStyle: { color: t.text.tertiary } },
    grid: { borderColor: t.border.subtle },
    categoryAxis: {
      axisLine: { lineStyle: { color: t.border.strong } },
      axisLabel: { color: t.text.tertiary, fontFamily: t.font.mono },
      splitLine: { lineStyle: { color: t.border.subtle } },
    },
    valueAxis: {
      axisLine: { lineStyle: { color: t.border.strong } },
      axisLabel: { color: t.text.tertiary, fontFamily: t.font.mono },
      splitLine: { lineStyle: { color: t.border.subtle } },
    },
    tooltip: {
      backgroundColor: t.bg.elevated,
      borderColor: t.border.strong,
      textStyle: { color: t.text.primary, fontFamily: t.font.mono },
    },
  });
}
// 用法: registerNqEchartsTheme(); <ReactECharts theme="nq" option={...} />
