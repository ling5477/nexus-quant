import {PaperTradingPage} from '@/pages/paper-trading/PaperTradingPage';

/**
 * PaperTradingRunsPage 是 K5-B 的兼容子路由包装。
 *
 * Why:
 * `/paper-trading/runs` 在 K5-B 阶段必须继续渲染旧完整 `PaperTradingPage`，确保 run list/detail、
 * portfolio / diagnostics / reviews、query key、局部状态和 mutation 行为完全沿用旧页面。后续 K5-C 才允许
 * 把大模块逐步迁移到对应子路由。
 */
export function PaperTradingRunsPage() {
    return <PaperTradingPage/>;
}
