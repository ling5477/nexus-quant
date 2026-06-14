// nqLwcOptions.ts — Lightweight Charts(K 线/行情主图)选项,从唯一来源派生(B0)
import { nqTokens as t, marketColors, DEFAULT_MARKET_CONVENTION, type MarketConvention } from '../tokens/nq-tokens';

export function nqLwcOptions(convention: MarketConvention = DEFAULT_MARKET_CONVENTION) {
  return {
    layout: { background: { color: 'transparent' }, textColor: t.text.tertiary, fontFamily: t.font.mono },
    grid: { vertLines: { color: t.border.subtle }, horzLines: { color: t.border.subtle } },
    rightPriceScale: { borderColor: t.border.strong },
    timeScale: { borderColor: t.border.strong },
    crosshair: { vertLine: { color: t.border.strong }, horzLine: { color: t.border.strong } },
  };
}

export function nqCandleColors(convention: MarketConvention = DEFAULT_MARKET_CONVENTION) {
  const m = marketColors(convention);
  return {
    upColor: m.up, borderUpColor: m.up, wickUpColor: m.up,
    downColor: m.down, borderDownColor: m.down, wickDownColor: m.down,
  };
}
