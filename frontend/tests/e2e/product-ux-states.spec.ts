import {expect, test} from 'playwright/test';

test('精度、缺失值、调度异常与切换 run 的状态隔离', async ({page}) => {
    page.on('pageerror', error => console.log('C3_PAGE_ERROR', error.message));
    const rows = (id: string) => ({paperRunId: id, publishId: `publish-${id}`, strategyVersionId: `version-${id}`,
        canonicalAccountId: id === 'legacy' ? null : id === 'run-a' ? 2 : 3, status: 'RUNNING', tradeEnv: 'SIM',
        exchangeCode: 'OKX', marketType: 'SPOT', symbol: 'BTC-USDT', intervalCode: '1h',
        datasetSnapshotJson: JSON.stringify({datasetId: `dataset-${id}`}), createdAt: '2026-10-01T00:00:00Z'});
    let readsUnavailable = false;
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        if (!path.startsWith('/api/')) return route.fallback();
        if (readsUnavailable && (path.endsWith('/continuous') || /^\/api\/paper-trading\/runs\/run-a$/.test(path)))
            return route.fulfill({status: 503, json: {code: 'UNAVAILABLE', message: 'unavailable'}});
        const reply = (json: unknown) => route.fulfill({json});
        if (path === '/api/auth/me') return reply({userId: 1, username: 'fixture', authenticated: true, roles: ['ADMIN', 'OPERATOR', 'VIEWER']});
        if (path === '/api/exchange-accounts') return reply([]);
        if (path === '/api/scheduler/jobs') return reply([{jobKey: 'CONTINUOUS_SIM_POLL', enabled: false,
            lastStatus: 'FAILED', lastErrorCode: 'FEED_UNAVAILABLE', fixedDelayMs: 30000}]);
        const match = /strategy-sim\/runs\/(run-[ab])\/(facts|decisions|continuous)/.exec(path);
        if (match) {
            const [, id, kind] = match;
            if (id === 'run-b') await new Promise(resolve => setTimeout(resolve, 500));
            if (kind === 'decisions') return reply([{decisionId: `decision-${id}`, paperRunId: id, status: 'NO_SIGNAL',
                reason: 'ALREADY_AT_TARGET', signalOpenTime: '2026-10-01T00:00:00Z', side: null, quantity: null, orderId: null}]);
            if (kind === 'continuous') return reply({paperRunId: id, status: 'STOPPED', blockReason: 'STALE_DATA',
                lastProcessedBar: '2026-10-01T00:00:00Z', lastObservedBar: '2026-10-01T00:00:00Z', lastPollAt: null});
            return reply({...rows(id), inputSha256: `input-${id}`, cash: null, positionQuantity: '0.00119354',
                markPrice: null, equity: null, pnl: null, markAsOf: null, ledgerAsOf: null, positionAsOf: null,
                orders: [], trades: [{trade_id: `trade-${id}`, order_id: `order-${id}`, qty: '0.00119354',
                    exact_qty: '0.00119354', price: '83000.12345678', symbol: 'BTC-USDT', side: 'BUY'}], ledgerEntries: []});
        }
        const runMatch = /^\/api\/paper-trading\/runs\/(run-[ab]|legacy)$/.exec(path);
        if (runMatch) return reply(rows(runMatch[1]));
        return reply(path.endsWith('/summary') ? {} : []);
    });
    await page.addInitScript(() => localStorage.setItem('nexus-quant.console.auth', JSON.stringify({accessToken: 'fixture',
        tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'fixture', roles: ['ADMIN', 'OPERATOR', 'VIEWER']})));
    await page.goto('/trading?paperRunId=run-a');
    let panel = page.getByTestId('canonical-sim-facts');
    await expect(panel.getByText('0.00119354', {exact: false}).first()).toBeVisible();
    await expect(panel.getByText(/0.00 BTC/)).toHaveCount(0);
    await expect(panel.getByText(/ALREADY_AT_TARGET/)).toBeVisible();
    await expect(panel.getByText(/FEED_UNAVAILABLE/)).toBeVisible();
    await expect(panel.getByText(/已禁用/)).toBeVisible();
    await expect(panel.getByText('STOPPED', {exact: true})).toBeVisible();
    await expect(panel.getByText(/STALE_DATA/)).toBeVisible();
    await expect(panel.getByText('- USDT', {exact: false}).first()).toBeVisible();
    await expect(panel.getByText('0 USDT', {exact: false})).toHaveCount(0);
    await page.goto('/trading?paperRunId=run-b');
    panel = page.getByTestId('canonical-sim-facts');
    await expect(panel.getByText('trade-run-a', {exact: true})).toHaveCount(0);
    await expect(panel.getByText('trade-run-b', {exact: true})).toBeVisible();
    await page.setViewportSize({width: 390, height: 844});
    await expect(page.getByRole('button', {name: '展开菜单'})).toBeVisible();
    await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
    await expect(panel.getByText(/ALREADY_AT_TARGET/)).toBeVisible();
    await page.screenshot({path: test.info().outputPath('canonical-mobile.png'), fullPage: true, animations: 'disabled'});
    await page.goto('/paper-trading/runs?paperRunId=run-a');
    panel = page.getByTestId('canonical-sim-facts');
    await expect(panel.getByText('STOPPED', {exact: true})).toBeVisible();
    readsUnavailable = true;
    await page.getByRole('button', {name: '刷新 canonical 事实'}).click();
    await expect(panel.getByText('STOPPED', {exact: true})).toHaveCount(0);
    await expect(panel.getByText('RUNNING', {exact: true})).toHaveCount(0);
    await expect(page.getByRole('button', {name: '恢复连续处理'})).toHaveCount(0);
    await expect(page.getByRole('button', {name: '执行下一策略决策'})).toBeDisabled();
    await page.goto('/paper-trading/runs?paperRunId=legacy');
    await expect(page.getByTestId('canonical-sim-facts')).toHaveCount(0);
    await expect(page.getByText('历史 Paper / Research', {exact: true})).toBeVisible();
    await expect(page.getByRole('button', {name: '创建历史 Research Paper'})).toBeVisible();
});
