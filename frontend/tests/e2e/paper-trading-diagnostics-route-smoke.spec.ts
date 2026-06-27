import {expect, test, type Page, type Route} from 'playwright/test';

const diagnosticsResponse = {
    overview: {
        totalRuns: 3,
        noOrderRunCount: 1,
        orderNoFillRunCount: 0,
        filledRunCount: 2,
        filledLossRunCount: 0,
        riskBlockedRunCount: 1,
        dataInsufficientRunCount: 0,
        highDrawdownRunCount: 0,
        failedRunCount: 0,
        runningRunCount: 1,
    },
    causeDistribution: [
        {
            cause: 'RISK_BLOCKED',
            count: 1,
            severity: 'CRITICAL',
            confidence: 'HIGH',
            description: '风控拦截 run：执行被风控阻断或判为高风险。',
        },
        {
            cause: 'NO_ORDER',
            count: 1,
            severity: 'WARNING',
            confidence: 'HIGH',
            description: '无订单 run：未产生任何订单与成交。',
        },
        {
            cause: 'HEALTHY',
            count: 1,
            severity: 'INFO',
            confidence: 'HIGH',
            description: '健康 run：已成交、无风控拦截、数据充分。',
        },
    ],
    runDiagnostics: [
        {
            paperRunId: 'diag-route-risk',
            strategyVersionId: 'sv-route-risk',
            publishId: 'pub-route-risk',
            status: 'RUNNING',
            orderCount: 1,
            tradeCount: 0,
            currentEquity: 100000,
            initialEquity: 100000,
            totalPnl: 0,
            totalReturn: 0,
            maxDrawdown: 0,
            riskBlocked: true,
            openAlertCount: 1,
            primaryCause: 'RISK_BLOCKED',
            secondaryCauses: [],
            severity: 'CRITICAL',
            causeConfidence: 'HIGH',
            explanation: '风控规则阻断执行或判定为高风险结果。',
            suggestedAction: '检查最近一次风控检查结果与触发规则，确认是否为预期风控行为。',
            lastRunTime: '2026-06-24T02:00:00Z',
        },
        {
            paperRunId: 'diag-route-no-order',
            strategyVersionId: 'sv-route-flat',
            publishId: 'pub-route-flat',
            status: 'RUNNING',
            orderCount: 0,
            tradeCount: 0,
            currentEquity: 100000,
            initialEquity: 100000,
            totalPnl: 0,
            totalReturn: 0,
            maxDrawdown: 0,
            riskBlocked: false,
            openAlertCount: 0,
            primaryCause: 'NO_ORDER',
            secondaryCauses: [],
            severity: 'WARNING',
            causeConfidence: 'HIGH',
            explanation: '已运行但未产生任何订单。',
            suggestedAction: '复盘策略触发条件、参数与输入数据覆盖范围。',
            lastRunTime: '2026-06-24T02:01:00Z',
        },
        {
            paperRunId: 'diag-route-healthy',
            strategyVersionId: 'sv-route-flat',
            publishId: 'pub-route-flat',
            status: 'STOPPED',
            orderCount: 3,
            tradeCount: 3,
            currentEquity: 112000,
            initialEquity: 100000,
            totalPnl: 12000,
            totalReturn: 0.12,
            maxDrawdown: -0.03,
            riskBlocked: false,
            openAlertCount: 0,
            primaryCause: 'HEALTHY',
            secondaryCauses: [],
            severity: 'INFO',
            causeConfidence: 'HIGH',
            explanation: '已成交、当前非亏损、无高回撤、无风控拦截、数据充分。',
            suggestedAction: '继续按 Paper 计划观察；不代表真实交易表现。',
            lastRunTime: '2026-06-24T02:02:00Z',
        },
    ],
    strategyDiagnostics: [
        {
            key: 'sv-route-risk',
            runCount: 1,
            primaryCause: 'RISK_BLOCKED',
            topCauses: ['RISK_BLOCKED'],
            noOrderCount: 0,
            orderNoFillCount: 0,
            filledLossCount: 0,
            riskBlockedCount: 1,
            dataInsufficientCount: 0,
            highDrawdownCount: 0,
            severity: 'CRITICAL',
            causeConfidence: 'HIGH',
        },
        {
            key: 'sv-route-flat',
            runCount: 2,
            primaryCause: 'NO_ORDER',
            topCauses: ['NO_ORDER', 'HEALTHY'],
            noOrderCount: 1,
            orderNoFillCount: 0,
            filledLossCount: 0,
            riskBlockedCount: 0,
            dataInsufficientCount: 0,
            highDrawdownCount: 0,
            severity: 'WARNING',
            causeConfidence: 'HIGH',
        },
    ],
    publishDiagnostics: [
        {
            key: 'pub-route-risk',
            runCount: 1,
            primaryCause: 'RISK_BLOCKED',
            topCauses: ['RISK_BLOCKED'],
            noOrderCount: 0,
            orderNoFillCount: 0,
            filledLossCount: 0,
            riskBlockedCount: 1,
            dataInsufficientCount: 0,
            highDrawdownCount: 0,
            severity: 'CRITICAL',
            causeConfidence: 'HIGH',
        },
        {
            key: 'pub-route-flat',
            runCount: 2,
            primaryCause: 'NO_ORDER',
            topCauses: ['NO_ORDER', 'HEALTHY'],
            noOrderCount: 1,
            orderNoFillCount: 0,
            filledLossCount: 0,
            riskBlockedCount: 0,
            dataInsufficientCount: 0,
            highDrawdownCount: 0,
            severity: 'WARNING',
            causeConfidence: 'HIGH',
        },
    ],
    safety: {
        environment: 'SIM/PAPER',
        liveEnabled: false,
        realExchangeTouched: false,
        message: '该执行诊断仅基于 Paper 模拟运行与本地执行事实做规则化归因，不构成真实投资建议，不代表 LIVE 或真实交易表现',
    },
};

