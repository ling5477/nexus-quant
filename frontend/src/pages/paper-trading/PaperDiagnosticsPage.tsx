import {usePaperExecutionDiagnosticsQuery} from '@/hooks/usePaperTradingQuery';

import {PaperExecutionDiagnosticsDashboard} from './components/PaperExecutionDiagnosticsDashboard';

/**
 * PaperDiagnosticsPage 是 K5-C2 的 `/paper-trading/diagnostics` 真实子路由。
 *
 * Why:
 * 本页只迁移 Execution Diagnostics 只读诊断视图。页面级唯一职责是实例化一次
 * `usePaperExecutionDiagnosticsQuery()`，再把同一个 query 实例传给 dashboard 处理 loading / error / empty /
 * fallback / cause filter / severity filter。这样可以让 diagnostics 请求只在本路由挂载时触发，不影响
 * `/paper-trading/portfolio` 的 portfolio summary 单请求，也不改变 `/paper-trading/runs` 旧完整页兼容行为。
 *
 * 边界：
 * - 不新增 API client、query key、hook 或 global store。
 * - 不迁移 Strategy Evaluation / Auto Review / Risk / Ranking。
 * - 仅展示 Paper-only、rules-based 诊断结果，不接 AI / DH runtime / LIVE / 真实交易所。
 */
export function PaperDiagnosticsPage() {
    const diagnosticsQuery = usePaperExecutionDiagnosticsQuery();

    return <PaperExecutionDiagnosticsDashboard query={diagnosticsQuery}/>;
}
