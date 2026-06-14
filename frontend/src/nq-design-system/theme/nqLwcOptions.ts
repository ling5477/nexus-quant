// nqLwcOptions.ts — Lightweight Charts(K 线/行情主图)选项,从唯一来源派生(B0 / Design Tokens v2)
// 说明:本文件只生成图表 chrome(坐标轴/网格/十字线)与 K 线涨跌色,不直接依赖 lightweight-charts 库;
// 颜色全部来自 nq-tokens,K 线涨跌色随行情惯例(convention)翻转。
import { nqTokens as t, marketColors, DEFAULT_MARKET_CONVENTION, type MarketConvention } from '../tokens/nq-tokens';

/**
 * Lightweight Charts 通用选项(图表 chrome)。
 * 注:图表 chrome 与行情惯例无关(涨跌色只作用于 K 线 series,见 nqCandleColors),
 * 因此此处不接收 convention 参数(本仓库启用 noUnusedParameters)。
 */
export function nqLwcOptions() {
  return {
    layout: { background: { color: 'transparent' }, textColor: t.text.tertiary, fontFamily: t.font.mono },
    grid: { vertLines: { color: t.border.subtle }, horzLines: { color: t.border.subtle } },
    rightPriceScale: { borderColor: t.border.strong },
    timeScale: { borderColor: t.border.strong },
    crosshair: { vertLine: { color: t.border.strong }, horzLine: { color: t.border.strong } },
  };
}

/** K 线涨跌色:随行情惯例翻转(CN_STOCK 红涨绿跌 / INTL_CRYPTO 绿涨红跌)。 */
export function nqCandleColors(convention: MarketConvention = DEFAULT_MARKET_CONVENTION) {
  const m = marketColors(convention);
  return {
    upColor: m.up, borderUpColor: m.up, wickUpColor: m.up,
    downColor: m.down, borderDownColor: m.down, wickDownColor: m.down,
  };
}
