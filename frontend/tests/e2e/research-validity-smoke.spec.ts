import {expect, test} from 'playwright/test';

// 页面契约 fixture 验证展示与缺失状态；数值及持久化由 PG16 后端测试独立证明。
test('Evaluation 和 Backtest 正式页面展示同一研究报告及历史缺失状态', async ({page}) => {
    const segment = {status: 'AVAILABLE', startTime: '2026-01-01T00:00:00Z', endTime: '2026-01-01T00:06:59Z',
        barCount: 7, startingEquity: 100, finalEquity: 101, strategyReturn: 0.01, netPnl: 1,
        maxDrawdown: 0.2, maxDrawdownRate: 0.002, tradeCount: 1, fee: 0.1, slippage: 0.2};
    let validity: unknown = {validationStatus: 'AVAILABLE', reason: null,
        splitPolicy: 'CHRONOLOGICAL_CLOSED_BARS_70_30_FLOOR_V1', segmentEquityPolicy: 'CONTINUOUS_RUN_PREVIOUS_BAR_EQUITY',
        identity: {backtestRunId: 'run', datasetId: 'dataset-research', strategyVersionId: 'version-research',
            barContentSha256: 'digest', strategyChecksum: 'checksum', symbol: 'BTC-USDT', interval: '1m'},
        assumptions: {initialCapital: 100, feeRate: 0.001, slippageBps: 10,
            executionTimingPolicy: 'NEXT_OPEN_STRICTLY_AFTER_AVAILABLE_AT', finalEquityPolicy: 'MARK_TO_MARKET'},
        full: {...segment, barCount: 10, endTime: '2026-01-01T00:09:59Z'}, inSample: segment,
        outOfSample: {...segment, startTime: '2026-01-01T00:07:00Z', endTime: '2026-01-01T00:09:59Z', barCount: 3, strategyReturn: -0.02},
        benchmark: {status: 'AVAILABLE', benchmarkType: 'BUY_AND_HOLD', valuationPolicy: 'MARK_TO_MARKET',
            entryTime: '2026-01-01T00:01:00Z', endTime: '2026-01-01T00:09:59Z', entryPrice: 100.1, endPrice: 103,
            finalEquity: 103, benchmarkReturn: 0.03, fee: 0.1, slippage: 0.2}, strategyVsBenchmarkDifference: -0.02};
    const report = () => ({evalReportId: 'evaluation', backtestRunId: 'run', evaluationStatus: 'SUCCEEDED',
        evaluatedAt: '2026-01-02T00:00:00Z', totalReturn: 0.01, researchValidity: validity});
    await page.route('**/api/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (!path.startsWith('/api/')) return route.fallback();
        const reply = (json: unknown) => route.fulfill({json});
        if (path === '/api/auth/me') return reply({userId: 1, username: 'fixture', roles: ['ADMIN', 'OPERATOR', 'VIEWER'], authenticated: true});
        if (path === '/api/evaluations') return reply([report()]);
        if (path === '/api/evaluations/evaluation' || path === '/api/backtest-runs/run/evaluation') return reply(report());
        if (path === '/api/backtest-configs') return reply([{backtestConfigId: 'config', researchConfigId: 'research', name: 'research config'}]);
        if (path === '/api/backtest-configs/config') return reply({backtestConfigId: 'config', researchConfigId: 'research', name: 'research config'});
        if (path === '/api/backtest-runs' && route.request().method() === 'POST') return reply({backtestRunId: 'run', status: 'SUCCEEDED'});
        if (path === '/api/backtest-runs/run') return reply({backtestRunId: 'run', backtestConfigId: 'config', status: 'SUCCEEDED'});
        if (path === '/api/exchange-accounts' || path === '/api/marketdata/datasets') return reply([]);
        return reply({});
    });
    await page.addInitScript(() => localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
        accessToken: 'research-browser-fixture', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'fixture', roles: ['ADMIN', 'OPERATOR', 'VIEWER'],
    })));
    await page.goto('/evaluations');
    await page.getByRole('button', {name: /查\s*询/}).click();
    await page.locator('tr').filter({hasText: 'evaluation'}).getByRole('button', {name: '查看详情'}).click();
    let panel = page.getByTestId('research-validity');
    await expect(panel.getByText('version-research', {exact: true})).toBeVisible();
    await expect(panel.getByText('dataset-research', {exact: true})).toBeVisible();
    await expect(panel.getByText('3%', {exact: true})).toBeVisible();
    await expect(panel.getByText('-2%', {exact: true}).first()).toBeVisible();
    await expect(panel.getByText(/不是随机交叉验证/)).toBeVisible();
    await page.screenshot({path: test.info().outputPath('research-validity-desktop.png'), fullPage: true});
    await page.goto('/backtests');
    await page.getByRole('button', {name: /查\s*询/}).click();
    await page.locator('tr').filter({hasText: 'config'}).getByRole('button', {name: '查看详情'}).click();
    await page.getByRole('button', {name: '创建回测运行'}).click();
    panel = page.getByTestId('research-validity');
    await expect(panel.getByText('3%', {exact: true})).toBeVisible();
    await expect(panel.getByText('样本外 (OOS)', {exact: true})).toBeVisible();
    validity = {validationStatus: 'NOT_AVAILABLE', reason: 'LEGACY_REPORT_WITHOUT_RESEARCH_VALIDITY',
        splitPolicy: 'CHRONOLOGICAL_CLOSED_BARS_70_30_FLOOR_V1', full: null, inSample: null, outOfSample: null, benchmark: null};
    await page.goto('/evaluations');
    await page.getByRole('button', {name: /查\s*询/}).click();
    await page.locator('tr').filter({hasText: 'evaluation'}).getByRole('button', {name: '查看详情'}).click();
    panel = page.getByTestId('research-validity');
    await expect(panel.getByText('不可用', {exact: true}).first()).toBeVisible();
    await expect(panel.getByText('0%', {exact: true})).toHaveCount(0);
    await expect(panel.getByText('—', {exact: true}).first()).toBeVisible();
    await page.setViewportSize({width: 390, height: 844});
    await expect(panel.getByText(/不是随机交叉验证/)).toBeVisible();
    await page.screenshot({path: test.info().outputPath('research-validity-mobile.png'), fullPage: true});
});
