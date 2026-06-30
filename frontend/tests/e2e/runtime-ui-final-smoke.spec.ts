import {expect, test, type Page, type Route} from 'playwright/test';

interface ReadinessItem {
    venue: string;
    capability: string;
    status: string;
    allowed: boolean;
    liveAuthorized: boolean;
    reasons: string[];
    message: string;
}

const simAccount = {
    exchangeAccountId: 101,
    legacyAccountId: 1,
    exchangeCode: 'BINANCE',
    tradeEnv: 'SIM',
    accountAlias: 'runtime-final-smoke-sim',
    externalAccountRef: null,
    isDefault: true,
    status: 'ENABLED',
};

const CAPABILITIES = ['PUBLIC_MARKETDATA', 'PLACE_ORDER', 'CANCEL_ORDER', 'PERMISSION_PROBE'];

function buildReadinessItems(): ReadinessItem[] {
    const items: ReadinessItem[] = [];

    for (const venue of ['NOOP', 'PAPER', 'SIM']) {
        for (const capability of CAPABILITIES) {
            items.push({
                venue,
                capability,
                status: 'NO_REAL',
                allowed: false,
                liveAuthorized: false,
                reasons: ['NO_REAL_DISABLED', 'READY_FOR_PAPER_ONLY'],
                message: 'paper-only / no-real runtime; not real authorization',
            });
        }
    }

    for (const venue of ['OKX', 'BINANCE']) {
        for (const capability of CAPABILITIES) {
            items.push({
                venue,
                capability,
                status: 'NOT_READY',
                allowed: false,
                liveAuthorized: false,
                reasons: capability === 'PERMISSION_PROBE'
                    ? ['PERMISSION_PROBE_DISABLED', 'SKIPPED', 'CREDENTIAL_UNCONFIGURED', 'LIVE_NOT_AUTHORIZED']
                    : ['ENDPOINT_DISABLED_SENTINEL', 'CREDENTIAL_UNCONFIGURED', 'LIVE_NOT_AUTHORIZED'],
                message: 'real provider not implemented / LIVE disabled',
            });
        }
    }

    for (const venue of ['FAKE', 'STUB', 'FUTURE_REAL']) {
        items.push({
            venue,
            capability: 'PUBLIC_MARKETDATA',
            status: 'NOT_READY',
            allowed: false,
            liveAuthorized: false,
            reasons: [venue === 'FUTURE_REAL' ? 'FUTURE_REAL_DISABLED' : venue, 'LIVE_NOT_AUTHORIZED'],
            message: `${venue} is not live-ready`,
        });
    }

    return items;
}

async function seedRuntimeFinalSmokeStubs(page: Page): Promise<void> {
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'runtime-ui-final-smoke-session',
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
            defaultExchangeAccountId: simAccount.exchangeAccountId,
            defaultExchangeCode: simAccount.exchangeCode,
            defaultTradeEnv: simAccount.tradeEnv,
            defaultAccountAlias: simAccount.accountAlias,
        },
    }));

    await page.route('**/api/exchange-accounts', (route: Route) => route.fulfill({
        status: 200,
        json: [simAccount],
    }));

    await page.route('**/api/adapters/readiness', (route: Route) => route.fulfill({
        status: 200,
        json: {generatedAt: '2026-06-30T12:00:00Z', items: buildReadinessItems()},
    }));

    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/runs(?:\?.*)?$/, (route: Route) => route.fulfill({
        status: 200,
        json: [],
    }));

    await page.route(/^https?:\/\/[^/]+\/api\/trading\/orders(?:\?.*)?$/, (route: Route) => route.fulfill({
        status: 200,
        json: {items: [], page: 0, size: 20, total: 0},
    }));
}

function formItem(page: Page, label: string) {
    return page.locator('.page-section').filter({hasText: '查询条件'}).locator('.ant-form-item').filter({hasText: label});
}

async function expectSelectValue(page: Page, label: string, value: string): Promise<void> {
    await expect(formItem(page, label).locator('.ant-select-selection-item')).toHaveText(value);
}

async function expectNoFalseReadyCopy(page: Page): Promise<void> {
    await expect(page.getByText(/LIVE ready/i)).toHaveCount(0);
    await expect(page.getByText(/Permission probe verified/i)).toHaveCount(0);
    await expect(page.getByText('LIVE 已授权')).toHaveCount(0);
}

