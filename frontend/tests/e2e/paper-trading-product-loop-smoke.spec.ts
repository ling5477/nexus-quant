import {expect, test, type Page, type Route} from 'playwright/test';

const PAPER_RUN_ID = 'paper-loop-1';

const paperRun = {
    paperRunId: PAPER_RUN_ID,
    publishId: 'publish-loop-1',
    strategyVersionId: 'strategy-version-loop-1',
    status: 'RUNNING',
    tradeEnv: 'SIM',
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    intervalCode: '1m',
    startedAt: '2026-06-24T01:00:00Z',
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

async function seedAuthAndPaperLoopStubs(page: Page): Promise<void> {
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

    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/runs(?:\?.*)?$/, (route: Route) => route.fulfill({
        status: 200,
        json: [paperRun],
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}`, (route: Route) => route.fulfill({
        status: 200,
        json: paperRun,
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/orders`, (route: Route) => route.fulfill({
        status: 200,
        json: [{
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
        }],
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/trades`, (route: Route) => route.fulfill({
        status: 200,
        json: [{
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
        }],
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/positions`, (route: Route) => route.fulfill({
        status: 200,
        json: [{
            paperPositionId: 'paper-position-1',
            paperRunId: PAPER_RUN_ID,
            symbol: 'BTC-USDT',
            quantity: '1',
            avgPrice: '65000',
            unrealizedPnl: '125',
            realizedPnl: '-65',
            updatedAt: '2026-06-24T01:02:00Z',
            createdAt: '2026-06-24T01:01:31Z',
        }],
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/equity-curve`, (route: Route) => route.fulfill({
        status: 200,
        json: [{
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
        }],
    }));
    await page.route(`**/api/paper-trading/runs/${PAPER_RUN_ID}/risk-results`, (route: Route) => route.fulfill({
        status: 200,
        json: [{
            riskResultId: 'risk-loop-1',
            paperRunId: PAPER_RUN_ID,
            checkType: 'BASIC_HEALTH_CHECK',
            status: 'PASSED',
            severity: 'LOW',
            message: 'paper loop ok',
            inputSnapshotJson: '{}',
            resultSnapshotJson: '{}',
            createdAt: '2026-06-24T01:03:00Z',
        }],
    }));
}

test.describe('paper trading product loop panel', () => {
    test('聚合展示订单、成交、持仓、PnL 与风控状态', async ({page}) => {
        await seedAuthAndPaperLoopStubs(page);

        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        await page.getByRole('button', {name: /查\s*询/}).click();
        const row = page.locator('tr').filter({hasText: PAPER_RUN_ID});
        await expect(row).toBeVisible();
        await row.getByRole('link', {name: '查看详情'}).or(row.getByRole('button', {name: '查看详情'})).click();

        const detail = page.getByRole('region', {name: 'Paper Trading 详情'});
        await expect(detail.getByText('Paper 执行闭环')).toBeVisible();
        await expect(detail.getByText('订单 → 成交 → 持仓 / PnL → 风控')).toBeVisible();
        await expect(detail.getByText('只读聚合当前 Paper run 的执行事实。')).toBeVisible();
        await expect(detail.getByText('FILLED').first()).toBeVisible();
        await expect(detail.getByText('BASIC_HEALTH_CHECK · LOW')).toBeVisible();
        await expect(detail.getByText('PASSED').first()).toBeVisible();

        const bodyText = await detail.innerText();
        expect(bodyText).toContain('订单事实');
        expect(bodyText).toContain('成交事实');
        expect(bodyText).toContain('持仓事实');
        expect(bodyText).toContain('净 PnL');
        expect(bodyText).toContain('60.00');
    });
});
