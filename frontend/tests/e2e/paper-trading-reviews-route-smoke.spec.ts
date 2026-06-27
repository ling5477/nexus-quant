import {expect, test, type Page, type Route} from 'playwright/test';

const reviewsStrategyEvaluations = {
    overview: {
        strategyCount: 1,
        publishCount: 1,
        evaluatedRunCount: 4,
        comparableRunCount: 4,
        sampleInsufficientStrategyCount: 0,
        profitableStrategyCount: 1,
        lossStrategyCount: 0,
        highRiskStrategyCount: 0,
        backtestDeviationStrategyCount: 0,
        topCompositeScore: 88,
        worstCompositeScore: 88,
    },
    strategyEvaluations: [{
        strategyVersionId: 'eval-route-strong',
        runCount: 4,
        comparableRunCount: 4,
        publishCount: 1,
        latestRunTime: '2026-06-24T02:00:00Z',
        currentEquity: 448000,
        initialEquity: 400000,
        totalPnl: 48000,
        totalReturn: 0.12,
        maxDrawdown: -0.03,
        winRunCount: 4,
        lossRunCount: 0,
        winRate: 1,
        averageReturn: 0.12,
        averageDrawdown: -0.03,
        riskBlockedCount: 0,
        dataInsufficientCount: 0,
        noOrderCount: 0,
        orderNoFillCount: 0,
        filledRunCount: 4,
        filledLossCount: 0,
        failedRunCount: 0,
        sampleScore: 40,
        riskScore: 94,
        returnScore: 82,
        executionScore: 100,
        backtestDeviationScore: 96,
        compositeScore: 88,
        ratingLabel: 'STRONG_PAPER_PERFORMER',
        evaluationConfidence: 'HIGH',
        primaryWeakness: 'SAMPLE',
        warnings: [],
        backtestDeviation: {
            backtestReturn: 0.1,
            paperReturn: 0.12,
            returnDeviation: 0.02,
            backtestMaxDrawdown: -0.03,
            paperMaxDrawdown: -0.03,
            drawdownDeviation: 0,
            deviationLevel: 'LOW',
            deviationExplanation: 'Paper 与 Backtest 收益偏差较小。',
        },
    }],
    publishEvaluations: [{
        publishId: 'pub-eval-route-1',
        strategyVersionId: 'eval-route-strong',
        runCount: 4,
        comparableRunCount: 4,
        latestRunTime: '2026-06-24T02:00:00Z',
        totalPnl: 48000,
        totalReturn: 0.12,
        maxDrawdown: -0.03,
        winRate: 1,
        sampleScore: 40,
        riskScore: 94,
        returnScore: 82,
        executionScore: 100,
        backtestDeviationScore: 96,
        compositeScore: 88,
        ratingLabel: 'STRONG_PAPER_PERFORMER',
        evaluationConfidence: 'HIGH',
        warnings: [],
        backtestDeviation: {
            backtestReturn: 0.1,
            paperReturn: 0.12,
            returnDeviation: 0.02,
            backtestMaxDrawdown: -0.03,
            paperMaxDrawdown: -0.03,
            drawdownDeviation: 0,
            deviationLevel: 'LOW',
            deviationExplanation: 'Paper 与 Backtest 收益偏差较小。',
        },
    }],
    rankings: {
        topCompositeStrategies: ['eval-route-strong'],
        worstCompositeStrategies: [],
        topReturnStrategies: ['eval-route-strong'],
        worstDrawdownStrategies: [],
        sampleInsufficientStrategies: [],
        highDeviationStrategies: [],
        highRiskStrategies: [],
    },
    safety: {
        environment: 'SIM/PAPER',
        liveEnabled: false,
        realExchangeTouched: false,
        message: '该策略评估仅基于 Paper 模拟运行与本地执行事实做 Paper 内部启发式评分，不是真实投资评级，不构成投资建议，不代表 LIVE 或真实交易表现',
    },
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
    safety: reviewsStrategyEvaluations.safety,
};

