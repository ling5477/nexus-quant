import {expect, test, type Page, type Route} from 'playwright/test';

const PAPER_RUN_ID = 'paper-loop-1';

const paperRun = {
    paperRunId: PAPER_RUN_ID,
    publishId: 'publish-loop-created',
    strategyVersionId: 'strategy-version-loop-1',
    status: 'CREATED',
    tradeEnv: 'SIM',
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    intervalCode: '1m',
    startedAt: null,
    stoppedAt: null,
    publishSnapshotJson: '{"source":"e2e"}',
    strategyVersionSnapshotJson: '{"version":"loop"}',
    datasetSnapshotJson: '{"dataset":"fixture"}',
    paramSnapshotJson: '{"orderQuantity":"1"}',
    configSnapshotJson: '{"mode":"paper"}',
    createdBy: 'e2e-operator',
    createdAt: '2026-06-24T00:59:00Z',
    updatedAt: '2026-06-24T01:03:00Z',
};

const defaultOrders = [{
    paperOrderId: 'paper-order-1',
    paperRunId: PAPER_RUN_ID,
    symbol: 'BTC-USDT',
    side: 'BUY',
    orderType: 'MARKET',
    quantity: '1',
    price: '65000',
    status: 'FILLED',
    reason: null,
    rawSignalJson: '{}',
    createdAt: '2026-06-24T01:01:00Z',
    updatedAt: '2026-06-24T01:01:30Z',
}];

const defaultTrades = [{
    paperTradeId: 'paper-trade-1',
    paperOrderId: 'paper-order-1',
    paperRunId: PAPER_RUN_ID,
    symbol: 'BTC-USDT',
    side: 'BUY',
    quantity: '1',
    price: '65000',
    fee: '65',
    tradedAt: '2026-06-24T01:01:31Z',
    createdAt: '2026-06-24T01:01:31Z',
}];

const defaultPositions = [{
    paperPositionId: 'paper-position-1',
    paperRunId: PAPER_RUN_ID,
    symbol: 'BTC-USDT',
    quantity: '1',
    avgPrice: '65000',
    unrealizedPnl: '125',
    realizedPnl: '-65',
    updatedAt: '2026-06-24T01:02:00Z',
    createdAt: '2026-06-24T01:01:31Z',
}];

const defaultRiskResults = [{
    riskResultId: 'risk-loop-1',
    paperRunId: PAPER_RUN_ID,
    checkType: 'BASIC_HEALTH_CHECK',
    status: 'PASSED',
    severity: 'LOW',
    message: 'paper loop ok',
    inputSnapshotJson: '{}',
    resultSnapshotJson: '{}',
    createdAt: '2026-06-24T01:03:00Z',
}];

const emptyPortfolioCurve = {
    points: [],
    pointCount: 0,
    latestEquity: null,
    peakEquity: null,
    maxDrawdown: null,
    currentDrawdown: null,
    coverage: {comparableRunCount: 0, missingEquityRunCount: 0, incompletePointCount: 0},
};

