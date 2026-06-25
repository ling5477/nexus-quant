/**
 * Nq 基础业务组件统一出口（Design System v1）。
 *
 * 边界：AntD 继续负责 Form/Input/Select/Modal/Drawer/Tabs 等基础交互；
 * 本目录只承载带 NQ 业务语义的展示组件（状态、环境、数字、指标、风险、图表）。
 */
export {NqPageHeader} from '@/components/nq/NqPageHeader';
export {NqMetricCard} from '@/components/nq/NqMetricCard';
export type {NqMetricTone} from '@/components/nq/NqMetricCard';
export {NqStatusTag} from '@/components/nq/NqStatusTag';
export type {NqStatusTone} from '@/components/nq/NqStatusTag';
export {NqEnvironmentBadge} from '@/components/nq/NqEnvironmentBadge';
export {NqRiskBanner} from '@/components/nq/NqRiskBanner';
export {NqFilterBar} from '@/components/nq/NqFilterBar';
export {NqDataTable, nqNumericColumn} from '@/components/nq/NqDataTable';
export {NqDangerConfirmButton} from '@/components/nq/NqDangerConfirmButton';
export {NqAmountText, NqPercentText, NqPriceText, formatNqNumber} from '@/components/nq/NqNumericText';
export {NqEmptyState, NqErrorState, NqLoadingState} from '@/components/nq/NqStates';
export {NqEquityCurveChart} from '@/components/nq/charts/NqEquityCurveChart';
export {NqDrawdownChart} from '@/components/nq/charts/NqDrawdownChart';
export {NqPortfolioEquityChart, NqPortfolioDrawdownChart} from '@/components/nq/charts/NqPortfolioCurveChart';
