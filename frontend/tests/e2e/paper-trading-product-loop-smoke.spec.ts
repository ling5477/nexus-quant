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

// 健康 STOPPED run 默认数据集：有订单、成交、持仓、PnL、风控通过；异常用例可逐项覆盖。
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
const defaultEquityCurve = [{
    equitySnapshotId: 'equity-loop-1',
    paperRunId: PAPER_RUN_ID,
    snapshotTime: '2026-06-24T01:02:30Z',
    totalEquity: '100060',
    cashBalance: '35000',
    positionValue: '65060',
    unrealizedPnl: '125',
    realizedPnl: '-65',
    drawdown: '0',
    source: 'E2E_STUB',
    createdAt: '2026-06-24T01:02:30Z',
}];
const defaultDailyReports = [{
    reportId: 'paper-daily-loop-1',
    paperRunId: PAPER_RUN_ID,
    reportDate: '2026-06-24',
    status: 'GENERATED',
    totalEquity: '100060',
    dailyPnl: '60',
    dailyReturn: '0.0006',
    maxDrawdown: '0',
    orderCount: 1,
    tradeCount: 1,
    alertCount: 0,
    riskRejectCount: 0,
    reportJson: '{}',
    generatedAt: '2026-06-24T01:03:30Z',
    createdAt: '2026-06-24T01:03:30Z',
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

const defaultPublishDetail = {
    publishRecordId: paperRun.publishId,
    backtestRunId: 'backtest-run-loop-1',
    backtestConfigId: 'backtest-config-loop-1',
    researchConfigId: 'research-loop-1',
    sourceStrategyId: 'strategy-loop-1',
    targetStrategyDefinitionId: 'strategy-definition-loop-1',
    strategyVersionId: paperRun.strategyVersionId,
    publishStatus: 'PUBLISHED',
    publishName: 'Loop comparison publish',
    publishedAt: '2026-06-24T00:58:00Z',
    evaluationSummaryJson: '{}',
    failureCode: null,
    failureMessage: null,
    publishSnapshotJson: '{}',
    versionSnapshotJson: '{}',
};

const defaultBacktestDetail = {
    backtestConfigId: defaultPublishDetail.backtestConfigId,
    researchConfigId: defaultPublishDetail.researchConfigId,
    name: 'Loop comparison backtest',
    description: 'paper comparison fixture',
    startTime: '2026-06-23T00:00:00Z',
    endTime: '2026-06-23T01:00:00Z',
    initialCapital: 100000,
    executionSpec: '{}',
    evaluationSpec: '{}',
    strategyVersionId: paperRun.strategyVersionId,
    strategyVersionSnapshotJson: '{}',
    paramSnapshotJson: '{}',
    configSnapshotJson: '{}',
    datasetId: 'dataset-loop-1',
    datasetSnapshotJson: '{}',
    configSnapshot: '{}',
    createdAt: '2026-06-24T00:50:00Z',
    updatedAt: '2026-06-24T00:55:00Z',
};

const defaultEvaluations = [{
    evalReportId: 'eval-loop-1',
    backtestRunId: defaultPublishDetail.backtestRunId,
    evaluationStatus: 'COMPLETED',
    evaluatedAt: '2026-06-24T00:57:00Z',
    initialCapital: 100000,
    finalEquity: 100120,
    netPnl: 120,
    totalReturnRate: 0.0012,
    totalReturn: 120,
    annualizedReturn: 0.02,
    maxDrawdown: 0,
    maxDrawdownRate: 0,
    winRate: 1,
    profitLossRatio: 1.2,
    sharpeRatio: 1.1,
    orderCount: 2,
    tradeCount: 2,
    metricsJson: '{}',
    failureCode: null,
    failureMessage: null,
}];

// 健康 STOPPED run 的后端聚合 summary：前端详情区优先消费它渲染复盘 / 诊断 / 时间线 / 关键指标。
const defaultSummary = {
    run: {
        ...paperRun,
        status: 'STOPPED',
        startedAt: '2026-06-24T01:04:00Z',
        stoppedAt: '2026-06-24T01:05:00Z',
    },
    counts: {
        orderCount: 1,
        tradeCount: 1,
        fillCount: 1,
        positionCount: 1,
        openAlertCount: 0,
        recoveryEventCount: 0,
    },
    latest: {
        order: defaultOrders[0],
        trade: defaultTrades[0],
        position: defaultPositions[0],
        equitySnapshot: defaultEquityCurve[0],
        dailyReport: defaultDailyReports[0],
        riskResult: defaultRiskResults[0],
        alert: null,
        recoveryEvent: null,
    },
    resultReview: {
        finalStatus: 'STOPPED',
        runtimeDurationText: '1 分钟 0 秒',
        netPnl: 60,
        riskResult: '通过',
        conclusion: '正常完成',
        conclusionLevel: 'info',
    },
    diagnoses: [{
        type: 'HEALTHY',
        severity: 'INFO',
        title: '暂无明显异常',
        description: '暂无明显异常：可继续查看时间线、订单、成交、持仓和 PnL。',
        checkTarget: '运行事件时间线、订单、成交、持仓、净 PnL',
    }],
    timeline: [
        {type: 'RUN_CREATED', status: 'CREATED', occurredAt: '2026-06-24T00:59:00Z', title: 'Paper run created', description: '创建于 SIM/Paper 环境，发布 ID publish-loop-created。'},
        {type: 'RUN_STARTED', status: 'RUNNING', occurredAt: '2026-06-24T01:04:00Z', title: 'Paper run started', description: '运行已进入 SIM/Paper 生命周期，不触发 LIVE 或真实交易所。'},
        {type: 'RUN_TERMINAL', status: 'STOPPED', occurredAt: '2026-06-24T01:05:00Z', title: 'Paper run stopped', description: '当前 run 已进入终态；历史订单、成交、持仓和风控事实仍可追溯。'},
        {type: 'LATEST_ORDER', status: 'FILLED', occurredAt: '2026-06-24T01:01:30Z', title: '最新订单状态事件', description: 'BUY MARKET BTC-USDT，数量 1，价格 65000。'},
        {type: 'LATEST_TRADE', status: 'FILLED', occurredAt: '2026-06-24T01:01:31Z', title: '最新成交事件', description: 'BUY BTC-USDT，成交数量 1，成交价 65000。'},
        {type: 'LATEST_POSITION', status: 'POSITION_UPDATED', occurredAt: '2026-06-24T01:02:00Z', title: '最新持仓更新时间', description: 'BTC-USDT 持仓 1，未实现盈亏 125。'},
        {type: 'LATEST_EQUITY', status: 'PNL_UP', occurredAt: '2026-06-24T01:02:30Z', title: '最新净 PnL / equity snapshot', description: '总权益 100060，净 PnL 60，来源 E2E_STUB。'},
        {type: 'LATEST_RISK', status: 'PASSED', occurredAt: '2026-06-24T01:03:00Z', title: '最新风控检查结果', description: 'BASIC_HEALTH_CHECK · LOW：paper loop ok'},
    ],
    safety: {
        environment: 'SIM/PAPER',
        liveEnabled: false,
        realExchangeTouched: false,
        message: 'SIM/Paper only · LIVE 未开启，不代表真实交易能力',
    },
};

// Paper 组合看板默认聚合（Loop-13）：盈利 run + 亏损高回撤 run + 数据不足/无交易 run。
// 前端组合看板直接消费后端 /portfolio/summary 单请求结果，无需对每个 run 扇出 summary。
const PORTFOLIO_RUN_A = 'paper-portfolio-a';
const PORTFOLIO_RUN_B = 'paper-portfolio-b';
const PORTFOLIO_RUN_C = 'paper-portfolio-c';
const portfolioRunA = {
    paperRunId: PORTFOLIO_RUN_A, status: 'STOPPED', symbol: 'BTC-USDT',
    strategyVersionId: 'sv-portfolio-1', publishId: 'pub-portfolio-1',
    currentEquity: 120000, initialEquity: 100000, totalPnl: 20000, totalReturn: 0.2,
    maxDrawdown: 0, riskBlocked: false, openAlertCount: 0, tradeCount: 5,
    lastActivityAt: '2026-06-22T02:00:00Z',
};
const portfolioRunB = {
    paperRunId: PORTFOLIO_RUN_B, status: 'STOPPED', symbol: 'ETH-USDT',
    strategyVersionId: 'sv-portfolio-2', publishId: 'pub-portfolio-2',
    currentEquity: 90000, initialEquity: 100000, totalPnl: -10000, totalReturn: -0.1,
    maxDrawdown: -0.25, riskBlocked: true, openAlertCount: 2, tradeCount: 3,
    lastActivityAt: '2026-06-21T02:00:00Z',
};
const portfolioRunC = {
    paperRunId: PORTFOLIO_RUN_C, status: 'CREATED', symbol: 'SOL-USDT',
    strategyVersionId: 'sv-portfolio-1', publishId: 'pub-portfolio-1',
    currentEquity: null, initialEquity: null, totalPnl: null, totalReturn: null,
    maxDrawdown: null, riskBlocked: false, openAlertCount: 0, tradeCount: 0,
    lastActivityAt: '2026-06-20T00:00:00Z',
};
// Loop-15 组合 equity / drawdown 时间序列：t1 仅 run-a 在册（run-b 未起跑 → missingRunCount 1），
// t2 双 run 在册刷新峰值 170000，t3 回落到 130000（最大回撤），t4 回升到 150000（当前回撤）。
const defaultPortfolioCurve = {
    points: [
        {timestamp: '2026-06-20T00:00:00Z', totalEquity: 100000, totalInitialEquity: 100000, totalPnl: 0, totalReturn: 0, peakEquity: 100000, drawdown: 0, sourceRunCount: 1, missingRunCount: 1},
        {timestamp: '2026-06-21T00:00:00Z', totalEquity: 170000, totalInitialEquity: 150000, totalPnl: 20000, totalReturn: 0.133333, peakEquity: 170000, drawdown: 0, sourceRunCount: 2, missingRunCount: 0},
        {timestamp: '2026-06-22T00:00:00Z', totalEquity: 130000, totalInitialEquity: 150000, totalPnl: -20000, totalReturn: -0.133333, peakEquity: 170000, drawdown: -0.235294, sourceRunCount: 2, missingRunCount: 0},
        {timestamp: '2026-06-23T00:00:00Z', totalEquity: 150000, totalInitialEquity: 150000, totalPnl: 0, totalReturn: 0, peakEquity: 170000, drawdown: -0.117647, sourceRunCount: 2, missingRunCount: 0},
    ],
    latestEquity: 150000,
    peakEquity: 170000,
    currentDrawdown: -0.117647,
    maxDrawdown: -0.235294,
    pointCount: 4,
    coverage: {comparableRunCount: 2, missingEquityRunCount: 1, incompletePointCount: 1},
};
const emptyPortfolioCurve = {
    points: [],
    latestEquity: null, peakEquity: null, currentDrawdown: null, maxDrawdown: null,
    pointCount: 0,
    coverage: {comparableRunCount: 0, missingEquityRunCount: 0, incompletePointCount: 0},
};
const defaultPortfolioSummary = {
    overview: {
        totalRuns: 3, runningCount: 0, stoppedCount: 2, failedCount: 0, cancelledCount: 0, createdCount: 1,
        totalInitialEquity: 200000, totalCurrentEquity: 210000, totalPnl: 10000, totalReturn: 0.05,
        returnEligibleRunCount: 2, worstRunDrawdown: -0.25, openAlertCount: 2,
        riskBlockedRunCount: 1, noTradeRunCount: 1, dataInsufficientRunCount: 1,
    },
    strategyGroups: [
        {key: 'sv-portfolio-1', runCount: 2, currentEquity: 120000, totalPnl: 20000, totalReturn: 0.2, worstDrawdown: 0, riskBlockedCount: 0, openAlertCount: 0, lastRunTime: '2026-06-22T02:00:00Z'},
        {key: 'sv-portfolio-2', runCount: 1, currentEquity: 90000, totalPnl: -10000, totalReturn: -0.1, worstDrawdown: -0.25, riskBlockedCount: 1, openAlertCount: 2, lastRunTime: '2026-06-21T02:00:00Z'},
    ],
    publishGroups: [
        {key: 'pub-portfolio-1', runCount: 2, currentEquity: 120000, totalPnl: 20000, totalReturn: 0.2, worstDrawdown: 0, riskBlockedCount: 0, openAlertCount: 0, lastRunTime: '2026-06-22T02:00:00Z'},
        {key: 'pub-portfolio-2', runCount: 1, currentEquity: 90000, totalPnl: -10000, totalReturn: -0.1, worstDrawdown: -0.25, riskBlockedCount: 1, openAlertCount: 2, lastRunTime: '2026-06-21T02:00:00Z'},
    ],
    highlights: {
        topWinner: portfolioRunA,
        worstDrawdown: portfolioRunB,
        highestRisk: portfolioRunB,
        mostRecent: portfolioRunA,
        noTradeRuns: [portfolioRunC],
        riskBlockedRuns: [portfolioRunB],
    },
    dataQuality: {
        missingEquityRuns: [portfolioRunC],
        dataInsufficientRuns: [portfolioRunC],
        missingBacktestSourceRuns: [portfolioRunC],
        missingPublishSourceRuns: [],
    },
    safety: {
        environment: 'SIM/PAPER', liveEnabled: false, realExchangeTouched: false,
        message: '该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现',
    },
    portfolioCurve: defaultPortfolioCurve,
};
const emptyPortfolioSummary = {
    overview: {
        totalRuns: 0, runningCount: 0, stoppedCount: 0, failedCount: 0, cancelledCount: 0, createdCount: 0,
        totalInitialEquity: null, totalCurrentEquity: null, totalPnl: null, totalReturn: null,
        returnEligibleRunCount: 0, worstRunDrawdown: null, openAlertCount: 0,
        riskBlockedRunCount: 0, noTradeRunCount: 0, dataInsufficientRunCount: 0,
    },
    strategyGroups: [],
    publishGroups: [],
    highlights: {topWinner: null, worstDrawdown: null, highestRisk: null, mostRecent: null, noTradeRuns: [], riskBlockedRuns: []},
    dataQuality: {missingEquityRuns: [], dataInsufficientRuns: [], missingBacktestSourceRuns: [], missingPublishSourceRuns: []},
    safety: {environment: 'SIM/PAPER', liveEnabled: false, realExchangeTouched: false, message: '该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现'},
    portfolioCurve: emptyPortfolioCurve,
};

// Loop-16 策略表现排行 fixture：3 个 strategyVersion（盈利低风险 / 高收益高回撤 / 数据不足且风控拦截）+ 2 个 publish。
// dataInsufficient / noTrade run 引用挂在 sv-rank-weak（publishId 为不在排行内的 pub-rank-weak），驱动每组派生计数。
const rankGoodRun = {
    paperRunId: 'rank-good-1', status: 'STOPPED', symbol: 'BTC-USDT',
    strategyVersionId: 'sv-rank-good', publishId: 'pub-rank-1',
    currentEquity: 130000, initialEquity: 100000, totalPnl: 30000, totalReturn: 0.3,
    maxDrawdown: -0.05, riskBlocked: false, openAlertCount: 0, tradeCount: 6,
    lastActivityAt: '2026-06-24T02:00:00Z',
};
const rankHighRiskRun = {
    paperRunId: 'rank-highrisk-1', status: 'STOPPED', symbol: 'ETH-USDT',
    strategyVersionId: 'sv-rank-highrisk', publishId: 'pub-rank-2',
    currentEquity: 150000, initialEquity: 100000, totalPnl: 50000, totalReturn: 0.5,
    maxDrawdown: -0.4, riskBlocked: true, openAlertCount: 2, tradeCount: 4,
    lastActivityAt: '2026-06-23T02:00:00Z',
};
const rankWeakRun1 = {
    paperRunId: 'rank-weak-1', status: 'CREATED', symbol: 'SOL-USDT',
    strategyVersionId: 'sv-rank-weak', publishId: 'pub-rank-weak',
    currentEquity: null, initialEquity: null, totalPnl: null, totalReturn: null,
    maxDrawdown: null, riskBlocked: true, openAlertCount: 1, tradeCount: 0,
    lastActivityAt: '2026-06-20T02:00:00Z',
};
const rankWeakRun2 = {...rankWeakRun1, paperRunId: 'rank-weak-2'};
const rankingPortfolioSummary = {
    overview: {
        totalRuns: 5, runningCount: 0, stoppedCount: 3, failedCount: 0, cancelledCount: 0, createdCount: 2,
        totalInitialEquity: 200000, totalCurrentEquity: 280000, totalPnl: 80000, totalReturn: 0.4,
        returnEligibleRunCount: 3, worstRunDrawdown: -0.4, openAlertCount: 3,
        riskBlockedRunCount: 3, noTradeRunCount: 2, dataInsufficientRunCount: 2,
    },
    strategyGroups: [
        {key: 'sv-rank-good', runCount: 2, currentEquity: 130000, totalPnl: 30000, totalReturn: 0.3, worstDrawdown: -0.05, riskBlockedCount: 0, openAlertCount: 0, lastRunTime: '2026-06-24T02:00:00Z', noTradeCount: 0, dataInsufficientCount: 0, comparableRunCount: 2, runningCount: 0, stoppedCount: 2, failedCount: 0, cancelledCount: 0, createdCount: 0},
        {key: 'sv-rank-highrisk', runCount: 1, currentEquity: 150000, totalPnl: 50000, totalReturn: 0.5, worstDrawdown: -0.4, riskBlockedCount: 1, openAlertCount: 2, lastRunTime: '2026-06-23T02:00:00Z', noTradeCount: 0, dataInsufficientCount: 0, comparableRunCount: 1, runningCount: 0, stoppedCount: 1, failedCount: 0, cancelledCount: 0, createdCount: 0},
        {key: 'sv-rank-weak', runCount: 2, currentEquity: null, totalPnl: null, totalReturn: null, worstDrawdown: null, riskBlockedCount: 2, openAlertCount: 1, lastRunTime: '2026-06-20T02:00:00Z', noTradeCount: 2, dataInsufficientCount: 2, comparableRunCount: 0, runningCount: 0, stoppedCount: 0, failedCount: 1, cancelledCount: 0, createdCount: 1},
    ],
    publishGroups: [
        {key: 'pub-rank-1', runCount: 2, currentEquity: 130000, totalPnl: 30000, totalReturn: 0.3, worstDrawdown: -0.05, riskBlockedCount: 0, openAlertCount: 0, lastRunTime: '2026-06-24T02:00:00Z', noTradeCount: 0, dataInsufficientCount: 0, comparableRunCount: 2, runningCount: 0, stoppedCount: 2, failedCount: 0, cancelledCount: 0, createdCount: 0},
        {key: 'pub-rank-2', runCount: 1, currentEquity: 150000, totalPnl: 50000, totalReturn: 0.5, worstDrawdown: -0.4, riskBlockedCount: 1, openAlertCount: 2, lastRunTime: '2026-06-23T02:00:00Z', noTradeCount: 0, dataInsufficientCount: 0, comparableRunCount: 1, runningCount: 0, stoppedCount: 1, failedCount: 0, cancelledCount: 0, createdCount: 0},
    ],
    highlights: {
        topWinner: rankHighRiskRun, worstDrawdown: rankHighRiskRun, highestRisk: rankWeakRun1, mostRecent: rankGoodRun,
        noTradeRuns: [rankWeakRun1, rankWeakRun2], riskBlockedRuns: [rankWeakRun1, rankHighRiskRun],
    },
    dataQuality: {
        missingEquityRuns: [rankWeakRun1, rankWeakRun2],
        dataInsufficientRuns: [rankWeakRun1, rankWeakRun2],
        missingBacktestSourceRuns: [],
        missingPublishSourceRuns: [rankWeakRun1, rankWeakRun2],
    },
    safety: {environment: 'SIM/PAPER', liveEnabled: false, realExchangeTouched: false, message: '该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现'},
    portfolioCurve: emptyPortfolioCurve,
};
// 旧后端响应（group 缺 Loop-17 精确计数字段）：前端应回退到 highlights/dataQuality 派生 noTrade/dataInsufficient，不崩溃。
const rankingLegacySummary = {
    ...rankingPortfolioSummary,
    strategyGroups: [
        {key: 'sv-rank-good', runCount: 2, currentEquity: 130000, totalPnl: 30000, totalReturn: 0.3, worstDrawdown: -0.05, riskBlockedCount: 0, openAlertCount: 0, lastRunTime: '2026-06-24T02:00:00Z'},
        {key: 'sv-rank-highrisk', runCount: 1, currentEquity: 150000, totalPnl: 50000, totalReturn: 0.5, worstDrawdown: -0.4, riskBlockedCount: 1, openAlertCount: 2, lastRunTime: '2026-06-23T02:00:00Z'},
        {key: 'sv-rank-weak', runCount: 2, currentEquity: null, totalPnl: null, totalReturn: null, worstDrawdown: null, riskBlockedCount: 2, openAlertCount: 1, lastRunTime: '2026-06-20T02:00:00Z'},
    ],
    publishGroups: [
        {key: 'pub-rank-1', runCount: 2, currentEquity: 130000, totalPnl: 30000, totalReturn: 0.3, worstDrawdown: -0.05, riskBlockedCount: 0, openAlertCount: 0, lastRunTime: '2026-06-24T02:00:00Z'},
        {key: 'pub-rank-2', runCount: 1, currentEquity: 150000, totalPnl: 50000, totalReturn: 0.5, worstDrawdown: -0.4, riskBlockedCount: 1, openAlertCount: 2, lastRunTime: '2026-06-23T02:00:00Z'},
    ],
};

type PaperLoopStubOptions = {
    seedRun?: boolean;
    status?: string;
    orders?: unknown[];
    trades?: unknown[];
    positions?: unknown[];
    equityCurve?: unknown[];
    dailyReports?: unknown[];
    riskResults?: unknown[];
    alerts?: unknown[];
    recoveryEvents?: unknown[];
    publishDetail?: unknown;
    backtestDetail?: unknown;
    evaluations?: unknown[];
    // 覆盖 run 自身的 strategyVersionId（传 null 模拟缺少策略版本链路头）。
    runStrategyVersionId?: string | null;
    // summary 覆盖：传入对象即用作 /summary 响应；传入 null 时返回 null（前端回退到明细派生）。
    summary?: unknown;
    // portfolio 覆盖：传入对象即用作 /portfolio/summary 响应（含空结构）；缺省用 defaultPortfolioSummary。
    portfolioSummary?: unknown;
};

async function seedAuthAndPaperLoopStubs(page: Page, options: PaperLoopStubOptions = {}): Promise<{setRunStatus: (status: string) => void}> {
    let currentRun = {
        ...paperRun,
        status: options.status ?? paperRun.status,
        strategyVersionId: 'runStrategyVersionId' in options ? options.runStrategyVersionId ?? null : paperRun.strategyVersionId,
        startedAt: options.status === 'RUNNING' ? '2026-06-24T01:04:00Z' : paperRun.startedAt,
        stoppedAt: options.status === 'STOPPED' ? '2026-06-24T01:05:00Z' : paperRun.stoppedAt,
    };
    let runs: typeof paperRun[] = options.seedRun ? [currentRun] : [];
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

    // Default all unmatched API calls to empty arrays so this smoke never touches a real backend.
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

    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/runs(?:\?.*)?$/, async (route: Route) => {
        if (route.request().method() === 'POST') {
            const payload = route.request().postDataJSON() as {publishId?: string};
            currentRun = {
                ...paperRun,
                publishId: payload.publishId ?? paperRun.publishId,
                status: 'CREATED',
                startedAt: null,
                stoppedAt: null,
                updatedAt: '2026-06-24T01:03:00Z',
            };
            runs = [currentRun];
            await route.fulfill({status: 200, json: currentRun});
            return;
        }
        await route.fulfill({status: 200, json: runs});
    });
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}`, (route: Route) => route.fulfill({
        status: 200,
        json: currentRun,
    }));
    // 后端聚合 summary 路由；'summary' in options 时用覆盖值（含 null / 异常结构以测试 fallback），否则用 defaultSummary。
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/summary`, (route: Route) => route.fulfill({
        status: 200,
        json: ('summary' in options ? options.summary : defaultSummary) ?? null,
    }));
    // Paper 组合看板聚合路由（Loop-13）；缺省用 defaultPortfolioSummary，可注入空结构覆盖。
    await page.route('**/api/paper-trading/portfolio/summary', (route: Route) => route.fulfill({
        status: 200,
        json: ('portfolioSummary' in options ? options.portfolioSummary : defaultPortfolioSummary) ?? null,
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/start`, (route: Route) => {
        currentRun = {
            ...currentRun,
            status: 'RUNNING',
            startedAt: '2026-06-24T01:04:00Z',
            stoppedAt: null,
            updatedAt: '2026-06-24T01:04:00Z',
        };
        runs = [currentRun];
        return route.fulfill({status: 200, json: currentRun});
    });
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/stop`, (route: Route) => {
        currentRun = {
            ...currentRun,
            status: 'STOPPED',
            stoppedAt: '2026-06-24T01:05:00Z',
            updatedAt: '2026-06-24T01:05:00Z',
        };
        runs = [currentRun];
        return route.fulfill({status: 200, json: currentRun});
    });
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/orders`, (route: Route) => route.fulfill({
        status: 200,
        json: options.orders ?? defaultOrders,
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/trades`, (route: Route) => route.fulfill({
        status: 200,
        json: options.trades ?? defaultTrades,
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/positions`, (route: Route) => route.fulfill({
        status: 200,
        json: options.positions ?? defaultPositions,
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/equity-curve`, (route: Route) => route.fulfill({
        status: 200,
        json: options.equityCurve ?? defaultEquityCurve,
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/daily-reports`, (route: Route) => route.fulfill({
        status: 200,
        json: options.dailyReports ?? defaultDailyReports,
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/risk-results`, (route: Route) => route.fulfill({
        status: 200,
        json: options.riskResults ?? defaultRiskResults,
    }));
    await page.route(`**/api/publishes/${paperRun.publishId}`, (route: Route) => {
        if ('publishDetail' in options && options.publishDetail === null) {
            return route.fulfill({status: 404, json: {code: 'NOT_FOUND', message: 'publish source missing'}});
        }
        return route.fulfill({status: 200, json: 'publishDetail' in options ? options.publishDetail : defaultPublishDetail});
    });
    await page.route(`**/api/backtest-configs/${defaultPublishDetail.backtestConfigId}`, (route: Route) => route.fulfill({
        status: 200,
        json: options.backtestDetail ?? defaultBacktestDetail,
    }));
    await page.route(/^https?:\/\/[^/]+\/api\/evaluations(?:\?.*)?$/, (route: Route) => route.fulfill({
        status: 200,
        json: options.evaluations ?? defaultEvaluations,
    }));
    // 告警 / 恢复事件默认空数组（健康用例）；异常用例可注入数据驱动异常原因聚合。
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/alerts**`, (route: Route) => route.fulfill({
        status: 200,
        json: options.alerts ?? [],
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/recovery-events**`, (route: Route) => route.fulfill({
        status: 200,
        json: options.recoveryEvents ?? [],
    }));
    return {setRunStatus};
}

async function openPaperRunDetail(page: Page): Promise<ReturnType<Page['getByRole']>> {
    await page.goto('/paper-trading');
    await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();
    await page.getByRole('button', {name: /查\s*询/}).click();
    const row = page.locator(`tr[data-row-key="${PAPER_RUN_ID}"]`);
    await expect(row).toBeVisible();
    await row.getByRole('link', {name: '查看详情'}).or(row.getByRole('button', {name: '查看详情'})).click();
    const detail = page.getByRole('region', {name: 'Paper Trading 详情'});
    await expect(detail.getByText(PAPER_RUN_ID).first()).toBeVisible();
    return detail;
}

async function expectLifecycleButtons(
    detail: ReturnType<Page['getByRole']>,
    status: string,
    startEnabled: boolean,
    stopEnabled: boolean,
): Promise<void> {
    await expect(detail.getByText(status).first()).toBeVisible();
    const startButton = detail.getByRole('button', {name: '启动 Paper Run'});
    const stopButton = detail.getByRole('button', {name: '停止 Paper Run'});
    if (startEnabled) {
        await expect(startButton).toBeEnabled();
    } else {
        await expect(startButton).toBeDisabled();
    }
    if (stopEnabled) {
        await expect(stopButton).toBeEnabled();
    } else {
        await expect(stopButton).toBeDisabled();
    }
    await expect(detail.getByText('生命周期操作仅作用于当前 SIM/Paper run；LIVE 未开启，不会触发真实交易所。')).toBeVisible();
    await expect(detail.getByText('Paper 执行闭环')).toBeVisible();
    await expect(detail.getByText('订单事实').first()).toBeVisible();
    await expect(detail.getByText('成交事实').first()).toBeVisible();
    await expect(detail.getByText('持仓事实').first()).toBeVisible();
    await expect(detail.getByText('净 PnL').first()).toBeVisible();
    await expect(detail.getByText('风控闭环').first()).toBeVisible();
    await expect(detail.getByText('运行事件时间线', {exact: true})).toBeVisible();
    await expect(detail.getByText('Paper run created')).toBeVisible();
    await expect(detail.getByText('SIM/Paper only · LIVE 未开启').first()).toBeVisible();
    // 异常原因聚合区域应在每个生命周期状态下可见。
    await expect(detail.getByText('异常原因聚合')).toBeVisible();
    await expect(detail.getByText('该诊断仅基于当前 Paper run 的查询结果，不代表真实交易能力，不触发 LIVE 或真实交易所。')).toBeVisible();
}

test.describe('paper trading product loop panel', () => {
    test('创建、启动、停止后仍聚合展示执行闭环', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page);

        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        await page.getByRole('button', {name: /创建\s*Paper\s*Run/i}).click();
        const dialog = page.getByRole('dialog', {name: /创建\s*Paper\s*Trading/});
        await expect(dialog).toBeVisible();
        await dialog.getByPlaceholder('发布记录 ID（publishId）').fill(paperRun.publishId);

        const createResponse = page.waitForResponse((response) => (
            response.url().includes('/api/paper-trading/runs')
            && response.request().method() === 'POST'
            && !response.url().endsWith('/start')
            && !response.url().endsWith('/stop')
        ));
        await page.getByRole('button', {name: 'OK', exact: true}).click();
        const created = await createResponse;
        expect(created.ok()).toBeTruthy();

        const detail = page.getByRole('region', {name: 'Paper Trading 详情'});
        await expect(detail.getByText(PAPER_RUN_ID).first()).toBeVisible();
        await expect(detail.getByText('CREATED').first()).toBeVisible();

        const startResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${PAPER_RUN_ID}/start`)
            && response.request().method() === 'POST'
        ));
        await detail.getByRole('button', {name: '启动 Paper Run'}).click();
        const started = await startResponse;
        expect(started.ok()).toBeTruthy();
        await expect(detail.getByText('RUNNING').first()).toBeVisible();

        const stopResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${PAPER_RUN_ID}/stop`)
            && response.request().method() === 'POST'
        ));
        await detail.getByRole('button', {name: '停止 Paper Run'}).click();
        const stopped = await stopResponse;
        expect(stopped.ok()).toBeTruthy();
        await expect(detail.getByText('STOPPED').first()).toBeVisible();

        await expect(detail.getByText('Paper 执行闭环')).toBeVisible();
        await expect(detail.getByText('订单 → 成交 → 持仓 / PnL → 风控')).toBeVisible();
        await expect(detail.getByText('只读聚合当前 Paper run 的执行事实。')).toBeVisible();
        await expect(detail.getByText('运行结果复盘')).toBeVisible();
        await expect(detail.getByText('最终状态')).toBeVisible();
        await expect(detail.getByText('运行时长')).toBeVisible();
        await expect(detail.getByText('1 分钟 0 秒').first()).toBeVisible();
        await expect(detail.getByText('订单数').first()).toBeVisible();
        await expect(detail.getByText('成交数').first()).toBeVisible();
        await expect(detail.getByText('持仓数').first()).toBeVisible();
        await expect(detail.getByText('风控结果').first()).toBeVisible();
        // 复盘结论同时出现在复盘横幅与链路摘要节点（复盘结论：…），用 exact 锁定复盘横幅。
        await expect(detail.getByText('正常完成', {exact: true})).toBeVisible();
        await expect(detail.getByText('该复盘只基于当前 Paper run 的查询结果，用于判断模拟运行质量，不代表真实交易能力。')).toBeVisible();
        await expect(detail.getByText('Backtest → Paper 结果对照')).toBeVisible();
        await expect(detail.getByText('PNL_DEVIATION')).toBeVisible();
        await expect(detail.getByText('收益偏差：Paper PnL 明显低于 backtest')).toBeVisible();
        await expect(detail.getByText('Backtest 与 Paper 均为模拟结果，不代表 LIVE 或真实交易表现。')).toBeVisible();
        await expect(detail.getByText('Strategy Version', {exact: true})).toBeVisible();
        await expect(detail.getByText('Publish ID', {exact: true})).toBeVisible();
        await expect(detail.getByText('Backtest ID / Trace ID', {exact: true})).toBeVisible();
        // backtestRunId 同时出现在对照卡片与链路卡片，用 .first 规避 strict 多匹配。
        await expect(detail.getByText(defaultPublishDetail.backtestRunId).first()).toBeVisible();
        await expect(detail.getByRole('columnheader', {name: '对比项'})).toBeVisible();
        await expect(detail.getByRole('columnheader', {name: 'Backtest'})).toBeVisible();
        await expect(detail.getByRole('columnheader', {name: 'Paper'})).toBeVisible();
        await expect(detail.getByText('状态').first()).toBeVisible();
        await expect(detail.getByText('订单数').first()).toBeVisible();
        await expect(detail.getByText('成交数').first()).toBeVisible();
        await expect(detail.getByText('净 PnL').first()).toBeVisible();
        await expect(detail.getByText('风控结果').first()).toBeVisible();
        await expect(detail.getByText('运行时间 / 样本区间')).toBeVisible();
        await expect(detail.getByText('策略版本 / 发布版本')).toBeVisible();

        // Strategy → Publish → Paper 链路（完整链路 fixture）：链路卡片 + 5 节点 + 完整诊断 + Paper-only 文案。
        await expect(detail.getByText('Strategy → Publish → Paper 链路')).toBeVisible();
        await expect(detail.getByText('SIM/Paper lineage · LIVE 未开启')).toBeVisible();
        await expect(detail.getByText('CHAIN_COMPLETE')).toBeVisible();
        await expect(detail.getByText('链路完整：Strategy Version → Publish → Backtest → Paper Run 均可识别')).toBeVisible();
        await expect(detail.getByText('策略版本').first()).toBeVisible();
        await expect(detail.getByText('策略发布', {exact: true})).toBeVisible();
        // 节点标签与完整诊断描述均含 '回测 / 评估'，用 exact 锁定链路节点标签。
        await expect(detail.getByText('回测 / 评估', {exact: true})).toBeVisible();
        await expect(detail.getByText('Paper 运行', {exact: true})).toBeVisible();
        await expect(detail.getByText('复盘 / 诊断', {exact: true})).toBeVisible();
        await expect(detail.getByText('已识别').first()).toBeVisible();
        await expect(detail.getByText(paperRun.strategyVersionId).first()).toBeVisible();
        await expect(detail.getByText('该链路仅展示研究、发布与 Paper 模拟运行关系，不代表 LIVE 或真实交易表现。')).toBeVisible();

        // Paper 账户资产与收益率（完整数据 fixture）：资产指标 + 周期收益 + Paper-only。
        await expect(detail.getByText('Paper 账户资产与收益率')).toBeVisible();
        await expect(detail.getByText('该收益统计仅基于 Paper 模拟账户与本地执行事实，不代表 LIVE 或真实交易表现。')).toBeVisible();
        await expect(detail.getByText('当前总资产')).toBeVisible();
        await expect(detail.getByText('总 PnL')).toBeVisible();
        await expect(detail.getByText('累计收益率')).toBeVisible();
        await expect(detail.getByText('最大回撤').first()).toBeVisible();
        await expect(detail.getByText('资金峰值')).toBeVisible();
        await expect(detail.getByText('初始资金')).toBeVisible();
        await expect(detail.getByText('周期收益（日 / 周 / 月 / 年 / 累计）')).toBeVisible();
        await expect(detail.getByText('+0.06%').first()).toBeVisible(); // 累计收益率 = 60 / 100000
        await expect(detail.getByText('数据不足').first()).toBeVisible(); // 单一快照下周/月/年周期收益不足

        await expect(detail.getByText('运行事件时间线', {exact: true})).toBeVisible();
        await expect(detail.getByText('SIM/Paper only · LIVE 未开启').first()).toBeVisible();
        await expect(detail.getByText('Paper run created')).toBeVisible();
        await expect(detail.getByText('Paper run started')).toBeVisible();
        await expect(detail.getByText('Paper run stopped')).toBeVisible();
        await expect(detail.getByText('最新订单状态事件')).toBeVisible();
        await expect(detail.getByText('最新成交事件')).toBeVisible();
        await expect(detail.getByText('最新持仓更新时间')).toBeVisible();
        await expect(detail.getByText('最新净 PnL / equity snapshot')).toBeVisible();
        await expect(detail.getByText('最新风控检查结果')).toBeVisible();
        await expect(detail.getByText('FILLED').first()).toBeVisible();
        await expect(detail.getByText('BASIC_HEALTH_CHECK · LOW', {exact: true})).toBeVisible();
        await expect(detail.getByText('PASSED').first()).toBeVisible();

        // 异常原因聚合：健康 STOPPED run 应得到 HEALTHY 结论（含类型、严重程度、建议检查对象、Paper-only 文案）。
        await expect(detail.getByText('异常原因聚合')).toBeVisible();
        await expect(detail.getByText('该诊断仅基于当前 Paper run 的查询结果，不代表真实交易能力，不触发 LIVE 或真实交易所。')).toBeVisible();
        await expect(detail.getByText('暂无明显异常', {exact: true})).toBeVisible();
        await expect(detail.getByText('HEALTHY')).toBeVisible();
        await expect(detail.getByText('INFO').first()).toBeVisible();
        await expect(detail.getByText('建议检查：运行事件时间线、订单、成交、持仓、净 PnL')).toBeVisible();

        // summary 成功加载 → 不出现 fallback 提示，证明复盘 / 诊断 / 时间线由 summary 驱动。
        await expect(detail.getByText('运行摘要聚合接口加载失败，已回退到明细查询派生展示；下方明细表格不受影响。')).toHaveCount(0);

        const bodyText = await detail.innerText();
        expect(bodyText).toContain('订单事实');
        expect(bodyText).toContain('成交事实');
        expect(bodyText).toContain('持仓事实');
        expect(bodyText).toContain('净 PnL');
        expect(bodyText).toContain('60.00');
        expect(bodyText).toContain('120.00');
        expect(bodyText).toContain('通过');
    });

    test('详情区生命周期按钮按 run 状态禁用并保持 Paper-only 文案', async ({page}) => {
        test.setTimeout(60_000);
        const harness = await seedAuthAndPaperLoopStubs(page, {seedRun: true});
        const cases = [
            {status: 'CREATED', startEnabled: true, stopEnabled: false},
            {status: 'RUNNING', startEnabled: false, stopEnabled: true},
            {status: 'STOPPED', startEnabled: false, stopEnabled: false},
            {status: 'FAILED', startEnabled: false, stopEnabled: false},
            {status: 'CANCELLED', startEnabled: false, stopEnabled: false},
            {status: 'UNKNOWN', startEnabled: false, stopEnabled: false},
        ];

        for (const item of cases) {
            await test.step(`${item.status} lifecycle controls`, async () => {
                harness.setRunStatus(item.status);
                const detail = await openPaperRunDetail(page);
                await expectLifecycleButtons(detail, item.status, item.startEnabled, item.stopEnabled);
            });
        }
    });

    test('STOPPED run 异常场景聚合原因（风控拦截 / 告警 / 恢复）', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {
            seedRun: true,
            status: 'STOPPED',
            // 有订单但无成交、无 PnL；风控拦截 + 未处理告警 + 恢复事件，驱动多条异常原因。
            orders: [{
                paperOrderId: 'paper-order-blocked',
                paperRunId: PAPER_RUN_ID,
                symbol: 'BTC-USDT',
                side: 'BUY',
                orderType: 'MARKET',
                quantity: '1',
                price: '65000',
                status: 'REJECTED',
                reason: 'risk rejected',
                rawSignalJson: '{}',
                createdAt: '2026-06-24T01:01:00Z',
                updatedAt: '2026-06-24T01:01:30Z',
            }],
            trades: [],
            positions: [],
            equityCurve: [],
            dailyReports: [],
            riskResults: [{
                riskResultId: 'risk-blocked-1',
                paperRunId: PAPER_RUN_ID,
                checkType: 'MAX_DRAWDOWN_CHECK',
                status: 'REJECTED',
                severity: 'HIGH',
                message: 'max drawdown exceeded',
                inputSnapshotJson: '{}',
                resultSnapshotJson: '{}',
                createdAt: '2026-06-24T01:03:00Z',
            }],
            alerts: [{
                alertId: 'alert-blocked-1',
                paperRunId: PAPER_RUN_ID,
                alertType: 'RISK',
                severity: 'WARNING',
                status: 'OPEN',
                title: 'Drawdown breach',
                message: 'risk gate rejected order',
                source: 'RISK_ENGINE',
                eventSnapshotJson: '{}',
                acknowledgedBy: null,
                acknowledgedAt: null,
                resolvedAt: null,
                createdAt: '2026-06-24T01:03:10Z',
                updatedAt: '2026-06-24T01:03:10Z',
            }],
            recoveryEvents: [{
                recoveryEventId: 'recovery-blocked-1',
                paperRunId: PAPER_RUN_ID,
                recoveryType: 'AUTO_RECOVER',
                status: 'FAILED',
                reason: 'auto recover failed',
                requestJson: '{}',
                resultJson: '{}',
                startedAt: '2026-06-24T01:04:00Z',
                finishedAt: '2026-06-24T01:04:05Z',
                createdAt: '2026-06-24T01:04:00Z',
            }],
            // 后端聚合 summary 驱动异常诊断：风控拦截（BLOCKING）+ 告警（WARNING）+ 恢复（INFO）。
            summary: {
                run: {...paperRun, status: 'STOPPED', startedAt: null, stoppedAt: '2026-06-24T01:05:00Z'},
                counts: {orderCount: 1, tradeCount: 0, fillCount: 0, positionCount: 0, openAlertCount: 1, recoveryEventCount: 1},
                latest: {order: null, trade: null, position: null, equitySnapshot: null, dailyReport: null, riskResult: null, alert: null, recoveryEvent: null},
                resultReview: {finalStatus: 'STOPPED', runtimeDurationText: '-', netPnl: null, riskResult: '拦截', conclusion: '风控拦截', conclusionLevel: 'danger'},
                diagnoses: [
                    {type: 'RISK_BLOCKED', severity: 'BLOCKING', title: '风控拦截', description: '风控拦截：订单可能未进入交易执行链路，请优先查看风控检查结果。', checkTarget: '风控状态卡片、风控结果 Tab'},
                    {type: 'ALERT_PRESENT', severity: 'WARNING', title: '存在未处理告警', description: '存在 1 条未处理告警：请查看告警面板并确认处理。', checkTarget: '告警面板'},
                    {type: 'RECOVERY_PRESENT', severity: 'INFO', title: '存在恢复事件', description: '存在恢复事件：本次 run 曾触发恢复或重试，请关注是否稳定。', checkTarget: '恢复事件面板'},
                ],
                timeline: [
                    {type: 'RUN_CREATED', status: 'CREATED', occurredAt: '2026-06-24T00:59:00Z', title: 'Paper run created', description: '创建于 SIM/Paper 环境，发布 ID publish-loop-created。'},
                    {type: 'RUN_TERMINAL', status: 'STOPPED', occurredAt: '2026-06-24T01:05:00Z', title: 'Paper run stopped', description: '当前 run 已进入终态；历史订单、成交、持仓和风控事实仍可追溯。'},
                    {type: 'LATEST_RISK', status: 'REJECTED', occurredAt: '2026-06-24T01:03:00Z', title: '最新风控检查结果', description: 'MAX_DRAWDOWN_CHECK · HIGH：max drawdown exceeded'},
                ],
                safety: {environment: 'SIM/PAPER', liveEnabled: false, realExchangeTouched: false, message: 'SIM/Paper only · LIVE 未开启，不代表真实交易能力'},
            },
        });

        const detail = await openPaperRunDetail(page);

        // 异常原因聚合区域：类型、严重程度、建议检查对象、Paper-only / 不触发 LIVE 文案。
        await expect(detail.getByText('异常原因聚合')).toBeVisible();
        await expect(detail.getByText('该诊断仅基于当前 Paper run 的查询结果，不代表真实交易能力，不触发 LIVE 或真实交易所。')).toBeVisible();

        // BLOCKING：风控拦截（'风控拦截' 同时出现在运行结果复盘结论中，用 .first 规避 strict 多匹配）。
        await expect(detail.getByText('RISK_BLOCKED', {exact: true})).toBeVisible();
        await expect(detail.getByText('风控拦截').first()).toBeVisible();
        await expect(detail.getByText('BLOCKING')).toBeVisible();
        await expect(detail.getByText('建议检查：风控状态卡片、风控结果 Tab')).toBeVisible();

        // WARNING：存在未处理告警（'WARNING' 同时是告警面板严重程度，用 .first 规避 strict 多匹配）。
        await expect(detail.getByText('ALERT_PRESENT')).toBeVisible();
        await expect(detail.getByText('存在未处理告警')).toBeVisible();
        await expect(detail.getByText('WARNING').first()).toBeVisible();
        await expect(detail.getByText('建议检查：告警面板')).toBeVisible();

        // INFO：存在恢复事件。
        await expect(detail.getByText('RECOVERY_PRESENT')).toBeVisible();
        await expect(detail.getByText('存在恢复事件', {exact: true})).toBeVisible();

        // 健康用例下才会出现的 HEALTHY 结论在异常场景不应出现。
        await expect(detail.getByText('暂无明显异常', {exact: true})).toHaveCount(0);

        // 既有运行结果复盘卡片与运行事件时间线保持可见。
        await expect(detail.getByText('运行结果复盘')).toBeVisible();
        await expect(detail.getByText('运行事件时间线', {exact: true})).toBeVisible();
    });

    test('summary 结构异常时页面不崩，回退到明细派生并保留 Paper-only 文案', async ({page}) => {
        // summary 返回空对象（无 counts）→ 前端守卫判为不可用 → 回退到已加载明细派生展示。
        // Loop-9 懒加载下回退是“可控回退”：明细 Tab 未展开则不主动拉全量，复盘按已有事实给出结论。
        await seedAuthAndPaperLoopStubs(page, {seedRun: true, status: 'STOPPED', summary: {}});

        const detail = await openPaperRunDetail(page);

        // 回退后详情区仍完整渲染（来自明细派生），页面不崩。
        await expect(detail.getByText('运行结果复盘')).toBeVisible();
        await expect(detail.getByText('异常原因聚合')).toBeVisible();
        await expect(detail.getByText('运行事件时间线', {exact: true})).toBeVisible();
        // 懒加载下 STOPPED 且订单明细未展开（无订单事实）→ 复盘结论“无交易”、诊断 NO_ORDER。
        // '无交易' 同时出现在复盘横幅与链路摘要节点（复盘结论：无交易），用 exact 锁定复盘横幅。
        await expect(detail.getByText('无交易', {exact: true})).toBeVisible();
        await expect(detail.getByText('NO_ORDER')).toBeVisible();

        // Paper-only / LIVE 未开启文案仍存在。
        await expect(detail.getByText('SIM/Paper only · LIVE 未开启').first()).toBeVisible();
        await expect(detail.getByText('该诊断仅基于当前 Paper run 的查询结果，不代表真实交易能力，不触发 LIVE 或真实交易所。')).toBeVisible();
    });

    test('无来源 backtest 时对照卡片展示空态且不阻塞详情', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {
            seedRun: true,
            status: 'STOPPED',
            publishDetail: {
                ...defaultPublishDetail,
                backtestRunId: null,
                backtestConfigId: null,
            },
        });

        const detail = await openPaperRunDetail(page);

        await expect(detail.getByText('Backtest → Paper 结果对照')).toBeVisible();
        await expect(detail.getByText('NO_BACKTEST_SOURCE')).toBeVisible();
        await expect(detail.getByText('无法对照：缺少来源 backtest')).toBeVisible();
        await expect(detail.getByText('暂无来源 backtest / 无法对照；当前 Paper run 详情仍可独立查看。')).toBeVisible();
        await expect(detail.getByText('Backtest 与 Paper 均为模拟结果，不代表 LIVE 或真实交易表现。')).toBeVisible();

        // Strategy → Publish → Paper 链路：缺少 backtest 时不崩溃，链路诊断说明无法做完整回测对照。
        await expect(detail.getByText('Strategy → Publish → Paper 链路')).toBeVisible();
        await expect(detail.getByText('CHAIN_MISSING_BACKTEST')).toBeVisible();
        await expect(detail.getByText('链路不完整：缺少 backtest')).toBeVisible();
        await expect(detail.getByText('缺少 backtest：Paper run 可查看，但无法做完整回测对照。').first()).toBeVisible();
        await expect(detail.getByText('缺失').first()).toBeVisible();
        await expect(detail.getByText('该链路仅展示研究、发布与 Paper 模拟运行关系，不代表 LIVE 或真实交易表现。')).toBeVisible();

        await expect(detail.getByText('运行结果复盘')).toBeVisible();
        await expect(detail.getByText('异常原因聚合')).toBeVisible();
        await expect(detail.getByText('运行事件时间线', {exact: true})).toBeVisible();
    });

    test('缺少 publish 来源时链路展示来源不完整且不崩溃', async ({page}) => {
        // publish detail 返回 404 → 前端 publishDetail = null → 链路 publish 节点来源不完整、回测节点缺失。
        await seedAuthAndPaperLoopStubs(page, {seedRun: true, status: 'STOPPED', publishDetail: null});

        const detail = await openPaperRunDetail(page);

        await expect(detail.getByText('Strategy → Publish → Paper 链路')).toBeVisible();
        await expect(detail.getByText('CHAIN_MISSING_PUBLISH')).toBeVisible();
        await expect(detail.getByText('链路不完整：缺少 publish 来源')).toBeVisible();
        await expect(detail.getByText('来源不完整').first()).toBeVisible();
        await expect(detail.getByText('Paper run 引用了发布 ID，但发布详情暂不可解析，来源不完整。')).toBeVisible();
        await expect(detail.getByText('该链路仅展示研究、发布与 Paper 模拟运行关系，不代表 LIVE 或真实交易表现。')).toBeVisible();

        // 详情区其它产品化卡片仍在，不崩溃。
        await expect(detail.getByText('运行结果复盘')).toBeVisible();
        await expect(detail.getByText('异常原因聚合')).toBeVisible();
        await expect(detail.getByText('运行事件时间线', {exact: true})).toBeVisible();
    });

    test('缺少 strategy version 时链路标记策略版本不可追踪且不崩溃', async ({page}) => {
        // run / publish / backtest 的 strategyVersionId 全部为空 → 链路头缺失，但发布与回测仍可识别。
        await seedAuthAndPaperLoopStubs(page, {
            seedRun: true,
            status: 'STOPPED',
            runStrategyVersionId: null,
            publishDetail: {...defaultPublishDetail, strategyVersionId: null},
            backtestDetail: {...defaultBacktestDetail, strategyVersionId: null},
        });

        const detail = await openPaperRunDetail(page);

        await expect(detail.getByText('Strategy → Publish → Paper 链路')).toBeVisible();
        await expect(detail.getByText('CHAIN_MISSING_STRATEGY_VERSION')).toBeVisible();
        await expect(detail.getByText('链路不完整：缺少策略版本')).toBeVisible();
        await expect(detail.getByText('缺少 strategy version：策略版本信息不可追踪。')).toBeVisible();
        await expect(detail.getByText('缺失').first()).toBeVisible();
        await expect(detail.getByText('该链路仅展示研究、发布与 Paper 模拟运行关系，不代表 LIVE 或真实交易表现。')).toBeVisible();

        // 其它链路节点（发布 / 回测 / Paper run）仍可识别，详情区不崩溃。
        await expect(detail.getByText('策略发布')).toBeVisible();
        await expect(detail.getByText('回测 / 评估')).toBeVisible();
        await expect(detail.getByText('运行结果复盘')).toBeVisible();
    });

    test('多 equity snapshot 时聚合账户资产、回撤与周期收益', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {
            seedRun: true,
            status: 'STOPPED',
            // 初始 100000（来自来源 backtest initialCapital）→ 峰值 110000 → 回落 95000 → 回升 105000。
            equityCurve: [
                {equitySnapshotId: 'eq-acc-1', paperRunId: PAPER_RUN_ID, snapshotTime: '2026-06-01T00:00:00Z', totalEquity: '100000', cashBalance: '100000', positionValue: '0', unrealizedPnl: '0', realizedPnl: '0', drawdown: '0', source: 'E2E_STUB', createdAt: '2026-06-01T00:00:00Z'},
                {equitySnapshotId: 'eq-acc-2', paperRunId: PAPER_RUN_ID, snapshotTime: '2026-06-08T00:00:00Z', totalEquity: '110000', cashBalance: '110000', positionValue: '0', unrealizedPnl: '0', realizedPnl: '10000', drawdown: '0', source: 'E2E_STUB', createdAt: '2026-06-08T00:00:00Z'},
                {equitySnapshotId: 'eq-acc-3', paperRunId: PAPER_RUN_ID, snapshotTime: '2026-06-15T00:00:00Z', totalEquity: '95000', cashBalance: '95000', positionValue: '0', unrealizedPnl: '0', realizedPnl: '-5000', drawdown: '0.1364', source: 'E2E_STUB', createdAt: '2026-06-15T00:00:00Z'},
                {equitySnapshotId: 'eq-acc-4', paperRunId: PAPER_RUN_ID, snapshotTime: '2026-06-22T00:00:00Z', totalEquity: '105000', cashBalance: '105000', positionValue: '0', unrealizedPnl: '0', realizedPnl: '5000', drawdown: '0.0455', source: 'E2E_STUB', createdAt: '2026-06-22T00:00:00Z'},
            ],
            dailyReports: [{
                reportId: 'paper-daily-acc-1', paperRunId: PAPER_RUN_ID, reportDate: '2026-06-22', status: 'GENERATED',
                totalEquity: '105000', dailyPnl: '1000', dailyReturn: '0.0096', maxDrawdown: '0.1364',
                orderCount: 4, tradeCount: 4, alertCount: 0, riskRejectCount: 0, reportJson: '{}',
                generatedAt: '2026-06-22T01:00:00Z', createdAt: '2026-06-22T01:00:00Z',
            }],
        });

        const detail = await openPaperRunDetail(page);

        await expect(detail.getByText('Paper 账户资产与收益率')).toBeVisible();
        await expect(detail.getByText('当前总资产')).toBeVisible();
        await expect(detail.getByText('+5.00%').first()).toBeVisible();   // 累计收益率 = (105000-100000)/100000
        await expect(detail.getByText('-13.64%')).toBeVisible();          // 最大回撤 = (95000-110000)/110000
        await expect(detail.getByText('-4.55%')).toBeVisible();           // 当前回撤 = (105000-110000)/110000
        await expect(detail.getByText('+10.53%')).toBeVisible();          // 周收益率 = (105000-95000)/95000
        await expect(detail.getByText('资金峰值')).toBeVisible();
        await expect(detail.getByText('110,000.00').first()).toBeVisible(); // 资金峰值
        await expect(detail.getByText('最近 equity snapshot')).toBeVisible();
        await expect(detail.getByText('数据不足').first()).toBeVisible(); // 月 / 年周期窗口外无 baseline
        await expect(detail.getByText('该收益统计仅基于 Paper 模拟账户与本地执行事实，不代表 LIVE 或真实交易表现。')).toBeVisible();
    });

    test('无 equity / 无日报时账户收益概览展示空态且不伪造收益率', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {
            seedRun: true,
            status: 'STOPPED',
            equityCurve: [],
            dailyReports: [],
        });

        const detail = await openPaperRunDetail(page);

        await expect(detail.getByText('Paper 账户资产与收益率')).toBeVisible();
        await expect(detail.getByText('暂无 Paper 账户资产数据，运行产生 equity 快照或日报后自动汇总。')).toBeVisible();
        await expect(detail.getByText('数据不足，无法计算收益率')).toBeVisible();
        await expect(detail.getByText('该收益统计仅基于 Paper 模拟账户与本地执行事实，不代表 LIVE 或真实交易表现。')).toBeVisible();

        // 不崩溃：其它产品化卡片仍在。
        await expect(detail.getByText('运行结果复盘')).toBeVisible();
        await expect(detail.getByText('Strategy → Publish → Paper 链路')).toBeVisible();
    });

    test('详情首屏只走 detail + summary，明细按需进入 Tab 后才懒加载', async ({page}) => {
        const calls = {orders: 0, trades: 0, positions: 0, risk: 0};
        // 在导航前注册请求计数，统计底部明细 Tab 对应的明细查询是否被请求。
        page.on('request', (req) => {
            const u = req.url();
            if (u.endsWith(`/runs/${PAPER_RUN_ID}/orders`)) calls.orders++;
            else if (u.endsWith(`/runs/${PAPER_RUN_ID}/trades`)) calls.trades++;
            else if (u.endsWith(`/runs/${PAPER_RUN_ID}/positions`)) calls.positions++;
            else if (u.endsWith(`/runs/${PAPER_RUN_ID}/risk-results`)) calls.risk++;
        });

        await seedAuthAndPaperLoopStubs(page, {seedRun: true, status: 'STOPPED'});
        const detail = await openPaperRunDetail(page);

        // 首屏由 summary 驱动渲染复盘 / 诊断 / 时间线，无需明细查询。
        await expect(detail.getByText('运行结果复盘')).toBeVisible();
        await expect(detail.getByText('异常原因聚合')).toBeVisible();
        await expect(detail.getByText('运行事件时间线', {exact: true})).toBeVisible();
        await page.waitForLoadState('networkidle');

        // 关键断言：首屏不主动请求订单 / 成交 / 持仓 / 风控明细。
        expect(calls.orders).toBe(0);
        expect(calls.trades).toBe(0);
        expect(calls.positions).toBe(0);
        expect(calls.risk).toBe(0);

        // 进入「订单」Tab → 仅触发订单明细懒加载，其它明细仍不请求。
        await detail.getByRole('tab', {name: '订单'}).click();
        await expect.poll(() => calls.orders).toBeGreaterThan(0);
        await expect(detail.getByText('paper-order-1')).toBeVisible();
        expect(calls.trades).toBe(0);
        expect(calls.positions).toBe(0);
        expect(calls.risk).toBe(0);

        // 进入「风控结果」Tab → 触发风控明细懒加载。
        await detail.getByRole('tab', {name: '风控结果'}).click();
        await expect.poll(() => calls.risk).toBeGreaterThan(0);
        // 订单只在切到订单 Tab 时请求过一次，未被重复全量拉取。
        expect(calls.positions).toBe(0);
    });

    test('Paper 组合看板展示组合总览、分组排行、Run 排行与数据质量提示', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {seedRun: true, status: 'STOPPED'});
        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        const dashboard = page.getByRole('region', {name: 'Paper 组合看板'});
        await expect(dashboard).toBeVisible();
        // Paper-only / 不代表真实交易表现安全提示。
        await expect(dashboard.getByText('跨多个 Paper run 只读汇总组合表现。')).toBeVisible();
        await expect(dashboard.getByText('该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。')).toBeVisible();

        // 组合总览：总资产 / 总 PnL / 累计收益率 / 最大回撤 / 状态数量。
        await expect(dashboard.getByText('Paper run 总数')).toBeVisible();
        await expect(dashboard.getByText('当前总资产')).toBeVisible();
        await expect(dashboard.getByText('210,000.00')).toBeVisible();            // 组合当前总资产
        await expect(dashboard.getByText('累计收益率').first()).toBeVisible();
        await expect(dashboard.getByText('+5.00%')).toBeVisible();                // 组合累计收益率 = 10000/200000
        await expect(dashboard.getByText('-25.00%').first()).toBeVisible();       // 组合最大回撤（按单 run 统计）
        await expect(dashboard.getByText('按单 run 最大回撤统计')).toBeVisible();
        await expect(dashboard.getByText('RUNNING 数量')).toBeVisible();
        await expect(dashboard.getByText(/状态分布：RUNNING 0 · STOPPED 2 · FAILED 0/)).toBeVisible();

        // 策略 / 发布维度排行表。
        await expect(dashboard.getByText('Strategy Version 收益排行')).toBeVisible();
        await expect(dashboard.getByText('Publish 收益排行')).toBeVisible();
        await expect(dashboard.getByText('sv-portfolio-1').first()).toBeVisible();
        await expect(dashboard.getByText('sv-portfolio-2').first()).toBeVisible();
        await expect(dashboard.getByText('pub-portfolio-1').first()).toBeVisible();
        await expect(dashboard.getByText('+20.00%').first()).toBeVisible();       // 策略/发布组 A 累计收益率
        await expect(dashboard.getByText('-10.00%').first()).toBeVisible();       // 策略/发布组 B 累计收益率

        // Run 排行：收益最高 / 回撤最大 / 风险最高 / 最近活跃。
        await expect(dashboard.getByText('收益最高')).toBeVisible();
        await expect(dashboard.getByText('回撤最大')).toBeVisible();
        await expect(dashboard.getByText('风险最高')).toBeVisible();
        await expect(dashboard.getByText('最近活跃')).toBeVisible();
        await expect(dashboard.getByText('风控拦截 run')).toBeVisible();
        await expect(dashboard.getByText('无交易 run')).toBeVisible();
        await expect(dashboard.getByText(PORTFOLIO_RUN_A).first()).toBeVisible(); // 收益最高 run
        await expect(dashboard.getByText(PORTFOLIO_RUN_B).first()).toBeVisible(); // 回撤最大 / 风控拦截 run
        await expect(dashboard.getByText(PORTFOLIO_RUN_C).first()).toBeVisible(); // 无交易 / 数据不足 run

        // 数据质量提示。
        await expect(dashboard.getByText('数据质量提示')).toBeVisible();
        await expect(dashboard.getByText('缺 equity（1）')).toBeVisible();
        await expect(dashboard.getByText('数据不足（1）')).toBeVisible();
        await expect(dashboard.getByText('缺 backtest 来源（1）')).toBeVisible();
        await expect(dashboard.getByText('缺 publish 来源（0）')).toBeVisible();
    });

    test('Paper 组合看板无 run 时展示空态且不伪造组合收益率', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {portfolioSummary: emptyPortfolioSummary});
        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        const dashboard = page.getByRole('region', {name: 'Paper 组合看板'});
        await expect(dashboard).toBeVisible();
        await expect(dashboard.getByText('暂无 Paper run，创建并运行后自动汇总组合表现。')).toBeVisible();
        await expect(dashboard.getByText('数据不足，无法计算组合收益率')).toBeVisible();
        await expect(dashboard.getByText('该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。')).toBeVisible();
    });

    test('Paper 风险与回撤驾驶舱展示风险总览、回撤排行与风控/无交易清单', async ({page}) => {
        // 复用 Loop-13 组合 summary（runA 盈利低风险 / runB 亏损高回撤且风控拦截 / runC 无交易且数据不足）。
        await seedAuthAndPaperLoopStubs(page, {seedRun: true, status: 'STOPPED'});
        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        const risk = page.getByRole('region', {name: 'Paper 风险与回撤驾驶舱'});
        await expect(risk).toBeVisible();

        // Paper-only / 不代表真实交易风险安全提示。
        await expect(risk.getByText('该风险看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易风险。')).toBeVisible();
        await expect(risk.getByText('聚焦组合内最高风险、最大回撤、风控拦截、无交易与数据不足的 Paper run。')).toBeVisible();

        // 1) 风险总览指标：最大回撤 / 风控拦截 / 未处理告警 / 无交易 / 数据不足 / FAILED·CANCELLED / 高风险。
        const ddCard = risk.locator('.nq-metric-card', {hasText: '最大单 run 回撤'});
        await expect(ddCard.locator('.nq-metric-card__value')).toContainText('-25.00%');
        await expect(ddCard).toContainText('当前最大回撤 run：paper-portfolio-b');
        await expect(risk.locator('.nq-metric-card', {hasText: '风控拦截 run'}).locator('.nq-metric-card__value')).toHaveText('1');
        await expect(risk.locator('.nq-metric-card', {hasText: '未处理告警'}).locator('.nq-metric-card__value')).toHaveText('2');
        await expect(risk.locator('.nq-metric-card', {hasText: '无交易 run'}).locator('.nq-metric-card__value')).toHaveText('1');
        await expect(risk.locator('.nq-metric-card', {hasText: '数据不足 run'}).locator('.nq-metric-card__value')).toHaveText('1');
        await expect(risk.locator('.nq-metric-card', {hasText: 'FAILED / CANCELLED'}).locator('.nq-metric-card__value')).toHaveText('0');
        await expect(risk.getByText('FAILED 0 · CANCELLED 0')).toBeVisible();
        await expect(risk.locator('.nq-metric-card', {hasText: '高风险 run 数'}).locator('.nq-metric-card__value')).toHaveText('1');
        await expect(risk.getByText('风控拦截 + 异常终态合计')).toBeVisible();

        // 2) 组合资金曲线与回撤（Loop-15 真实组合时间序列口径）。
        await expect(risk.getByText('组合资金曲线与回撤', {exact: true})).toBeVisible();
        await expect(risk.locator('.nq-metric-card', {hasText: '最新组合 equity'}).locator('.nq-metric-card__value')).toContainText('150,000.00');
        await expect(risk.locator('.nq-metric-card', {hasText: '资金峰值'}).locator('.nq-metric-card__value')).toContainText('170,000.00');
        await expect(risk.locator('.nq-metric-card', {hasText: '组合当前回撤'}).locator('.nq-metric-card__value')).toContainText('-11.76%');
        await expect(risk.locator('.nq-metric-card', {hasText: '组合最大回撤'}).locator('.nq-metric-card__value')).toContainText('-23.53%');
        await expect(risk.locator('.nq-metric-card', {hasText: '可比 run'}).locator('.nq-metric-card__value')).toHaveText('2');
        await expect(risk.locator('.nq-metric-card', {hasText: '可比 run'})).toContainText('缺 equity 1 · 不完整点 1');
        // 组合资金曲线 + 回撤曲线图（复用 Design System ECharts 封装），及覆盖度/口径说明。
        await expect(risk.getByText('组合资金曲线', {exact: true})).toBeVisible();
        await expect(risk.getByText('组合回撤曲线', {exact: true})).toBeVisible();
        await expect(risk.locator('.nq-chart')).toHaveCount(2);
        await expect(risk.getByText('每个时间点 sourceRunCount 为已在册 run 数', {exact: false})).toBeVisible();
        await expect(risk.getByText('该组合资金曲线仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。', {exact: false})).toBeVisible();
        await expect(risk.getByText('该曲线是组合资金合计曲线，不等同于严格时间加权收益。', {exact: false})).toBeVisible();
        // 采样点表折叠保留（默认折叠，header 可见，保持数据透明度）。
        await expect(risk.getByText('组合曲线采样点（共 4，展开查看最近 4 条）')).toBeVisible();

        // 3) 回撤分析（单 run 口径，与组合曲线互补）。
        await expect(risk.getByText('回撤分析')).toBeVisible();
        await expect(risk.getByText('0% ~ -5%')).toBeVisible();
        await expect(risk.getByText('-5% ~ -10%')).toBeVisible();
        await expect(risk.getByText('-10% ~ -20%')).toBeVisible();
        await expect(risk.getByText('< -20%')).toBeVisible();
        await expect(risk.getByText('组合层真实时间序列回撤见上方「组合资金曲线与回撤」。', {exact: false})).toBeVisible();
        // 回撤排行表含高回撤 run 与低风险 run。
        await expect(risk.getByText(PORTFOLIO_RUN_B).first()).toBeVisible();
        await expect(risk.getByText(PORTFOLIO_RUN_A).first()).toBeVisible();

        // 3) 风控与异常清单。
        await expect(risk.getByText('风控与异常清单')).toBeVisible();
        await expect(risk.getByText('未处理告警 run（1）')).toBeVisible();
        await expect(risk.getByText('FAILED / CANCELLED run（共 0）')).toBeVisible();

        // 4) 无交易 / 数据不足清单：无交易 run 与可能原因（runC 状态 CREATED → 尚未启动）。
        await expect(risk.getByText('无交易 / 数据不足清单')).toBeVisible();
        await expect(risk.getByText(PORTFOLIO_RUN_C).first()).toBeVisible();
        await expect(risk.getByText('尚未启动', {exact: true})).toBeVisible();

        // 5) 风险数据质量：缺 equity / 缺 PnL（runC currentEquity/totalPnl 均为 null）。
        await expect(risk.getByText('风险数据质量')).toBeVisible();
        await expect(risk.getByText('缺 equity snapshot（1）')).toBeVisible();
        await expect(risk.getByText('缺 PnL（1）')).toBeVisible();
    });

    test('Paper 风险与回撤驾驶舱无 run 时展示空态且不伪造风险数值', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {portfolioSummary: emptyPortfolioSummary});
        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        const risk = page.getByRole('region', {name: 'Paper 风险与回撤驾驶舱'});
        await expect(risk).toBeVisible();
        await expect(risk.getByText('暂无 Paper 风险数据，创建并运行 Paper run 后自动汇总风险与回撤。')).toBeVisible();
        await expect(risk.getByText('数据不足，不展示回撤 / 风险数值')).toBeVisible();
        await expect(risk.getByText('该风险看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易风险。')).toBeVisible();
    });

    test('portfolioCurve 缺失时组合曲线卡回退且单 run 回撤口径仍在', async ({page}) => {
        // 有 run 但 portfolioCurve 为 null（模拟旧版本响应 / 数据不足）→ 曲线卡回退提示，不崩溃，单 run 排行保留。
        await seedAuthAndPaperLoopStubs(page, {
            seedRun: true,
            status: 'STOPPED',
            portfolioSummary: {...defaultPortfolioSummary, portfolioCurve: null},
        });
        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        const risk = page.getByRole('region', {name: 'Paper 风险与回撤驾驶舱'});
        await expect(risk).toBeVisible();

        // 组合曲线卡回退文案（不伪造组合回撤）。
        await expect(risk.getByText('组合资金曲线与回撤', {exact: true})).toBeVisible();
        await expect(risk.getByText('组合 equity curve 暂不可用（数据不足或旧版本响应），回退按单 run 最大回撤统计口径展示。')).toBeVisible();
        await expect(risk.getByText('数据不足，不展示组合时间序列回撤')).toBeVisible();

        // 单 run 回撤口径仍在，风险总览不受影响。
        await expect(risk.getByText('回撤分析')).toBeVisible();
        await expect(risk.locator('.nq-metric-card', {hasText: '最大单 run 回撤'}).locator('.nq-metric-card__value')).toContainText('-25.00%');
        await expect(risk.getByText('该风险看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易风险。')).toBeVisible();
    });

    test('Paper 策略表现排行展示榜单概览、策略/发布排行表与风险调整分', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {seedRun: true, status: 'STOPPED', portfolioSummary: rankingPortfolioSummary});
        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        const ranking = page.getByRole('region', {name: 'Paper 策略表现排行'});
        await expect(ranking).toBeVisible();

        // Paper-only / 排名口径说明。
        await expect(ranking.getByText('该策略排行仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。')).toBeVisible();
        await expect(ranking.getByText('风险调整分为 Paper 内部排序分，仅用于模拟结果横向比较，不代表真实投资评级。', {exact: false})).toBeVisible();

        // 1) 榜单概览（footer 为对应 strategyVersionId）。
        await expect(ranking.locator('.nq-metric-card', {hasText: '收益最高'})).toContainText('sv-rank-highrisk');
        await expect(ranking.locator('.nq-metric-card', {hasText: '收益最高'}).locator('.nq-metric-card__value')).toContainText('+50.00%');
        await expect(ranking.locator('.nq-metric-card', {hasText: '风险调整后最高'})).toContainText('sv-rank-good');
        await expect(ranking.locator('.nq-metric-card', {hasText: '风险调整后最高'}).locator('.nq-metric-card__value')).toContainText('+0.2500');
        await expect(ranking.locator('.nq-metric-card', {hasText: '回撤最大'})).toContainText('sv-rank-highrisk');
        await expect(ranking.locator('.nq-metric-card', {hasText: '风控拦截最多'})).toContainText('sv-rank-weak');
        // Loop-17：无交易最多 / 数据不足最多 / 异常终态最多 基于后端 group 精确计数。
        await expect(ranking.locator('.nq-metric-card', {hasText: '无交易最多'})).toContainText('sv-rank-weak');
        await expect(ranking.locator('.nq-metric-card', {hasText: '无交易最多'}).locator('.nq-metric-card__value')).toHaveText('2');
        await expect(ranking.locator('.nq-metric-card', {hasText: '数据不足最多'})).toContainText('sv-rank-weak');
        await expect(ranking.locator('.nq-metric-card', {hasText: '异常终态最多'})).toContainText('sv-rank-weak');

        // 2) Strategy Version 排行表：三组策略 + 风险调整分 / 无交易 / 异常终态列。
        await expect(ranking.getByText('Strategy Version 排行（按风险调整分）')).toBeVisible();
        await expect(ranking.getByRole('columnheader', {name: '风险调整分'}).first()).toBeVisible();
        await expect(ranking.getByRole('columnheader', {name: '无交易'}).first()).toBeVisible();
        await expect(ranking.getByRole('columnheader', {name: '异常终态'}).first()).toBeVisible();
        await expect(ranking.getByText('sv-rank-good').first()).toBeVisible();
        await expect(ranking.getByText('sv-rank-highrisk').first()).toBeVisible();
        await expect(ranking.getByText('sv-rank-weak').first()).toBeVisible();
        // 风险调整分缺失（totalReturn/回撤缺失）→ 该行展示「数据不足」。
        await expect(ranking.locator('tr[data-row-key="sv-rank-weak"]')).toContainText('数据不足');

        // 3) Publish 排行表：两组发布，同样含无交易 / 异常终态列。
        await expect(ranking.getByText('Publish 排行（按风险调整分）')).toBeVisible();
        await expect(ranking.getByText('pub-rank-1').first()).toBeVisible();
        await expect(ranking.getByText('pub-rank-2').first()).toBeVisible();
    });

    test('Paper 策略表现排行在旧后端缺 group 计数字段时回退派生且不崩', async ({page}) => {
        // group 缺 Loop-17 精确计数 → 前端回退 highlights/dataQuality 派生 noTrade/dataInsufficient（异常终态无法派生记 0）。
        await seedAuthAndPaperLoopStubs(page, {seedRun: true, status: 'STOPPED', portfolioSummary: rankingLegacySummary});
        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        const ranking = page.getByRole('region', {name: 'Paper 策略表现排行'});
        await expect(ranking).toBeVisible();
        // 回退派生：sv-rank-weak 的 dataInsufficient 仍由 dataQuality.dataInsufficientRuns（2 条）推导，不崩溃。
        await expect(ranking.locator('.nq-metric-card', {hasText: '数据不足最多'})).toContainText('sv-rank-weak');
        await expect(ranking.locator('.nq-metric-card', {hasText: '无交易最多'})).toContainText('sv-rank-weak');
        await expect(ranking.getByText('sv-rank-weak').first()).toBeVisible();
        // 异常终态无法从 highlights 派生 → 概览回退「暂无异常终态」。
        await expect(ranking.locator('.nq-metric-card', {hasText: '异常终态最多'})).toContainText('暂无异常终态');
        await expect(ranking.getByText('该策略排行仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。')).toBeVisible();
    });

    test('Paper 策略表现排行无分组时展示空态且不做排行', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page, {portfolioSummary: emptyPortfolioSummary});
        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        const ranking = page.getByRole('region', {name: 'Paper 策略表现排行'});
        await expect(ranking).toBeVisible();
        await expect(ranking.getByText('暂无可分组的 Paper 策略 / 发布数据，创建并运行 Paper run 后自动汇总排行。')).toBeVisible();
        await expect(ranking.getByText('数据不足，不做排行')).toBeVisible();
        await expect(ranking.getByText('该策略排行仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。')).toBeVisible();
    });
});
