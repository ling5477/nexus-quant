import {expect, test, type Page, type Route} from 'playwright/test';

interface MarketdataBarFixture {
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    openTime: string;
    closeTime: string;
    openPrice: number;
    highPrice: number;
    lowPrice: number;
    closePrice: number;
    volume: number;
    quoteVolume: number;
    tradeCount: number;
    qualityStatus?: string | null;
}

const SAMPLE_READINESS = {
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    instrumentId: 'BTC-USDT',
    symbol: 'BTC-USDT',
    interval: '1m',
    status: 'GAP',
    freshnessStatus: 'STALE',
    sourceHealthStatus: 'GAP',
    sourceHealthReason: 'Local bar sequence or qualityStatus evidence indicates a gap; gapCount=2.',
    qualityStatusSummary: {
        okCount: 2,
        gapSignalCount: 1,
        invalidCount: 0,
        unknownQualityCount: 1,
        statuses: {
            OK: 2,
            GAP_DETECTED: 1,
            UNKNOWN: 1,
        },
    },
    barCount: 3,
    firstBarTime: '2026-06-29T01:00:00Z',
    lastBarTime: '2026-06-29T01:03:59Z',
    expectedBarCount: 5,
    gapCount: 2,
    unknownQualityCount: 1,
    lastSuccessAt: '2026-06-29T01:05:00Z',
    lastFailureAt: null,
    backendSupportLevel: 'NO_MIGRATION_MVP',
    generatedAt: '2026-06-29T01:06:00Z',
};

const SAMPLE_BARS: MarketdataBarFixture[] = [
    {
        exchangeCode: 'BINANCE',
        marketType: 'SPOT',
        symbol: 'BTC-USDT',
        interval: '1m',
        openTime: '2026-06-29T01:00:00Z',
        closeTime: '2026-06-29T01:00:59Z',
        openPrice: 64210,
        highPrice: 64520,
        lowPrice: 64080,
        closePrice: 64480,
        volume: 128.4,
        quoteVolume: 8273184,
        tradeCount: 120,
        qualityStatus: 'OK',
    },
    {
        exchangeCode: 'BINANCE',
        marketType: 'SPOT',
        symbol: 'BTC-USDT',
        interval: '1m',
        openTime: '2026-06-29T01:02:00Z',
        closeTime: '2026-06-29T01:02:59Z',
        openPrice: 64480,
        highPrice: 64840,
        lowPrice: 64320,
        closePrice: 64790,
        volume: 154.7,
        quoteVolume: 10008713,
        tradeCount: 141,
        qualityStatus: 'OK',
    },
    {
        exchangeCode: 'BINANCE',
        marketType: 'SPOT',
        symbol: 'BTC-USDT',
        interval: '1m',
        openTime: '2026-06-29T01:03:00Z',
        closeTime: '2026-06-29T01:03:59Z',
        openPrice: 64790,
        highPrice: 64980,
        lowPrice: 64620,
        closePrice: 64840,
        volume: 111.3,
        quoteVolume: 7216692,
        tradeCount: 104,
        qualityStatus: null,
    },
];

async function seedAuthAndMarketdataStubs(page: Page): Promise<void> {
    // Why: 该 smoke 只验证前端 quality readiness 表达，不启动后端、不调用真实行情源。
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'marketdata-quality-smoke-session',
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
            defaultExchangeAccountId: 101,
            defaultExchangeCode: 'BINANCE',
            defaultTradeEnv: 'SIM',
            defaultAccountAlias: 'marketdata-quality-smoke',
        },
    }));

    await page.route('**/api/exchange-accounts', (route: Route) => route.fulfill({
        status: 200,
        json: [{
            exchangeAccountId: 101,
            legacyAccountId: null,
            exchangeCode: 'BINANCE',
            tradeEnv: 'SIM',
            accountAlias: 'marketdata-quality-smoke',
            externalAccountRef: null,
            isDefault: true,
            status: 'ACTIVE',
        }],
    }));

    await page.route('**/api/marketdata/bars**', (route: Route) => route.fulfill({
        status: 200,
        json: SAMPLE_BARS,
    }));

    await page.route('**/api/marketdata/readiness**', (route: Route) => route.fulfill({
        status: 200,
        json: SAMPLE_READINESS,
    }));
}

async function fillQueryWindow(page: Page): Promise<void> {
    const dateInputs = page.locator('.ant-picker-input input');

    await dateInputs.nth(0).fill('2026-06-29 01:00:00');
    await dateInputs.nth(0).press('Enter');
    await dateInputs.nth(1).fill('2026-06-29 01:04:00');
    await dateInputs.nth(1).press('Enter');
}

test.describe('marketdata quality readiness view', () => {
    test('mock readiness summary replaces pending source health and keeps kline visible', async ({page}) => {
        await seedAuthAndMarketdataStubs(page);
        await page.goto('/marketdata');

        const qualityPanel = page.getByTestId('marketdata-quality-readiness-view');
        await expect(qualityPanel).toBeVisible();
        await expect(qualityPanel).toContainText('UNKNOWN');

        await fillQueryWindow(page);
        const readinessResponse = page.waitForResponse((response) => (
            response.url().includes('/api/marketdata/readiness')
            && response.status() === 200
        ));
        await page.getByRole('button', {name: /查\s*询/}).first().click();
        await readinessResponse;

        const chartPanel = page.getByTestId('marketdata-kline-readiness-view');
        const kline = chartPanel.getByTestId('nq-kline-chart').filter({hasText: 'OHLCV K-line'}).first();

        await expect(kline.locator('canvas').first()).toBeVisible({timeout: 15_000});
        await expect(qualityPanel).toContainText(/Bars loaded[\s\S]*3/);
        await expect(qualityPanel).toContainText('Last bar time');
        await expect(qualityPanel).toContainText('Freshness');
        await expect(qualityPanel).toContainText('GAP');
        await expect(qualityPanel).toContainText('STALE');
        await expect(qualityPanel).toContainText(/Gap count[\s\S]*2/);
        await expect(qualityPanel).toContainText(/Unknown quality count[\s\S]*1/);
        await expect(qualityPanel).toContainText('Source health status');
        await expect(qualityPanel).toContainText('source health: GAP');
        await expect(qualityPanel).toContainText('NO_MIGRATION_MVP');
        await expect(qualityPanel).toContainText('Local bar sequence or qualityStatus evidence indicates a gap');
        await expect(qualityPanel).toContainText('Last success');
        await expect(qualityPanel).not.toContainText('Pending backend support');
        await expect(qualityPanel).not.toContainText('source health: not available from current API');
        await expect(page.getByText('Marketdata bars 查询失败')).toHaveCount(0);
    });
});
