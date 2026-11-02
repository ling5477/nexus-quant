// 兼容旧 v2 API，视觉值统一派生自控制台主题，避免独立页、图表与业务页分叉。
import {nqColor, nqFont, nqRadius} from '@/theme/tokens';

export const nqTokens = {
  bg: {app: nqColor.bgSunken, canvas: nqColor.bgPage, panel: nqColor.bgPanel, elevated: nqColor.bgElevated},
  text: {primary: nqColor.text, secondary: nqColor.textSecondary, tertiary: nqColor.textTertiary, disabled: nqColor.textDisabled},
  border: {subtle: nqColor.borderSubtle, strong: nqColor.border},
  semantic: {primary: nqColor.primary, success: nqColor.success, warning: nqColor.warning, danger: nqColor.danger, info: nqColor.info},
  // 行情方向:与 success/danger 解耦,由 convention 决定具体 hex
  market: {
    CN_STOCK: {up: nqColor.up, down: nqColor.down, flat: nqColor.neutral},
    INTL_CRYPTO: {up: nqColor.down, down: nqColor.up, flat: nqColor.neutral},
  },
  env: {PAPER: nqColor.paper, DEMO: nqColor.demo, LIVE: nqColor.live, READONLY: nqColor.neutral, AUDITED: nqColor.success},
  radius: {sm: nqRadius.sm, md: nqRadius.md},
  font: {
    ui: nqFont.family,
    mono: nqFont.familyMono,
    sizeBase: 13, sizeCJK: 14,
  },
  space: [4, 8, 12, 16, 24, 32] as const,
} as const;

export type MarketConvention = 'CN_STOCK' | 'INTL_CRYPTO';
/** NQ 业务 UI 固定红涨绿跌；显式 convention 参数仅保留给开发诊断与兼容图表。 */
export const DEFAULT_MARKET_CONVENTION: MarketConvention = 'CN_STOCK';
export const marketColors = (c: MarketConvention = DEFAULT_MARKET_CONVENTION) => nqTokens.market[c];