const reviewsAutoReviews = {
    overview: {
        totalRuns: 4,
        reviewedRunCount: 4,
        issueRunCount: 1,
        healthyRunCount: 3,
        criticalIssueCount: 1,
        warningIssueCount: 0,
        strategyReviewedCount: 1,
        publishReviewedCount: 1,
        topIssueCause: 'RISK_BLOCKED',
        topWeakness: 'RISK',
        generatedAt: '2026-06-24T03:00:00Z',
    },
    portfolioReview: {
        headline: 'Paper 组合存在 1 个关键复盘问题。',
        summary: '共 4 个 bounded Paper run，其中 1 个存在关键问题。结论为规则化 Paper 复盘，不构成投资建议。',
        keyFindings: ['关键问题 run 1 个，建议优先排查。'],
        riskHighlights: ['RISK_BLOCKED: 1 项。'],
        executionHighlights: [],
        strategyHighlights: ['Paper 内部表现较强策略 1 个（非投资推荐）。'],
        backtestDeviationHighlights: [],
        suggestedNextActions: ['检查风控规则、仓位限制与风险阈值。'],
        limitations: ['结论基于 SIM/Paper 模拟执行事实，不代表 LIVE 或真实交易表现。'],
    },
    runReviews: [{
        paperRunId: 'review-route-risk',
        strategyVersionId: 'eval-route-strong',
        publishId: 'pub-eval-route-1',
        status: 'STOPPED',
        primaryCause: 'RISK_BLOCKED',
        severity: 'CRITICAL',
        confidence: 'HIGH',
        totalPnl: -2000,
        totalReturn: -0.02,
        maxDrawdown: -0.08,
        reviewHeadline: '风控拦截：执行被风控阻断或判为高风险',
        reviewSummary: '风控阻断或高风险结果出现。',
        keyFacts: ['状态=STOPPED', '风控拦截=是'],
        likelyReasons: ['风控规则可能触发'],
        suggestedActions: ['检查风控规则'],
        tags: ['RISK_BLOCKED', 'CRITICAL'],
    }],
    strategyReviews: [{
        strategyVersionId: 'eval-route-strong',
        ratingLabel: 'STRONG_PAPER_PERFORMER',
        compositeScore: 88,
        evaluationConfidence: 'HIGH',
        primaryWeakness: 'SAMPLE',
        reviewHeadline: 'Paper 内部表现较强（非投资推荐）',
        reviewSummary: '该策略版本在 Paper 模拟内部表现较强，但不代表真实交易表现，也不构成投资建议。',
        strengths: ['Paper 收益评分较高'],
        weaknesses: ['主要短板: 样本不足'],
        warnings: [],
        suggestedActions: ['继续积累 Paper 样本以提升评估置信度'],
    }],
    publishReviews: [{
        publishId: 'pub-eval-route-1',
        strategyVersionId: 'eval-route-strong',
        ratingLabel: 'STRONG_PAPER_PERFORMER',
        compositeScore: 88,
        evaluationConfidence: 'HIGH',
        primaryWeakness: 'SAMPLE',
        reviewHeadline: 'Paper 内部表现较强（非投资推荐）',
        reviewSummary: '该发布在 Paper 模拟内部表现较强，不构成投资建议。',
        strengths: ['Paper 收益评分较高'],
        weaknesses: ['主要短板: 样本不足'],
        warnings: [],
        suggestedActions: ['继续积累 Paper 样本以提升评估置信度'],
    }],
    issueClusters: [{
        clusterKey: 'cluster-route-risk',
        cause: 'RISK_BLOCKED',
        severity: 'CRITICAL',
        count: 1,
        affectedRunIds: ['review-route-risk'],
        affectedStrategyVersionIds: ['eval-route-strong'],
        affectedPublishIds: ['pub-eval-route-1'],
        summary: '多个 Paper run 被风控阻断或判为高风险。',
        suggestedAction: '检查风控规则、仓位限制与风险阈值。',
    }],
    safety: {
        paperOnly: true,
        rulesBased: true,
        noInvestmentAdvice: true,
        noLiveTrading: true,
        noAiRuntime: true,
        message: '该自动复盘仅基于 Paper 模拟运行与本地执行事实做规则化摘要，未接 AI / DH runtime，不构成真实投资建议，不代表 LIVE 或真实交易表现',
    },
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
    safety: reviewsAutoReviews.safety,
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
    highlights: {
        topWinner: null,
        worstDrawdown: null,
        highestRisk: null,
        mostRecent: null,
        noTradeRuns: [],
        riskBlockedRuns: [],
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
        points: [],
        latestEquity: null,
        peakEquity: null,
        currentDrawdown: null,
        maxDrawdown: null,
        pointCount: 0,
        coverage: {
            comparableRunCount: 0,
            missingEquityRunCount: 0,
            incompletePointCount: 0,
        },
    },
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
    safety: {
        environment: 'SIM/PAPER',
        liveEnabled: false,
        realExchangeTouched: false,
        message: '该执行诊断仅基于 Paper 模拟运行与本地执行事实做规则化归因，不构成真实投资建议，不代表 LIVE 或真实交易表现',
    },
};

