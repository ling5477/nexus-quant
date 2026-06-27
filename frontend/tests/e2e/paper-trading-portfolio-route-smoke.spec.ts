import {expect, test, type Page, type Route} from 'playwright/test';

const portfolioSummary = {
    overview: {
        totalRuns: 3,
        runningCount: 1,
        stoppedCount: 2,
        failedCount: 0,
        cancelledCount: 0,
        createdCount: 0,
        totalInitialEquity: 200000,
        totalCurrentEquity: 216000,
        totalPnl: 16000,
        totalReturn: 0.08,
        returnEligibleRunCount: 2,
        worstRunDrawdown: -0.18,
        openAlertCount: 1,
        riskBlockedRunCount: 1,
        noTradeRunCount: 1,
        dataInsufficientRunCount: 1,
        noOrderRunCount: 1,
        orderNoFillRunCount: 0,
        filledRunCount: 2,
    },
    strategyGroups: [
        {
            key: 'strategy-portfolio-a',
            runCount: 2,
            currentEquity: 126000,
            totalPnl: 26000,
            totalReturn: 0.26,
            worstDrawdown: -0.06,
            riskBlockedCount: 0,
            openAlertCount: 0,
            lastRunTime: '2026-06-24T02:00:00Z',
        },
        {
            key: 'strategy-portfolio-risk',
            runCount: 1,
            currentEquity: 90000,
            totalPnl: -10000,
            totalReturn: -0.1,
            worstDrawdown: -0.18,
            riskBlockedCount: 1,
            openAlertCount: 1,
            lastRunTime: '2026-06-23T02:00:00Z',
        },
    ],
    publishGroups: [
        {
            key: 'publish-portfolio-a',
            runCount: 2,
            currentEquity: 126000,
            totalPnl: 26000,
            totalReturn: 0.26,
            worstDrawdown: -0.06,
            riskBlockedCount: 0,
            openAlertCount: 0,
            lastRunTime: '2026-06-24T02:00:00Z',
        },
        {
            key: 'publish-portfolio-risk',
            runCount: 1,
            currentEquity: 90000,
            totalPnl: -10000,
            totalReturn: -0.1,
            worstDrawdown: -0.18,
            riskBlockedCount: 1,
            openAlertCount: 1,
            lastRunTime: '2026-06-23T02:00:00Z',
        },
    ],
    highlights: {
        topWinner: {
            paperRunId: 'portfolio-run-winner',
            status: 'STOPPED',
            symbol: 'BTC-USDT',
            strategyVersionId: 'strategy-portfolio-a',
            publishId: 'publish-portfolio-a',
            currentEquity: 126000,
            initialEquity: 100000,
            totalPnl: 26000,
            totalReturn: 0.26,
            maxDrawdown: -0.06,
            riskBlocked: false,
            openAlertCount: 0,
            orderCount: 4,
            tradeCount: 4,
            noOrder: false,
            orderNoFill: false,
            hasFill: true,
            lastActivityAt: '2026-06-24T02:00:00Z',
        },
        worstDrawdown: {
            paperRunId: 'portfolio-run-risk',
            status: 'STOPPED',
            symbol: 'ETH-USDT',
            strategyVersionId: 'strategy-portfolio-risk',
            publishId: 'publish-portfolio-risk',
            currentEquity: 90000,
            initialEquity: 100000,
            totalPnl: -10000,
            totalReturn: -0.1,
            maxDrawdown: -0.18,
            riskBlocked: true,
            openAlertCount: 1,
            orderCount: 2,
            tradeCount: 1,
            noOrder: false,
            orderNoFill: false,
            hasFill: true,
            lastActivityAt: '2026-06-23T02:00:00Z',
        },
        highestRisk: {
            paperRunId: 'portfolio-run-risk',
            status: 'STOPPED',
            symbol: 'ETH-USDT',
            strategyVersionId: 'strategy-portfolio-risk',
            publishId: 'publish-portfolio-risk',
            currentEquity: 90000,
            initialEquity: 100000,
            totalPnl: -10000,
            totalReturn: -0.1,
            maxDrawdown: -0.18,
            riskBlocked: true,
            openAlertCount: 1,
            orderCount: 2,
            tradeCount: 1,
            noOrder: false,
            orderNoFill: false,
            hasFill: true,
            lastActivityAt: '2026-06-23T02:00:00Z',
        },
        mostRecent: {
            paperRunId: 'portfolio-run-winner',
            status: 'STOPPED',
            symbol: 'BTC-USDT',
            strategyVersionId: 'strategy-portfolio-a',
            publishId: 'publish-portfolio-a',
            currentEquity: 126000,
            initialEquity: 100000,
            totalPnl: 26000,
            totalReturn: 0.26,
            maxDrawdown: -0.06,
            riskBlocked: false,
            openAlertCount: 0,
            orderCount: 4,
            tradeCount: 4,
            noOrder: false,
            orderNoFill: false,
            hasFill: true,
            lastActivityAt: '2026-06-24T02:00:00Z',
        },
        noTradeRuns: [{
            paperRunId: 'portfolio-run-empty',
            status: 'CREATED',
            symbol: 'SOL-USDT',
            strategyVersionId: 'strategy-portfolio-a',
            publishId: 'publish-portfolio-a',
            currentEquity: null,
            initialEquity: null,
            totalPnl: null,
            totalReturn: null,
            maxDrawdown: null,
            riskBlocked: false,
            openAlertCount: 0,
            orderCount: 0,
            tradeCount: 0,
            noOrder: true,
            orderNoFill: false,
            hasFill: false,
            lastActivityAt: '2026-06-22T02:00:00Z',
        }],
        riskBlockedRuns: [{
            paperRunId: 'portfolio-run-risk',
            status: 'STOPPED',
            symbol: 'ETH-USDT',
            strategyVersionId: 'strategy-portfolio-risk',
            publishId: 'publish-portfolio-risk',
            currentEquity: 90000,
            initialEquity: 100000,
            totalPnl: -10000,
            totalReturn: -0.1,
            maxDrawdown: -0.18,
            riskBlocked: true,
            openAlertCount: 1,
            orderCount: 2,
            tradeCount: 1,
            noOrder: false,
            orderNoFill: false,
            hasFill: true,
            lastActivityAt: '2026-06-23T02:00:00Z',
        }],
    },
    dataQuality: {
        missingEquityRuns: [],
        dataInsufficientRuns: [],
        missingBacktestSourceRuns: [],
        missingPublishSourceRuns: [],
    },
    safety: {
        environment: 'SIM/PAPER',
        liveEnabled: false,
        realExchangeTouched: false,
        message: '该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现',
    },
    portfolioCurve: {
        points: [
            {
                timestamp: '2026-06-22T00:00:00Z',
                totalEquity: 200000,
                totalInitialEquity: 200000,
                totalPnl: 0,
                totalReturn: 0,
                peakEquity: 200000,
                drawdown: 0,
                sourceRunCount: 2,
                missingRunCount: 1,
            },
            {
                timestamp: '2026-06-23T00:00:00Z',
                totalEquity: 208000,
                totalInitialEquity: 200000,
                totalPnl: 8000,
                totalReturn: 0.04,
                peakEquity: 208000,
                drawdown: 0,
                sourceRunCount: 2,
                missingRunCount: 1,
            },
            {
                timestamp: '2026-06-24T00:00:00Z',
                totalEquity: 216000,
                totalInitialEquity: 200000,
                totalPnl: 16000,
                totalReturn: 0.08,
                peakEquity: 216000,
                drawdown: -0.02,
                sourceRunCount: 3,
                missingRunCount: 0,
            },
        ],
        latestEquity: 216000,
        peakEquity: 216000,
        currentDrawdown: -0.02,
        maxDrawdown: -0.08,
        pointCount: 3,
        coverage: {
            comparableRunCount: 3,
            missingEquityRunCount: 0,
            incompletePointCount: 0,
        },
    },
};

