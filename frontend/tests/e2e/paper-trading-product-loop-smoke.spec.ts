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
});
