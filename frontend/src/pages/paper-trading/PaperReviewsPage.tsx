import {Alert, Space, Typography} from 'antd';

import {usePaperAutoReviewsQuery, usePaperStrategyEvaluationsQuery} from '@/hooks/usePaperTradingQuery';

import {PaperAutoReviewDashboard} from './components/PaperAutoReviewDashboard';
import {PaperStrategyEvaluationDashboard} from './components/PaperStrategyEvaluationDashboard';

/**
 * PaperReviewsPage 是 K5-C3 的 `/paper-trading/reviews` 真实子路由。
 *
 * Why:
 * Strategy Evaluation 与 Auto Review 都是跨 run 的只读复盘型聚合，不应继续挂在 runs 运行控制台首屏。
 * 本页只实例化 `usePaperStrategyEvaluationsQuery()` 与 `usePaperAutoReviewsQuery()` 两个独立 query，
 * 不读取 portfolio / diagnostics，不提升到 global store，也不改变后端 API、query key 或 retry 策略。
 *
 * 边界：
 * - Section A 固定承载 K3B Strategy Evaluation Dashboard。
 * - Section B 固定承载 K4B Auto Review Dashboard。
 * - 仅 Paper-only / rules-based 展示，不接 AI / DH runtime / LIVE / 真实交易所。
 * - 页面切换只走 React Router 挂载卸载，不做交叉 refetch。
 */
export function PaperReviewsPage() {
    const strategyEvaluationsQuery = usePaperStrategyEvaluationsQuery();
    const autoReviewsQuery = usePaperAutoReviewsQuery();

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Alert
                type="info"
                showIcon
                message="Paper-only reviews · Strategy evaluation + rules-based auto review"
                description={(
                    <Typography.Text type="secondary">
                        本页只消费 Paper strategy evaluations 与 auto reviews 两个只读 query；no investment advice，
                        不代表 LIVE 或真实交易表现，也不读取 credential 或访问真实交易所。
                    </Typography.Text>
                )}
            />

            <section aria-label="Strategy Evaluation Dashboard">
                <Typography.Text strong>Section A · Strategy Evaluation Dashboard</Typography.Text>
                <PaperStrategyEvaluationDashboard query={strategyEvaluationsQuery}/>
            </section>

            <section aria-label="Auto Review Dashboard">
                <Typography.Text strong>Section B · Auto Review Dashboard</Typography.Text>
                <PaperAutoReviewDashboard query={autoReviewsQuery}/>
            </section>
        </Space>
    );
}