test.describe('runtime guarded UI final smoke', () => {
    test('covers dashboard, runtime, marketdata, paper and trading guards without write endpoint calls', async ({page}) => {
        const apiWrites: string[] = [];
        const apiRequests: string[] = [];
        const websocketUrls: string[] = [];
        const externalExchangeRequests: string[] = [];

        page.on('request', (request) => {
            const url = request.url();
            const entry = `${request.method()} ${url}`;

            if (/okx|binance|bybit|gate|coinbase|kraken/i.test(new URL(url).hostname)) {
                externalExchangeRequests.push(entry);
            }
            if (!url.includes('/api/')) {
                return;
            }
            apiRequests.push(entry);
            if (request.method() !== 'GET') {
                apiWrites.push(entry);
            }
        });
        page.on('websocket', (websocket) => websocketUrls.push(websocket.url()));

        await seedRuntimeFinalSmokeStubs(page);

        await page.goto('/dashboard');
        await expect(page).toHaveURL(/\/dashboard$/);
        await expect(page.getByRole('heading', {name: '控制台总览'})).toBeVisible();
        const dashboardCard = page.getByTestId('dashboard-runtime-readiness-card');
        await expect(dashboardCard).toContainText('Runtime Readiness');
        await expect(dashboardCard).toContainText('Runtime guarded: LIVE disabled');
        await expect(dashboardCard).toContainText('Real provider');
        await expect(dashboardCard).toContainText('Not implemented');
        await expect(dashboardCard).toContainText('Simulated only');
        await expect(dashboardCard).toContainText('Skipped / NoReal');
        await expect(dashboardCard).toContainText('NoReal/Fake/Stub/FutureReal not live-ready.');
        await expect(dashboardCard).toContainText('Permission probe SKIPPED / disabled is not verified.');
        await expect(dashboardCard.getByRole('link', {name: 'View Runtime Readiness'})).toHaveAttribute('href', '/runtime/readiness');
        await expect(dashboardCard.getByRole('link', {name: 'View MarketData Readiness'})).toHaveAttribute('href', '/marketdata');
        await expectNoFalseReadyCopy(page);

        await dashboardCard.getByRole('link', {name: 'View MarketData Readiness'}).click();
        await expect(page).toHaveURL(/\/marketdata$/);
        await expect(page.getByRole('heading', {name: 'Marketdata'})).toBeVisible();
        await expect(page.getByTestId('marketdata-kline-readiness-view')).toBeVisible();
        await expect(page.getByTestId('marketdata-quality-readiness-view')).toBeVisible();

        await page.goto('/dashboard');
        await page.getByTestId('dashboard-runtime-readiness-card').getByRole('link', {name: 'View Runtime Readiness'}).click();
        await expect(page).toHaveURL(/\/runtime\/readiness$/);
        await expect(page.getByRole('heading', {name: 'Runtime Readiness Overview'})).toBeVisible();
        await expect(page.getByText('LIVE disabled').first()).toBeVisible();
        await expect(page.getByText('RealClient / real provider / real exchange adapter not implemented')).toBeVisible();
        await expect(page.getByText('READY_FOR_PAPER_ONLY').first()).toBeVisible();
        await expect(page.getByText('PERMISSION_PROBE_DISABLED / SKIPPED').first()).toBeVisible();
        await expect(page.getByText('NoReal / Fake / Stub / FutureReal').first()).toBeVisible();
        await expectNoFalseReadyCopy(page);

        await page.getByRole('link', {name: 'View MarketData readiness'}).click();
        await expect(page).toHaveURL(/\/marketdata\?exchangeCode=BINANCE&marketType=SPOT&symbol=BTC-USDT&interval=1m$/);
        await expect(page.getByRole('heading', {name: 'Marketdata'})).toBeVisible();
        await expect(page.getByTestId('marketdata-runtime-deep-link')).toContainText('Runtime readiness context applied');
        await expect(page.getByTestId('marketdata-runtime-deep-link')).toContainText('不会自动触发采集');
        await expectSelectValue(page, '交易所', 'BINANCE');
        await expectSelectValue(page, '市场', 'SPOT');
        await expectSelectValue(page, '交易对', 'BTC-USDT');
        await expectSelectValue(page, '周期', '1m');

        await page.goto('/paper-trading');
        await expect(page).toHaveURL(/\/paper-trading\/runs$/);
        const paperBanner = page.getByTestId('paper-real-boundary-banner');
        await expect(paperBanner).toContainText('Paper-only boundary');
        await expect(paperBanner).toContainText('Paper Trading is simulated.');
        await expect(paperBanner).toContainText('Paper order ≠ real order.');
        await expect(paperBanner).toContainText('Paper risk pass ≠ LIVE authorization.');
        await expect(paperBanner).toContainText('permission probe SKIPPED do not authorize LIVE trading.');
        await expectNoFalseReadyCopy(page);

        await page.goto('/trading');
        await expect(page).toHaveURL(/\/trading$/);
        const tradingBanner = page.getByTestId('runtime-guarded-live-disabled-banner');
        await expect(tradingBanner).toContainText('Runtime guarded: LIVE disabled');
        await expect(tradingBanner).toContainText('LIVE disabled.');
        await expect(tradingBanner).toContainText('Real provider not implemented.');
        await expect(tradingBanner).toContainText('NoReal/Fake/Stub/FutureReal not live-ready.');
        await expect(tradingBanner).toContainText('Permission probe SKIPPED / disabled is not verified.');
        await expectNoFalseReadyCopy(page);

        expect(apiWrites, 'runtime guarded UI final smoke must not call write endpoints').toEqual([]);
        expect(apiRequests.some((entry) => entry.includes('permission-probe')), 'must not call permission probe endpoints').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('ingestions/run-once') || entry.includes('ingestion-jobs/run-once')), 'must not trigger ingestion run-once').toBeFalsy();
        expect(apiRequests.some((entry) => !entry.startsWith('GET ') && /order|cancel/i.test(entry)), 'must not submit order/cancel writes').toBeFalsy();
        expect(apiRequests.some((entry) => !entry.startsWith('GET ') && /transfer|withdraw/i.test(entry)), 'must not submit transfer/withdraw writes').toBeFalsy();
        const appWebsocketUrls = websocketUrls.filter((url) => !/^ws:\/\/(?:127\.0\.0\.1|localhost):\d+\/\?token=/.test(url));
        expect(appWebsocketUrls, 'runtime guarded UI final smoke must not open application WebSocket').toEqual([]);
        expect(externalExchangeRequests, 'runtime guarded UI final smoke must not call external exchange hosts').toEqual([]);
    });
});