type ReviewsRouteStubOptions = {
    strategyEvaluations?: unknown;
    strategyEvaluationsStatus?: number;
    autoReviews?: unknown;
    autoReviewsStatus?: number;
};

async function seedAuthAndReviewsRouteStubs(page: Page, options: ReviewsRouteStubOptions = {}) {
    let strategyEvaluationRequests = 0;
    let autoReviewRequests = 0;
    let portfolioSummaryRequests = 0;
    let diagnosticsRequests = 0;

    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'paper-reviews-route-stub-session',
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
        portfolioSummaryRequests += 1;
        return route.fulfill({status: 200, json: emptyPortfolioSummary});
    });

    await page.route('**/api/paper-trading/execution-diagnostics', (route: Route) => {
        diagnosticsRequests += 1;
        return route.fulfill({status: 200, json: emptyExecutionDiagnostics});
    });

    await page.route('**/api/paper-trading/strategy-evaluations', (route: Route) => {
        strategyEvaluationRequests += 1;
        const statusCode = options.strategyEvaluationsStatus ?? 200;
        if (statusCode !== 200) {
            return route.fulfill({
                status: statusCode,
                json: {code: 'EVALUATION_ERROR', message: 'strategy evaluations unavailable', traceId: 'trc-review-eval'},
            });
        }
        return route.fulfill({
            status: 200,
            json: ('strategyEvaluations' in options ? options.strategyEvaluations : reviewsStrategyEvaluations) ?? null,
        });
    });

    await page.route('**/api/paper-trading/auto-reviews', (route: Route) => {
        autoReviewRequests += 1;
        const statusCode = options.autoReviewsStatus ?? 200;
        if (statusCode !== 200) {
            return route.fulfill({
                status: statusCode,
                json: {code: 'AUTO_REVIEW_ERROR', message: 'auto reviews unavailable', traceId: 'trc-review-auto'},
            });
        }
        return route.fulfill({
            status: 200,
            json: ('autoReviews' in options ? options.autoReviews : reviewsAutoReviews) ?? null,
        });
    });

    return {
        strategyEvaluationRequests: () => strategyEvaluationRequests,
        autoReviewRequests: () => autoReviewRequests,
        portfolioSummaryRequests: () => portfolioSummaryRequests,
        diagnosticsRequests: () => diagnosticsRequests,
    };
}

