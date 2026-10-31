import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Button, Card, Popover, Segmented, Space, Tag, Typography} from 'antd';
import {Outlet, useLocation, useNavigate} from 'react-router-dom';

import {NqPageHeader} from '@/components/nq';
import {simAnalysisNavigationSearch} from '@/features/paper-trading/analysis-navigation';

const PAPER_TRADING_ROUTE_OPTIONS = [
    {
        get label() {
            return t('pages:runs');
        },
        value: '/paper-trading/runs',
    },
    {
        get label() {
            return t('workspace.navigation.assets');
        },
        value: '/paper-trading/portfolio',
    },
    {
        get label() {
            return t('pages:diagnostics');
        },
        value: '/paper-trading/diagnostics',
    },
    {
        get label() {
            return t('workspace.navigation.reviews');
        },
        value: '/paper-trading/reviews',
    },
];

function activePaperTradingRoute(pathname: string): string {
    const matched = PAPER_TRADING_ROUTE_OPTIONS.find((option) => pathname.startsWith(option.value));
    return matched?.value ?? '/paper-trading/runs';
}

/**
 * PaperTradingRouteShell 只承载模拟交易子路由壳。
 *
 * Why:
 * 统一保留 `/paper-trading/*` 的导航、安全提示和旧入口重定向；组合、诊断、复盘与运行页由各自子路由拥有。
 * 路由壳不发起业务 query，避免切换页面时提前加载其他子路由的数据。
 */
export function PaperTradingRouteShell() {
    useTranslation('pages');
    const location = useLocation();
    const navigate = useNavigate();
    const activeRoute = activePaperTradingRoute(location.pathname);
    const analysis = !activeRoute.endsWith('/runs');
    const analysisTitle = activeRoute.endsWith('/portfolio')
        ? t('workspace.navigation.assets')
        : activeRoute.endsWith('/reviews')
          ? t('workspace.navigation.reviews')
          : activeRoute.endsWith('/diagnostics')
            ? t('pages:paperExecutionDiagnostics')
            : t('pages:paperTrading');

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Card className="page-card" variant="borderless">
                <NqPageHeader
                    title={analysisTitle}
                    description={
                        activeRoute.endsWith('/runs')
                            ? t(
                                  'pages:paperRunsPortfolioMonitoringDiagnosticsEvaluationAndAutomatedReviews',
                              )
                            : undefined
                    }
                    badge={analysis ? undefined : t('pages:routeShell')}
                    extra={
                        analysis ? (
                            <Space size={6} wrap>
                                <Tag color="blue">{t('pages:simPaperOnly')}</Tag>
                                <Tag color="red">{t('pages:liveDisabled2')}</Tag>
                                <Popover
                                    trigger="click"
                                    title={t('pages:legacyAnalysis.workspaceScope')}
                                    content={
                                        <Typography.Paragraph style={{maxWidth: 280, margin: 0}}>
                                            {t('pages:legacyAnalysis.workspaceScopeMeaning')}
                                        </Typography.Paragraph>
                                    }
                                >
                                    <Button type="text" size="small">
                                        {t('pages:legacyAnalysis.workspaceScope')}
                                    </Button>
                                </Popover>
                            </Space>
                        ) : (
                            <Space size={6} wrap>
                                <Tag color="blue">{t('pages:simPaperOnly')}</Tag>
                                <Tag color="red">{t('pages:liveDisabled2')}</Tag>
                                <Tag color="default">{t('pages:noRealExchangeConnection')}</Tag>
                                <Tag color="default">{t('pages:notInvestmentAdvice')}</Tag>
                            </Space>
                        )
                    }
                />
            </Card>

            <Card className="page-section" variant="borderless" styles={{body: {paddingBlock: 12}}}>
                <div style={{minWidth: 0, maxWidth: '100%', overflowX: 'auto'}}>
                    <Segmented
                        aria-label={t('pages:paperTradingNavigation')}
                        options={PAPER_TRADING_ROUTE_OPTIONS.map(({label, value}) => ({
                            label,
                            value,
                        }))}
                        value={activeRoute}
                        onChange={(value) => {
                            navigate(String(value) + simAnalysisNavigationSearch(location.search));
                        }}
                    />
                </div>
            </Card>

            <Outlet />
        </Space>
    );
}
