import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Card, Segmented, Space, Tag} from 'antd';
import {Outlet, useLocation, useNavigate} from 'react-router-dom';

import {NqPageHeader, RuntimeGuardBanner} from '@/components/nq';

const PAPER_TRADING_ROUTE_OPTIONS = [
    {get label() { return t('pages:runs'); }, value: '/paper-trading/runs'},
    {get label() { return t('pages:portfolio'); }, value: '/paper-trading/portfolio'},
    {get label() { return t('pages:diagnostics'); }, value: '/paper-trading/diagnostics'},
    {get label() { return t('pages:reviews'); }, value: '/paper-trading/reviews'},
];

function activePaperTradingRoute(pathname: string): string {
    const matched = PAPER_TRADING_ROUTE_OPTIONS.find((option) => pathname.startsWith(option.value));
    return matched?.value ?? '/paper-trading/runs';
}

/**
 * PaperTradingRouteShell 只承载 K5-B 子路由壳。
 *
 * Why:
 * K5-B 的目标是先建立 `/paper-trading/*` 路由骨架，为 K5-C 逐模块迁移留出口。
 * 当前不迁移 Portfolio / Diagnostics / Reviews 业务模块，也不新增 query；Runs 子路由继续渲染旧完整页，
 * 以保持旧入口、侧边栏高亮和 product-loop E2E 行为兼容。
 */
export function PaperTradingRouteShell() {
    useTranslation('pages');
    const location = useLocation();
    const navigate = useNavigate();
    const activeRoute = activePaperTradingRoute(location.pathname);

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Card className="page-card" variant="borderless">
                <NqPageHeader
                    title={t('pages:paperTrading')}
                    description={t('pages:paperRunsPortfolioMonitoringDiagnosticsEvaluationAndAutomatedReviews')}
                    badge={t('pages:routeShell')}
                    extra={(
                        <Space size={6} wrap>
                            <Tag color="blue">{t('pages:simPaperOnly')}</Tag>
                            <Tag color="red">{t('pages:liveDisabled2')}</Tag>
                            <Tag color="default">{t('pages:noRealExchangeConnection')}</Tag>
                            <Tag color="default">{t('pages:notInvestmentAdvice')}</Tag>
                        </Space>
                    )}
                />
            </Card>

            <Card className="page-section" variant="borderless" styles={{body: {paddingBlock: 12}}}>
                <Space direction="vertical" size={10} style={{display: 'flex'}}>
                    <Segmented
                        aria-label={t('pages:paperTradingNavigation')}
                        options={PAPER_TRADING_ROUTE_OPTIONS}
                        value={activeRoute}
                        onChange={(value) => navigate(value)}
                    />
                    <RuntimeGuardBanner variant="paper-boundary"/>
                </Space>
            </Card>

            <Outlet/>
        </Space>
    );
}