test.describe('paper trading reviews child route', () => {
    test('K5-C3：/paper-trading/reviews 渲染 Strategy Evaluation 与 Auto Review', async ({page}) => {
        const stubs = await seedAuthAndReviewsRouteStubs(page);

        await page.goto('/paper-trading/reviews');

        await expect(page).toHaveURL(/\/paper-trading\/reviews$/);
        await expect(page.getByRole('heading', {name: 'Paper Trading'})).toBeVisible();
        await expect(page.getByText('Section A · Strategy Evaluation Dashboard')).toBeVisible();
        await expect(page.getByText('Section B · Auto Review Dashboard')).toBeVisible();
        await expect(page.getByText(/Paper-only reviews/)).toBeVisible();
        await expect(page.getByText(/no investment advice/)).toBeVisible();

        const strategyEvaluation = page.getByRole('region', {name: 'Paper 策略评估', exact: true});
        await expect(strategyEvaluation).toBeVisible();
        await expect(strategyEvaluation.getByText('SIM/Paper only · Internal evaluation')).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 策略评估表'}).locator('tbody tr[data-row-key="eval-route-strong"]')).toHaveCount(1);
        await expect(page.getByRole('region', {name: 'Paper 发布评估表'}).locator('tbody tr[data-row-key="pub-eval-route-1"]')).toHaveCount(1);

        const autoReview = page.getByRole('region', {name: 'Paper 自动复盘', exact: true});
        await expect(autoReview).toBeVisible();
        await expect(autoReview.getByText('SIM/Paper only · Rules-based review')).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 自动复盘 Run 表'}).locator('tbody tr[data-row-key="review-route-risk"]')).toHaveCount(1);
        await expect(page.getByRole('region', {name: 'Paper 自动复盘问题聚类'}).locator('tbody tr[data-row-key="cluster-route-risk"]')).toHaveCount(1);

        expect(stubs.strategyEvaluationRequests()).toBe(1);
        expect(stubs.autoReviewRequests()).toBe(1);
        expect(stubs.portfolioSummaryRequests()).toBe(0);
        expect(stubs.diagnosticsRequests()).toBe(0);
    });

    test('K5-C3：切到 reviews 不触发 portfolio 或 diagnostics query', async ({page}) => {
        const stubs = await seedAuthAndReviewsRouteStubs(page);

        await page.goto('/paper-trading/portfolio');
        await expect(page.getByRole('region', {name: 'Paper 组合看板'})).toBeVisible();
        expect(stubs.portfolioSummaryRequests()).toBe(1);
        expect(stubs.diagnosticsRequests()).toBe(0);

        await page.getByText('Reviews', {exact: true}).click();
        await expect(page).toHaveURL(/\/paper-trading\/reviews$/);
        await expect(page.getByRole('region', {name: 'Paper 策略评估', exact: true})).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 自动复盘', exact: true})).toBeVisible();

        expect(stubs.strategyEvaluationRequests()).toBe(1);
        expect(stubs.autoReviewRequests()).toBe(1);
        expect(stubs.portfolioSummaryRequests()).toBe(1);
        expect(stubs.diagnosticsRequests()).toBe(0);
    });

    test('K5-C3：reviews 空态同时覆盖 Strategy Evaluation 与 Auto Review', async ({page}) => {
        await seedAuthAndReviewsRouteStubs(page, {
            strategyEvaluations: emptyStrategyEvaluations,
            autoReviews: emptyAutoReviews,
        });

        await page.goto('/paper-trading/reviews');

        await expect(page.getByRole('region', {name: 'Paper 策略评估', exact: true}).getByText('暂无 Paper 策略评估数据，创建并运行 Paper run 后自动生成策略评估。')).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 自动复盘', exact: true}).getByText('暂无 Paper 自动复盘数据，创建并运行 Paper run 后自动生成规则化复盘。')).toBeVisible();
        await expect(page.getByText(/Paper-only reviews/)).toBeVisible();
        await expect(page.getByText(/no investment advice/)).toBeVisible();
    });

    test('K5-C3：reviews 错误态隔离且不白屏', async ({page}) => {
        const stubs = await seedAuthAndReviewsRouteStubs(page, {
            strategyEvaluationsStatus: 500,
            autoReviewsStatus: 500,
        });

        await page.goto('/paper-trading/reviews');

        await expect(page.getByRole('region', {name: 'Paper 策略评估', exact: true}).getByText('Paper 策略评估加载失败')).toBeVisible();
        await expect(page.getByRole('region', {name: 'Paper 自动复盘', exact: true}).getByText('Paper 自动复盘加载失败')).toBeVisible();
        await expect(page.getByRole('heading', {name: 'Paper Trading'})).toBeVisible();
        await expect(page.getByText(/Paper-only reviews/)).toBeVisible();

        expect(stubs.strategyEvaluationRequests()).toBe(1);
        expect(stubs.autoReviewRequests()).toBe(1);
        expect(stubs.portfolioSummaryRequests()).toBe(0);
        expect(stubs.diagnosticsRequests()).toBe(0);
    });
});
