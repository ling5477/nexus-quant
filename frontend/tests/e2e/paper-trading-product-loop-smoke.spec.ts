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
};

async function seedAuthAndPaperLoopStubs(page: Page, options: PaperLoopStubOptions = {}): Promise<{setRunStatus: (status: string) => void}> {
    let currentRun = {
        ...paperRun,
        status: options.status ?? paperRun.status,
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
        await expect(detail.getByText('1 分钟 0 秒')).toBeVisible();
        await expect(detail.getByText('订单数').first()).toBeVisible();
        await expect(detail.getByText('成交数').first()).toBeVisible();
        await expect(detail.getByText('持仓数').first()).toBeVisible();
        await expect(detail.getByText('风控结果').first()).toBeVisible();
        await expect(detail.getByText('正常完成')).toBeVisible();
        await expect(detail.getByText('该复盘只基于当前 Paper run 的查询结果，用于判断模拟运行质量，不代表真实交易能力。')).toBeVisible();
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

        const bodyText = await detail.innerText();
        expect(bodyText).toContain('订单事实');
        expect(bodyText).toContain('成交事实');
        expect(bodyText).toContain('持仓事实');
        expect(bodyText).toContain('净 PnL');
        expect(bodyText).toContain('60.00');
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
        });

        const detail = await openPaperRunDetail(page);

        // 异常原因聚合区域：类型、严重程度、建议检查对象、Paper-only / 不触发 LIVE 文案。
        await expect(detail.getByText('异常原因聚合')).toBeVisible();
        await expect(detail.getByText('该诊断仅基于当前 Paper run 的查询结果，不代表真实交易能力，不触发 LIVE 或真实交易所。')).toBeVisible();

        // BLOCKING：风控拦截（'风控拦截' 同时出现在运行结果复盘结论中，用 .first 规避 strict 多匹配）。
        await expect(detail.getByText('RISK_BLOCKED')).toBeVisible();
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
});
