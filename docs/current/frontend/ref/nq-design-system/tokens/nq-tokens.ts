// nq-tokens.ts — NQ Console 设计 token 唯一来源(B0)
// CSS 变量、AntD 主题、ECharts、Lightweight Charts 全部从这里派生。禁止任何页面/图表私配颜色。
// 颜色已过 WCAG AA 对比度校验(详见 NQ_DESIGN_TOKENS_V2.md)。

export const nqTokens = {
  bg:     { app: '#070f1c', canvas: '#0b1322', panel: '#0f1b2d', elevated: '#16263d' },
  text:   { primary: '#eef3ff', secondary: '#bcc8de', tertiary: '#93a1ba', disabled: '#6b7990' },
  border: { subtle: '#22324a', strong: '#3a4d6e' },
  semantic: { primary: '#5b8cff', success: '#3ad29f', warning: '#fbbf3f', danger: '#ff6166', info: '#56c7f5' },
  // 行情方向:与 success/danger 解耦,由 convention 决定具体 hex
  market: {
    CN_STOCK:    { up: '#ff5c6c', down: '#33d6a6', flat: '#93a1ba' }, // 红涨绿跌(A 股习惯)
    INTL_CRYPTO: { up: '#33d6a6', down: '#ff5c6c', flat: '#93a1ba' }, // 绿涨红跌(Binance/OKX 默认)
  },
  env: { PAPER: '#3b82f6', DEMO: '#7c5cff', LIVE: '#ff4d4f', READONLY: '#667085', AUDITED: '#3ad29f' },
  radius: { sm: 4, md: 6 },
  font: {
    ui: "'Inter','HarmonyOS Sans SC','PingFang SC','Microsoft YaHei',system-ui,sans-serif",
    mono: "'JetBrains Mono','Roboto Mono',ui-monospace,monospace",
    sizeBase: 13, sizeCJK: 14,
  },
  space: [4, 8, 12, 16, 24, 32] as const,
} as const;

export type MarketConvention = 'CN_STOCK' | 'INTL_CRYPTO';
export const DEFAULT_MARKET_CONVENTION: MarketConvention = 'INTL_CRYPTO';
export const marketColors = (c: MarketConvention = DEFAULT_MARKET_CONVENTION) => nqTokens.market[c];
