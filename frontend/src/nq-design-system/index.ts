// index.ts — NQ Console Design System (Design Tokens v2) 统一导出
export * from './tokens/nq-tokens';
export * from './tokens/nq-css-vars';
export { nqAntdTheme } from './theme/nqAntdTheme';
export { registerNqEchartsTheme } from './theme/nqEchartsTheme';
export { nqLwcOptions, nqCandleColors } from './theme/nqLwcOptions';
export { StatusTag } from './status/StatusTag';
export type { StatusTone } from './status/StatusTag';
export { EnvironmentBadge } from './status/EnvironmentBadge';
export type { NqEnv } from './status/EnvironmentBadge';
export { RiskBanner } from './status/RiskBanner';
export { DataFreshness } from './status/DataFreshness';
export type { FreshnessState } from './status/DataFreshness';
export { AppShell } from './shell/AppShell';
export {NqPageScaffold} from './shell/NqPageScaffold';
export {BrandLockup} from './brand/BrandLockup';
export {ExchangeBadge, ExchangeIcon} from './brand/ExchangeBadge';
export { NqKlineChart, NqVolumeChart } from './charts';
export type { NqChartBaseProps, NqKlineBar } from './charts';

// 格式化:纯函数 + 表格列组件
export {
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
} from './format/nqFormat';
export { NumberCell, MoneyCell, PercentCell, ChangeCell, StatusCell } from './format/cells';

// 表格密度:token + class 助手(需 import './table/nq-table.css')
export {
  NQ_TABLE_DENSITY,
  nqTableClassName,
} from './table/tableDensity';
export type { NqTableDensity } from './table/tableDensity';
