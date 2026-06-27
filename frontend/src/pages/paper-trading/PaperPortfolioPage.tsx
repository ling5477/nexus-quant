import {Card, Space, Typography} from 'antd';

import {
    NqAmountText,
    NqEmptyState,
    NqMetricCard,
    NqPercentText,
    NqPortfolioDrawdownChart,
    NqPortfolioEquityChart,
    NqRiskBanner,
} from '@/components/nq';
import {usePaperPortfolioSummaryQuery} from '@/hooks/usePaperTradingQuery';
import type {PaperPortfolioCurve, PaperPortfolioCurvePoint, PaperPortfolioSummaryResponse} from '@/types/paper-trading';

import {PaperPortfolioDashboard} from './components/PaperPortfolioDashboard';

function asPortfolioSummary(raw: unknown): PaperPortfolioSummaryResponse | null {
    return raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
        ? (raw as PaperPortfolioSummaryResponse)
        : null;
}

/**
 * PaperPortfolioPage 是 K5-C1 的 `/paper-trading/portfolio` 真实子路由。
 *
 * Why:
 * 本页只迁移 Portfolio 只读展示：组合总览、分组摘要、数据质量和组合资金 / 回撤曲线。
 * `portfolioQuery` 在页面顶层只调用一次，然后传给展示组件并复用同一份 data 渲染曲线，避免 Dashboard、
 * 风险驾驶舱或排行组件各自发起同 key 查询。本页不包含 run lifecycle、factTab、mutation、diagnostics、
 * strategy evaluation 或 auto review；这些仍留在 `/paper-trading/runs`。
 */
export function PaperPortfolioPage() {
    const portfolioQuery = usePaperPortfolioSummaryQuery();
    const portfolio = asPortfolioSummary(portfolioQuery.data);

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <PaperPortfolioDashboard query={portfolioQuery}/>
            <PaperPortfolioCurveCard curve={portfolio?.portfolioCurve}/>
        </Space>
    );
}

/**
 * 组合资金 / 回撤曲线卡只消费 Portfolio summary 响应中的 `portfolioCurve`。
 * 空数据按 Paper-only 安全口径显示空态，不新增请求，也不回退读取真实交易所或账户余额。
 */
function PaperPortfolioCurveCard({curve}: {curve: PaperPortfolioCurve | null | undefined}) {
    const points: PaperPortfolioCurvePoint[] = curve?.points ?? [];
    const hasCurve = Boolean(curve) && points.length > 0;

    return (
        <Card
            className="page-section"
            variant="borderless"
            title="组合资金曲线与回撤"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="info"
                    message="组合曲线仅基于 Paper summary 的只读聚合结果。"
                    description="本卡不读取真实交易所、credential 或账户余额；数据不足时显示空态，不伪造收益或回撤。"
                />

                {hasCurve && curve ? (
                    <>
                        <div className="nq-status-strip">
                            <NqMetricCard label="最新组合 equity" value={<NqAmountText value={curve.latestEquity}/>}/>
                            <NqMetricCard label="资金峰值" value={<NqAmountText value={curve.peakEquity}/>}/>
                            <NqMetricCard
                                label="组合当前回撤"
                                value={curve.currentDrawdown !== null
                                    ? <NqPercentText value={curve.currentDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="warning"
                            />
                            <NqMetricCard
                                label="组合最大回撤"
                                value={curve.maxDrawdown !== null
                                    ? <NqPercentText value={curve.maxDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="danger"
                            />
                            <NqMetricCard
                                label="可比 run"
                                value={String(curve.coverage.comparableRunCount)}
                                footer={`缺 equity ${curve.coverage.missingEquityRunCount} · 不完整点 ${curve.coverage.incompletePointCount}`}
                            />
                        </div>

                        <Space direction="vertical" size={4} style={{display: 'flex'}}>
                            <Typography.Text strong style={{fontSize: 13}}>组合资金曲线</Typography.Text>
                            <NqPortfolioEquityChart points={points}/>
                        </Space>

                        <Space direction="vertical" size={4} style={{display: 'flex'}}>
                            <Typography.Text strong style={{fontSize: 13}}>组合回撤曲线</Typography.Text>
                            <NqPortfolioDrawdownChart points={points}/>
                        </Space>

                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            覆盖度：可比 run {curve.coverage.comparableRunCount} · 缺 equity {curve.coverage.missingEquityRunCount}
                            {' '}· 不完整点 {curve.coverage.incompletePointCount} · 采样点 {curve.pointCount}。
                            该曲线是 Paper 组合资金合计曲线，不等同于严格时间加权收益，也不代表 LIVE 或真实交易表现。
                        </Typography.Text>
                    </>
                ) : (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="组合 equity curve 暂不可用（数据不足或旧版本响应）。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>
                            数据不足，不展示组合时间序列回撤。
                        </Typography.Text>
                    </Space>
                )}
            </Space>
        </Card>
    );
}