async function seedAuthAndPortfolioStubs(page: Page): Promise<{summaryRequests: () => number}> {
    let portfolioSummaryRequestCount = 0;

    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'paper-portfolio-route-stub-session',
            tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z',
            username: 'e2e-operator',
            roles: ['ADMIN'],
        }));
    });

    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) => route.fulfill({status: 200, json: []}));

    await page.route('**/api/auth/me', (route: Route) => route.fulfill({
        status: 200,
        json: {
            userId: 1,
            username: 'e2e-operator',
            roles: ['ADMIN'],
            authenticated: true,
            defaultExchangeAccountId: null,
            defaultExchangeCode: null,
            defaultTradeEnv: null,
            defaultAccountAlias: null,
        },
    }));

    await page.route('**/api/paper-trading/portfolio/summary', (route: Route) => {
        portfolioSummaryRequestCount += 1;
        return route.fulfill({status: 200, json: portfolioSummary});
    });

    return {summaryRequests: () => portfolioSummaryRequestCount};
}

test.describe('paper trading portfolio child route', () => {
    test('K5-C1：/paper-trading/portfolio 渲染组合看板、曲线与分组摘要且 summary 单请求', async ({page}) => {
        const stubs = await seedAuthAndPortfolioStubs(page);

        await page.goto('/paper-trading/portfolio');

        await expect(page.getByRole('heading', {name: 'Paper Trading'})).toBeVisible();
        const portfolioDashboard = page.getByRole('region', {name: 'Paper 组合看板'});
        const riskDashboard = page.getByRole('region', {name: 'Paper 风险与回撤驾驶舱'});
        const rankingDashboard = page.getByRole('region', {name: 'Paper 策略表现排行'});
        await expect(portfolioDashboard).toBeVisible();
        await expect(riskDashboard).toBeVisible();
        await expect(rankingDashboard).toBeVisible();
        await expect(page.getByText('Paper run 总数')).toBeVisible();
        await expect(page.getByText('Strategy Version 收益排行')).toBeVisible();
        await expect(page.getByText('Publish 收益排行')).toBeVisible();
        await expect(portfolioDashboard.getByText('strategy-portfolio-a').first()).toBeVisible();
        await expect(portfolioDashboard.getByText('publish-portfolio-risk').first()).toBeVisible();
        await expect(riskDashboard.getByText('组合资金曲线与回撤', {exact: true}).first()).toBeVisible();
        await expect(riskDashboard.getByText('组合资金曲线', {exact: true}).first()).toBeVisible();
        await expect(riskDashboard.getByText('组合回撤曲线', {exact: true}).first()).toBeVisible();
        await expect(page.getByText('SIM/Paper only').first()).toBeVisible();
        await expect(page.getByText('LIVE 未开启').first()).toBeVisible();
        await expect(page.getByText('不接真实交易所').first()).toBeVisible();
        await expect(page.getByText('不构成投资建议').first()).toBeVisible();
        expect(stubs.summaryRequests()).toBe(1);
    });
});
