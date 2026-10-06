import {expect, test} from 'playwright/test';

// 隔离 PG16 集成入口建立样本并启动正式 HTTP API；该用例不拦截任何 API。
test('同一 Continuous SIM run 在 Paper / Strategy SIM / Trading 展示同一 canonical 事实', async ({page, request}) => {
    const runId = process.env.C3_FILLED_RUN;
    expect(runId, '必须由隔离 PostgreSQL 16 样本入口提供').toBeTruthy();
    const login = await request.post('/api/auth/login', {data: {username: 'c3-disposable', password: 'c3-disposable-password'}});
    expect(login.ok()).toBeTruthy();
    const session = await login.json();
    const headers = {Authorization: `Bearer ${session.accessToken}`};
    const read = async (path: string) => {
        const response = await request.get(path, {headers});
        expect(response.ok(), path).toBeTruthy();
        return response.json();
    };
    await page.addInitScript((value) => localStorage.setItem('nexus-quant.console.auth', JSON.stringify(value)), session);
    const facts = await read(`/api/paper-trading/strategy-sim/runs/${runId}/facts`);
    const decisions = await read(`/api/paper-trading/strategy-sim/runs/${runId}/decisions`);
    const run = await read(`/api/paper-trading/runs/${runId}`);
    expect(run.canonicalAccountId).toBe(facts.canonicalAccountId);
    const orders = await read(`/api/paper-trading/runs/${runId}/orders`);
    const trades = await read(`/api/paper-trading/runs/${runId}/trades`);
    const positions = await read(`/api/paper-trading/runs/${runId}/positions`);
    expect(orders[0].paperOrderId).toBe(facts.orders[0].order_id);
    expect(trades[0].paperTradeId).toBe(facts.trades[0].trade_id);
    expect(trades[0].paperOrderId).toBe(facts.orders[0].order_id);
    for (const key of ['price', 'side']) expect(trades[0][key]).toBe(facts.trades[0][key]);
    expect(trades[0].quantity).toBe(facts.trades[0].qty);
    expect(positions[0].quantity).toBe(facts.positionQuantity);
    expect(facts.positions[0].qty).toBe(positions[0].quantity);
    expect(facts.positions[0].avg_price).toBe(positions[0].avgPrice);
    const tradingOrder = await read(`/api/trading/orders/${orders[0].paperOrderId}`);
    const tradingTrade = await read(`/api/trading/orders/${orders[0].paperOrderId}/trade`);
    expect(tradingOrder.accountId).toBe(facts.canonicalAccountId);
    expect(tradingOrder.quantity).toBe(facts.orders[0].qty);
    expect(tradingOrder.status).toBe(facts.orders[0].status);
    expect(tradingTrade.tradeId).toBe(trades[0].paperTradeId);
    expect(tradingTrade.price).toBe(trades[0].price);
    expect(tradingTrade.quantity).toBe(trades[0].quantity);
    await page.goto(`/paper-trading/runs?paperRunId=${runId}`);
    let panel = page.getByTestId('canonical-sim-facts');
    await expect(panel.getByText(facts.trades[0].trade_id, {exact: true}).first()).toBeVisible();
    await expect(panel.getByText('STOPPED', {exact: true}).first()).toBeVisible();
    await expect(panel.getByText(/CONTINUOUS_SIM_POLL: 已禁用/)).toBeVisible();
    const paperText = await panel.innerText();
    await panel.getByRole('link', {name: '查看交易工作台'}).click();
    await expect(page).toHaveURL(new RegExp(`paperRunId=${runId}`));
    panel = page.getByTestId('canonical-sim-facts');
    await expect(panel.getByText(facts.trades[0].trade_id, {exact: true}).first()).toBeVisible();
    expect(await panel.innerText()).toBe(paperText);
    await page.reload();
    await expect(panel.getByText(runId!, {exact: true}).first()).toBeVisible();
    await expect(panel.getByText('STOPPED', {exact: true}).first()).toBeVisible();
    await page.screenshot({path: test.info().outputPath('canonical-filled-real.png'), fullPage: true});
    await page.goto(`/trading?paperRunId=${process.env.C3_NO_SIGNAL_RUN}`);
    await expect(page.getByTestId('canonical-sim-facts').getByText(/NO_SIGNAL/)).toBeVisible();
    await expect(page.getByText('尚未产生', {exact: false})).toBeVisible();
    await page.goto(`/trading?paperRunId=${process.env.C3_REJECTED_RUN}`);
    panel = page.getByTestId('canonical-sim-facts');
    await expect(panel.getByText(/RISK_REJECTED/).first()).toBeVisible();
    await expect(panel.getByText(/ACCOUNT_TRADING_DISABLED/).first()).toBeVisible();
    await page.screenshot({path: test.info().outputPath('canonical-risk-real.png'), fullPage: true});
    await page.goto('/system/scheduler');
    await expect(page.getByText('CONTINUOUS_SIM_POLL', {exact: true})).toBeVisible();
    await expect(page.getByText('PAPER_MATCHING', {exact: true})).toBeVisible();
    await page.screenshot({path: test.info().outputPath('canonical-scheduler-real.png'), fullPage: true});
    console.log('C3_CANONICAL_MATRIX', JSON.stringify({paperRunId:runId, canonicalAccountId:facts.canonicalAccountId,
        strategyVersionId:facts.strategyVersionId, decisionId:decisions.find((item: {orderId: string}) => item.orderId === orders[0].paperOrderId)?.decisionId,
        noSignalRun:process.env.C3_NO_SIGNAL_RUN, rejectedRun:process.env.C3_REJECTED_RUN, orderId:orders[0].paperOrderId, tradeId:trades[0].paperTradeId,
        exactValues:facts.exactValues, markAsOf:facts.markAsOf, ledgerAsOf:facts.ledgerAsOf, positionAsOf:facts.positionAsOf}));
});
