// index.ts — NQ Console Design System (B0 / Design Tokens v2) 统一导出
export * from './tokens/nq-tokens';
export * from './tokens/nq-css-vars';
export { nqAntdTheme } from './theme/nqAntdTheme';
export { registerNqEchartsTheme } from './theme/nqEchartsTheme';
export { nqLwcOptions, nqCandleColors } from './theme/nqLwcOptions';
export { StatusTag } from './status/StatusTag';
export type { StatusTagProps, StatusTone } from './status/StatusTag';
export { EnvironmentBadge } from './status/EnvironmentBadge';
export type { EnvironmentBadgeProps, NqEnv } from './status/EnvironmentBadge';
export { RiskBanner } from './status/RiskBanner';
export type { RiskBannerProps, RiskSeverity } from './status/RiskBanner';
export { DataFreshness } from './status/DataFreshness';
export type { DataFreshnessProps, FreshnessState } from './status/DataFreshness';
export { AppShell } from './shell/AppShell';
export type { AppShellProps } from './shell/AppShell';

// 格式化(B0.2):纯函数 + 表格列组件
export {
  isNqEmpty,
  formatNqNumber,
  formatNqMoney,
  formatNqPercent,
  nqDirectionOf,
  NQ_DIRECTION_VAR,
} from './format/nqFormat';
export type {
  NqNumberFormatOptions,
  NqMoneyFormatOptions,
  NqPercentFormatOptions,
  NqDirection,
} from './format/nqFormat';
export { NumberCell, MoneyCell, PercentCell, ChangeCell, StatusCell } from './format/cells';
export type { PercentCellProps, ChangeCellProps, StatusCellProps } from './format/cells';

// 表格密度(B0.2):token + class 助手(需 import './table/nq-table.css')
export {
  NQ_TABLE_DENSITY,
  NQ_DEFAULT_TABLE_DENSITY,
  nqTableClassName,
  nqAntdTableCellPadding,
} from './table/tableDensity';
export type { NqTableDensity, NqTableDensityToken } from './table/tableDensity';