const emptyPortfolioSummary = {
    overview: {
        totalRuns: 0,
        runningCount: 0,
        stoppedCount: 0,
        failedCount: 0,
        cancelledCount: 0,
        createdCount: 0,
        totalInitialEquity: null,
        totalCurrentEquity: null,
        totalPnl: null,
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
    strategyGroups: [],
    publishGroups: [],
    highlights: {topWinner: null, worstDrawdown: null, highestRisk: null, mostRecent: null, noTradeRuns: [], riskBlockedRuns: []},
    dataQuality: {missingEquityRuns: [], dataInsufficientRuns: [], missingBacktestSourceRuns: [], missingPublishSourceRuns: []},
    safety: {environment: 'SIM/PAPER', liveEnabled: false, realExchangeTouched: false, message: 'Paper only'},
    portfolioCurve: emptyPortfolioCurve,
};

const emptyExecutionDiagnostics = {
    overview: {
        totalRuns: 0,
        noOrderRunCount: 0,
        orderNoFillRunCount: 0,
        filledRunCount: 0,
        filledLossRunCount: 0,
        riskBlockedRunCount: 0,
        dataInsufficientRunCount: 0,
        highDrawdownRunCount: 0,
        failedRunCount: 0,
        runningRunCount: 0,
    },
    causeDistribution: [],
    runDiagnostics: [],
    strategyDiagnostics: [],
    publishDiagnostics: [],
    safety: {environment: 'SIM/PAPER', liveEnabled: false, realExchangeTouched: false, message: 'Paper only'},
};

const emptyStrategyEvaluations = {
    overview: {
        strategyCount: 0,
        publishCount: 0,
        evaluatedRunCount: 0,
        comparableRunCount: 0,
        sampleInsufficientStrategyCount: 0,
        profitableStrategyCount: 0,
        lossStrategyCount: 0,
        highRiskStrategyCount: 0,
        backtestDeviationStrategyCount: 0,
        topCompositeScore: null,
        worstCompositeScore: null,
    },
    strategyEvaluations: [],
    publishEvaluations: [],
    rankings: {
        topCompositeStrategies: [],
        worstCompositeStrategies: [],
        topReturnStrategies: [],
        worstDrawdownStrategies: [],
        sampleInsufficientStrategies: [],
        highDeviationStrategies: [],
        highRiskStrategies: [],
    },
    safety: {environment: 'SIM/PAPER', liveEnabled: false, realExchangeTouched: false, message: 'Paper only'},
};

const emptyAutoReviews = {
    overview: {
        totalRuns: 0,
        reviewedRunCount: 0,
        issueRunCount: 0,
        healthyRunCount: 0,
        criticalIssueCount: 0,
        warningIssueCount: 0,
        strategyReviewedCount: 0,
        publishReviewedCount: 0,
        topIssueCause: null,
        topWeakness: null,
        generatedAt: '2026-06-24T03:00:00Z',
    },
    portfolioReview: {
        headline: '暂无足够 Paper 事实生成复盘。',
        summary: '当前没有可用的 bounded Paper run 与策略评估事实，无法生成自动复盘。',
        keyFindings: [],
        riskHighlights: [],
        executionHighlights: [],
        strategyHighlights: [],
        backtestDeviationHighlights: [],
        suggestedNextActions: [],
        limitations: ['结论基于 SIM/Paper 模拟执行事实，不代表 LIVE 或真实交易表现。'],
    },
    runReviews: [],
    strategyReviews: [],
    publishReviews: [],
    issueClusters: [],
    safety: {paperOnly: true, rulesBased: true, noInvestmentAdvice: true, noLiveTrading: true, noAiRuntime: true, message: 'Paper only'},
};

interface PaperLoopCounters {
    portfolio: number;
    diagnostics: number;
    legacyEvaluations: number;
    strategyEvaluations: number;
    autoReviews: number;
}

interface PaperLoopHarness {
    counters: PaperLoopCounters;
    setRunStatus: (status: string) => void;
}

/**
 * seedAuthAndPaperLoopStubs 固定本 spec 的浏览器会话和 API 响应。
 *
 * Why:
 * K5-C4 要验证 runs 不再读取迁出的 dashboard query。本 harness 同时为迁出 query 设置计数器：
 * runs 页面访问期间计数必须保持 0；只有用户点击跳转进入独立页面后，对应 query 才允许触发。
 */
async function seedAuthAndPaperLoopStubs(page: Page, seedRun = true): Promise<PaperLoopHarness> {
    let currentRun = {...paperRun};
    let runs: typeof paperRun[] = seedRun ? [currentRun] : [];
    const counters: PaperLoopCounters = {
        portfolio: 0,
        diagnostics: 0,
        legacyEvaluations: 0,
        strategyEvaluations: 0,
        autoReviews: 0,
    };

    const setRunStatus = (status: string) => {
        currentRun = {
            ...currentRun,
            status,
            startedAt: status === 'RUNNING' ? '2026-06-24T01:04:00Z' : currentRun.startedAt,
            stoppedAt: status === 'STOPPED' ? '2026-06-24T01:05:00Z' : null,
            updatedAt: `2026-06-24T01:${status === 'RUNNING' ? '04' : '05'}:00Z`,
        };
        runs = [currentRun];
    };

    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'paper-loop-stub-session',
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

    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/portfolio\/summary$/, (route: Route) => {
        counters.portfolio += 1;
        return route.fulfill({status: 200, json: emptyPortfolioSummary});
    });
    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/execution-diagnostics$/, (route: Route) => {
        counters.diagnostics += 1;
        return route.fulfill({status: 200, json: emptyExecutionDiagnostics});
    });
    await page.route(/^https?:\/\/[^/]+\/api\/evaluations(?:\?.*)?$/, (route: Route) => {
        counters.legacyEvaluations += 1;
        return route.fulfill({status: 200, json: []});
    });
    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/strategy-evaluations$/, (route: Route) => {
        counters.strategyEvaluations += 1;
        return route.fulfill({status: 200, json: emptyStrategyEvaluations});
    });
    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/auto-reviews$/, (route: Route) => {
        counters.autoReviews += 1;
        return route.fulfill({status: 200, json: emptyAutoReviews});
    });

    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/runs(?:\?.*)?$/, async (route: Route) => {
        if (route.request().method() === 'POST') {
            const payload = route.request().postDataJSON() as {publishId?: string};
            currentRun = {...paperRun, publishId: payload.publishId ?? paperRun.publishId, status: 'CREATED'};
            runs = [currentRun];
            return route.fulfill({status: 200, json: currentRun});
        }
        return route.fulfill({status: 200, json: runs});
    });

    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}`, (route: Route) => route.fulfill({
        status: 200,
        json: currentRun,
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/summary`, (route: Route) => route.fulfill({
        status: 200,
        json: {
            run: currentRun,
            counts: {orderCount: 1, tradeCount: 1, fillCount: 1, positionCount: 1, openAlertCount: 0, recoveryEventCount: 0},
            latest: {order: defaultOrders[0], trade: defaultTrades[0], position: defaultPositions[0], equitySnapshot: null, dailyReport: null, riskResult: defaultRiskResults[0], alert: null, recoveryEvent: null},
            resultReview: {finalStatus: currentRun.status, runtimeDurationText: '-', netPnl: '60', riskResult: '通过', conclusion: '正常完成', conclusionLevel: 'info'},
            diagnoses: [],
            timeline: [],
            safety: {environment: 'SIM/PAPER', liveEnabled: false, realExchangeTouched: false, message: 'SIM/Paper only'},
        },
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/start`, (route: Route) => {
        setRunStatus('RUNNING');
        return route.fulfill({status: 200, json: currentRun});
    });
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/stop`, (route: Route) => {
        setRunStatus('STOPPED');
        return route.fulfill({status: 200, json: currentRun});
    });
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/orders`, (route: Route) => route.fulfill({status: 200, json: defaultOrders}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/trades`, (route: Route) => route.fulfill({status: 200, json: defaultTrades}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/positions`, (route: Route) => route.fulfill({status: 200, json: defaultPositions}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/risk-results`, (route: Route) => route.fulfill({status: 200, json: defaultRiskResults}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/equity-curve`, (route: Route) => route.fulfill({status: 200, json: []}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/position-curve`, (route: Route) => route.fulfill({status: 200, json: []}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/replay`, (route: Route) => route.fulfill({status: 200, json: []}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/emergency-stops`, (route: Route) => route.fulfill({status: 200, json: []}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/alerts**`, (route: Route) => route.fulfill({status: 200, json: []}));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/recovery-events**`, (route: Route) => route.fulfill({status: 200, json: []}));

    return {counters, setRunStatus};
}

async function expectRunsIsExecutionOnly(page: Page): Promise<void> {
    await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();
    await expect(page.getByRole('region', {name: 'Paper 组合看板'})).toHaveCount(0);
    await expect(page.getByRole('region', {name: 'Paper 执行诊断'})).toHaveCount(0);
    await expect(page.getByRole('region', {name: 'Paper 策略评估', exact: true})).toHaveCount(0);
    await expect(page.getByRole('region', {name: 'Paper 自动复盘', exact: true})).toHaveCount(0);
    await expect(page.getByRole('region', {name: 'Paper 策略表现排行'})).toHaveCount(0);
    await expect(page.getByText('运行结果复盘')).toHaveCount(0);
    await expect(page.getByText('Backtest → Paper 结果对照')).toHaveCount(0);
    await expect(page.getByText('Strategy → Publish → Paper 链路')).toHaveCount(0);
}

test.describe('paper trading runs slimmer', () => {
    test('K5-C4：/paper-trading 重定向到 runs，runs 不挂载分析 dashboard 或迁出 query', async ({page}) => {
        const harness = await seedAuthAndPaperLoopStubs(page, true);

        await page.goto('/paper-trading');

        await expect(page).toHaveURL(/\/paper-trading\/runs$/);
        await expect(page.getByRole('heading', {name: 'Paper Trading'})).toBeVisible();
        await expect(page.getByLabel('Paper Trading 子路由导航')).toBeVisible();
        await expect(page.getByText('SIM/Paper only').first()).toBeVisible();
        await expect(page.getByText('LIVE 未开启').first()).toBeVisible();
        await expectRunsIsExecutionOnly(page);

        expect(harness.counters.portfolio).toBe(0);
        expect(harness.counters.diagnostics).toBe(0);
        expect(harness.counters.legacyEvaluations).toBe(0);
        expect(harness.counters.strategyEvaluations).toBe(0);
        expect(harness.counters.autoReviews).toBe(0);
    });

    test('K5-C4：runs 分析入口只做跳转到独立子路由', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, true);

        await page.goto('/paper-trading/runs');
        await expectRunsIsExecutionOnly(page);

        await page.getByRole('button', {name: '查看组合分析'}).click();
        await expect(page).toHaveURL(/\/paper-trading\/portfolio$/);
        await expect(page.getByRole('region', {name: 'Paper 组合看板'})).toBeVisible();

        await page.goto('/paper-trading/runs');
        await page.getByRole('button', {name: '查看执行诊断'}).click();
        await expect(page).toHaveURL(/\/paper-trading\/diagnostics$/);
        await expect(page.getByRole('region', {name: 'Paper 执行诊断', exact: true})).toBeVisible();

        await page.goto('/paper-trading/runs');
        await page.getByRole('button', {name: '查看策略评估'}).click();
        await expect(page).toHaveURL(/\/paper-trading\/reviews$/);
        await expect(page.getByRole('region', {name: 'Paper 策略评估', exact: true})).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 自动复盘', exact: true})).toBeVisible();
    });

    test('K5-C4：run list、create/start/stop、detail 与 factTab 行为保持', async ({page}) => {
        const harness = await seedAuthAndPaperLoopStubs(page, false);

        await page.goto('/paper-trading/runs');
        await expectRunsIsExecutionOnly(page);
        await page.getByRole('button', {name: /查\s*询/}).click();
        await expect(page.getByText('当前筛选条件下没有 Paper Trading run。')).toBeVisible();

        await page.getByRole('button', {name: /创建\s*Paper\s*Run/i}).click();
        const dialog = page.getByRole('dialog', {name: /创建\s*Paper\s*Trading/});
        await expect(dialog).toBeVisible();
        await dialog.getByPlaceholder('发布记录 ID（publishId）').fill(paperRun.publishId);
        await page.getByRole('button', {name: 'OK', exact: true}).click();

        const detail = page.getByRole('region', {name: 'Paper Trading 详情'});
        await expect(detail.getByText(PAPER_RUN_ID).first()).toBeVisible();
        await expect(detail.getByText('CREATED').first()).toBeVisible();
        await expect(detail.getByText('Paper Run ID')).toBeVisible();
        await expect(detail.getByText('生命周期操作仅作用于当前 SIM/Paper run；LIVE 未开启，不会触发真实交易所。')).toBeVisible();
        await expect(detail.getByText('订单事实').first()).toBeVisible();
        await expect(detail.getByText('成交事实').first()).toBeVisible();
        await expect(detail.getByText('持仓事实').first()).toBeVisible();
        await expect(detail.getByText('净 PnL').first()).toBeVisible();
        await expect(detail.getByText('风控闭环').first()).toBeVisible();

        await detail.getByRole('button', {name: '启动 Paper Run'}).click();
        await expect(detail.getByText('RUNNING').first()).toBeVisible();
        await detail.getByRole('button', {name: '停止 Paper Run'}).click();
        await expect(detail.getByText('STOPPED').first()).toBeVisible();

        await detail.getByRole('tab', {name: '订单'}).click();
        await expect(detail.getByText('paper-order-1')).toBeVisible();
        await detail.getByRole('tab', {name: '成交'}).click();
        await expect(detail.getByText('paper-trade-1')).toBeVisible();
        await detail.getByRole('tab', {name: '持仓', exact: true}).click();
        await expect(detail.getByRole('columnheader', {name: '未实现盈亏'})).toBeVisible();
        await expect(detail.getByText('BTC-USDT').first()).toBeVisible();
        await detail.getByRole('tab', {name: '快照'}).click();
        await expect(detail.getByText('Publish Snapshot')).toBeVisible();
        await expect(detail.getByText('Strategy Version Snapshot')).toBeVisible();

        expect(harness.counters.portfolio).toBe(0);
        expect(harness.counters.diagnostics).toBe(0);
        expect(harness.counters.legacyEvaluations).toBe(0);
        expect(harness.counters.strategyEvaluations).toBe(0);
        expect(harness.counters.autoReviews).toBe(0);
    });

    test('K5-C4：已有 run 可查询、选择并保持状态按钮禁用规则', async ({page}) => {
        const harness = await seedAuthAndPaperLoopStubs(page, true);

        await page.goto('/paper-trading/runs');
        await page.getByRole('button', {name: /查\s*询/}).click();
        const row = page.locator(`tr[data-row-key="${PAPER_RUN_ID}"]`);
        await expect(row).toBeVisible();
        await row.getByRole('button', {name: '查看详情'}).click();

        const detail = page.getByRole('region', {name: 'Paper Trading 详情'});
        await expect(detail.getByRole('button', {name: '启动 Paper Run'})).toBeEnabled();
        await expect(detail.getByRole('button', {name: '停止 Paper Run'})).toBeDisabled();

        harness.setRunStatus('RUNNING');
        await page.goto('/paper-trading/runs');
        await page.getByRole('button', {name: /查\s*询/}).click();
        await expect(row.getByText('RUNNING')).toBeVisible();
        await row.getByRole('button', {name: '查看详情'}).click();
        await expect(detail.getByRole('button', {name: '启动 Paper Run'})).toBeDisabled();
        await expect(detail.getByRole('button', {name: '停止 Paper Run'})).toBeEnabled();
    });
});
