import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Alert, Space, Typography} from 'antd';

import {usePaperAutoReviewsQuery, usePaperStrategyEvaluationsQuery} from '@/features/paper-trading/hooks/usePaperTradingQuery';

import {PaperAutoReviewDashboard} from './components/PaperAutoReviewDashboard';
import {PaperStrategyEvaluationDashboard} from './components/PaperStrategyEvaluationDashboard';

/**
 * PaperReviewsPage 是 现有的 `/paper-trading/reviews` 真实子路由。
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
    useTranslation('pages');
    const strategyEvaluationsQuery = usePaperStrategyEvaluationsQuery();
    const autoReviewsQuery = usePaperAutoReviewsQuery();

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Alert
                type="info"
                showIcon
                message={t('pages:paperOnlyReviewsStrategyEvaluationAndRuleBasedReviews')}
                description={(
                    <Typography.Text type="secondary">
                        {t('pages:thisPageReadsPaperStrategyEvaluationsAndAutomatedReviewsOnlyItProvidesNoInvestmentAdviceOrLivePerfor')}</Typography.Text>
                )}
            />

            <section aria-label={t('pages:strategyEvaluationDashboard')}>
                <Typography.Text strong>{t('pages:sectionAStrategyEvaluationDashboard')}</Typography.Text>
                <PaperStrategyEvaluationDashboard query={strategyEvaluationsQuery}/>
            </section>

            <section aria-label={t('pages:automatedReviewDashboard')}>
                <Typography.Text strong>{t('pages:sectionBAutomatedReviewDashboard')}</Typography.Text>
                <PaperAutoReviewDashboard query={autoReviewsQuery}/>
            </section>
        </Space>
    );
}