const emptyPortfolioSummary = {
    overview: {
        totalRuns: 0,
        runningCount: 0,
        stoppedCount: 0,
        failedCount: 0,
        cancelledCount: 0,
        createdCount: 0,
        totalInitialEquity: 0,
        totalCurrentEquity: 0,
        totalPnl: 0,
        totalReturn: null,
        returnEligibleRunCount: 0,
        worstRunDrawdown: null,
        openAlertCount: 0,
        riskBlockedRunCount: 0,
        noTradeRunCount: 0,
        dataInsufficientRunCount: 0,
        noOrderRunCount: 0,
        orderNoFillRunCount: 0,
        filledRunCount: 0,
    },
};

async function seedAuthAndDiagnosticsStubs(page: Page): Promise<{
    diagnosticsRequests: () => number;
    portfolioSummaryRequests: () => number;
}> {
    let diagnosticsRequestCount = 0;
    let portfolioSummaryRequestCount = 0;

    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'paper-diagnostics-route-stub-session',
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

    await page.route('**/api/paper-trading/execution-diagnostics', (route: Route) => {
        diagnosticsRequestCount += 1;
        return route.fulfill({status: 200, json: diagnosticsResponse});
    });

    await page.route('**/api/paper-trading/portfolio/summary', (route: Route) => {
        portfolioSummaryRequestCount += 1;
        return route.fulfill({status: 200, json: emptyPortfolioSummary});
    });

    return {
        diagnosticsRequests: () => diagnosticsRequestCount,
        portfolioSummaryRequests: () => portfolioSummaryRequestCount,
    };
}

