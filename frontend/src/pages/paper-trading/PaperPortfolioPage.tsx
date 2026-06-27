import {Space} from 'antd';

import {usePaperPortfolioSummaryQuery} from '@/hooks/usePaperTradingQuery';

import {PaperPortfolioDashboard} from './components/PaperPortfolioDashboard';
import {PaperRiskDrawdownDashboard} from './components/PaperRiskDashboard';
import {PaperStrategyRankingDashboard} from './components/PaperStrategyRankingDashboard';

/**
 * PaperPortfolioPage 是 K5-C1 的 `/paper-trading/portfolio` 真实子路由。
 *
 * Why:
 * 本页只迁移 Portfolio 只读展示：组合总览、分组摘要、风险回撤、策略排行、数据质量和组合资金 / 回撤曲线。
 * `portfolioQuery` 在页面顶层只调用一次，然后传给 Portfolio / Risk / Ranking 展示组件并复用同一份 data 渲染曲线，
 * 避免各 dashboard 各自发起同 key 查询。本页不包含 run lifecycle、factTab、mutation、diagnostics、
 * strategy evaluation 或 auto review；这些分别留在 `/paper-trading/runs`、`/diagnostics`、`/reviews`。
 */
export function PaperPortfolioPage() {
    const portfolioQuery = usePaperPortfolioSummaryQuery();

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <PaperPortfolioDashboard query={portfolioQuery}/>
            <PaperRiskDrawdownDashboard query={portfolioQuery}/>
            <PaperStrategyRankingDashboard query={portfolioQuery}/>
        </Space>
    );
}