test.describe('paper trading diagnostics child route', () => {
    test('K5-C2：/paper-trading/diagnostics 渲染真实执行诊断且 query 边界独立', async ({page}) => {
        const stubs = await seedAuthAndDiagnosticsStubs(page);

        await page.goto('/paper-trading/diagnostics');

        await expect(page.getByRole('heading', {name: 'Paper Trading'})).toBeVisible();
        await expect(page.locator('[aria-label="Paper Trading 子路由导航"]')).toBeVisible();
        await expect(page.getByText('Runs', {exact: true})).toBeVisible();
        await expect(page.getByText('Portfolio', {exact: true})).toBeVisible();
        await expect(page.getByText('Diagnostics', {exact: true})).toBeVisible();
        await expect(page.getByText('Reviews', {exact: true})).toBeVisible();

        const diagnostics = page.getByRole('region', {name: 'Paper 执行诊断', exact: true});
        await expect(diagnostics).toBeVisible();
        await expect(diagnostics.getByText('Paper 执行诊断', {exact: true})).toBeVisible();
        await expect(diagnostics.getByText('SIM/Paper only · Rules-based diagnostics')).toBeVisible();
        await expect(diagnostics.getByText('基于 Paper 执行事实的规则化归因，不代表 LIVE 或真实交易建议。')).toBeVisible();

        await expect(diagnostics.locator('.nq-metric-card', {hasText: '纳入诊断 run'}).locator('.nq-metric-card__value')).toHaveText('3');
        await expect(page.getByRole('region', {name: 'Paper 执行诊断主因分布表'})).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 执行诊断 Run 表'})).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 执行诊断 Strategy 表'})).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 执行诊断 Publish 表'})).toBeVisible();
        await expect(page.getByText('diag-route-risk')).toBeVisible();
        await expect(page.getByText('sv-route-risk')).toBeVisible();
        await expect(page.getByText('pub-route-risk')).toBeVisible();

        await expect(diagnostics.getByText(/该诊断仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现/)).toBeVisible();
        await expect(diagnostics.getByText(/诊断结果为规则化归因，不是 AI 投资建议，也不构成真实交易建议/)).toBeVisible();
        await expect(diagnostics.getByText(/confidence 表示该诊断原因由事实直接判断或推断得到，不代表真实交易结论/)).toBeVisible();

        const filterGroup = diagnostics.getByRole('group', {name: 'Paper 执行诊断筛选'});
        const runTable = page.getByRole('region', {name: 'Paper 执行诊断 Run 表'});

        await filterGroup.locator('.ant-select-selector').first().click();
        await page.getByRole('option', {name: '风控拦截 RISK_BLOCKED', exact: true}).click();
        await expect(runTable.locator('tbody tr[data-row-key="diag-route-risk"]')).toHaveCount(1);
        await expect(runTable.locator('tbody tr[data-row-key="diag-route-no-order"]')).toHaveCount(0);

        await diagnostics.getByRole('button', {name: '查看全部'}).click();
        await filterGroup.locator('.ant-select-selector').last().click();
        await page.getByRole('option', {name: 'CRITICAL', exact: true}).click();
        await expect(runTable.locator('tbody tr[data-row-key="diag-route-risk"]')).toHaveCount(1);
        await expect(runTable.locator('tbody tr[data-row-key="diag-route-healthy"]')).toHaveCount(0);

        expect(stubs.diagnosticsRequests()).toBe(1);
        expect(stubs.portfolioSummaryRequests()).toBe(0);
    });

    test('K5-C2：/paper-trading/portfolio 不挂载 diagnostics query', async ({page}) => {
        const stubs = await seedAuthAndDiagnosticsStubs(page);

        await page.goto('/paper-trading/portfolio');

        await expect(page.getByRole('heading', {name: 'Paper Trading'})).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 组合看板'})).toBeVisible();
        await expect(page.getByText('暂无 Paper run，创建并运行后自动汇总组合表现。')).toBeVisible();
        expect(stubs.portfolioSummaryRequests()).toBe(1);
        expect(stubs.diagnosticsRequests()).toBe(0);
    });
});
