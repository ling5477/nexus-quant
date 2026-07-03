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

type ReadinessFixture = Record<string, unknown>;

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
        openTime: '2026-06-29T01:01:00Z',
        closeTime: '2026-06-29T01:01:59Z',
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
        openTime: '2026-06-29T01:02:00Z',
        closeTime: '2026-06-29T01:02:59Z',
        openPrice: 64790,
        highPrice: 64980,
        lowPrice: 64620,
        closePrice: 64840,
        volume: 111.3,
        quoteVolume: 7216692,
        tradeCount: 104,
        qualityStatus: 'OK',
    },
];

function readinessFixture(overrides: Partial<ReadinessFixture> = {}): ReadinessFixture {
    return {
        exchangeCode: 'BINANCE',
        exchange: 'BINANCE',
        marketType: 'SPOT',
        instrumentId: 'BTC-USDT',
        symbol: 'BTC-USDT',
        interval: '1m',
        timeframe: '1m',
        sourceCode: 'BINANCE:BTC-USDT:1m',
        dataOrigin: 'LOCAL_DB',
        status: 'FRESH',
        sourceStatus: 'ENABLED',
        freshnessStatus: 'FRESH',
        sourceHealthStatus: 'FRESH',
        sourceHealth: 'HEALTHY',
        sourceHealthReason: 'Local DB bars are fresh and complete for the requested window.',
        gapStatus: 'NONE',
        qualityStatusSummary: {
            okCount: 3,
            gapSignalCount: 0,
            invalidCount: 0,
            unknownQualityCount: 0,
            statuses: {
                OK: 3,
            },
        },
        barCount: 3,
        firstBarTime: '2026-06-29T01:00:00Z',
        lastBarTime: '2026-06-29T01:02:59Z',
        expectedBarCount: 3,
        gapCount: 0,
        missingFrom: null,
        missingTo: null,
        unknownQualityCount: 0,
        lastSuccessAt: '2026-06-29T01:03:00Z',
        lastFailureAt: null,
        lastObservedAt: '2026-06-29T01:03:00Z',
        latencyMs: 18,
        errorRate: 0,
        errorCategory: 'NONE',
        staleAfterSeconds: 300,
        degradedReason: null,
        disabledReason: null,
        traceId: null,
        requestId: null,
        backendSupportLevel: 'NO_MIGRATION_MVP',
        generatedAt: '2026-06-29T01:04:00Z',
        updatedAt: '2026-06-29T01:04:00Z',
        ...overrides,
    };
}

async function seedAuthAndMarketdataStubs(
    page: Page,
    readiness: ReadinessFixture,
    bars: MarketdataBarFixture[] = SAMPLE_BARS,
): Promise<string[]> {
    const requests: string[] = [];

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

    page.on('request', (request) => requests.push(request.url()));

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
        json: bars,
    }));

    await page.route('**/api/marketdata/readiness**', (route: Route) => route.fulfill({
        status: 200,
        json: readiness,
    }));

    return requests;
}

async function fillQueryWindow(page: Page): Promise<void> {
    const dateInputs = page.locator('.ant-picker-input input');

    await dateInputs.nth(0).fill('2026-06-29 01:00:00');
    await dateInputs.nth(0).press('Enter');
    await dateInputs.nth(1).fill('2026-06-29 01:04:00');
    await dateInputs.nth(1).press('Enter');
}

async function loadReadinessScenario(
    page: Page,
    readiness: ReadinessFixture,
    bars: MarketdataBarFixture[] = SAMPLE_BARS,
): Promise<{qualityPanel: ReturnType<Page['getByTestId']>; requests: string[]}> {
    const requests = await seedAuthAndMarketdataStubs(page, readiness, bars);
    await page.goto('/marketdata');

    const qualityPanel = page.getByTestId('marketdata-quality-readiness-view');
    await expect(qualityPanel).toBeVisible();
    await expect(qualityPanel).toContainText('行情数据质量只读诊断');

    await fillQueryWindow(page);
    const readinessResponse = page.waitForResponse((response) => (
        response.url().includes('/api/marketdata/readiness')
        && response.request().method() === 'GET'
        && response.status() === 200
    ));
    await page.getByRole('button', {name: /查\s*询/}).first().click();
    await readinessResponse;

    return {qualityPanel, requests};
}

function expectNoForbiddenCopy(page: Page) {
    return expect(page.getByText(/trading authorized|live ready|permission granted|provider ready|real-ready|live-ready|private trading ready/i)).toHaveCount(0);
}

function expectNoForbiddenRequests(requests: string[]): void {
    const forbiddenApiPattern = /credential|permission|withdraw|transfer|\/order|\/cancel|\/amend|positions|wallet|subaccount|\/private|listenKey/i;
    const forbiddenHostPattern = /okx|binance|bybit|coinbase|kraken|gate\.io/i;

    for (const requestUrl of requests) {
        const hostname = new URL(requestUrl).hostname;
        expect(requestUrl, `forbidden private/credential API request: ${requestUrl}`).not.toMatch(forbiddenApiPattern);
        expect(hostname, `forbidden real exchange host request: ${requestUrl}`).not.toMatch(forbiddenHostPattern);
    }
}

test.describe('marketdata quality readiness view', () => {
    test('显示 FRESH / HEALTHY / NONE，并把 nullable 事实显示为中文空态', async ({page}) => {
        const {qualityPanel, requests} = await loadReadinessScenario(page, readinessFixture({
            gapCount: null,
            missingFrom: null,
            missingTo: null,
            latencyMs: null,
            errorRate: null,
            traceId: null,
            requestId: null,
        }));

        const chartPanel = page.getByTestId('marketdata-kline-readiness-view');
        const kline = chartPanel.getByTestId('nq-kline-chart').filter({hasText: 'OHLCV K-line'}).first();
        await expect(kline.locator('canvas').first()).toBeVisible({timeout: 15_000});

        await expect(qualityPanel).toContainText('本页仅展示行情数据质量诊断结果。');
        await expect(qualityPanel).toContainText('数据质量正常不代表可以交易。');
        await expect(qualityPanel).toContainText('Public marketdata readiness 不等于 trading authorization。');
        await expect(qualityPanel).toContainText('LIVE 当前禁用，private trading / permission probe / real provider 未实现。');
        await expect(qualityPanel).toContainText('FRESH');
        await expect(qualityPanel).toContainText('HEALTHY');
        await expect(qualityPanel).toContainText('NONE');
        await expect(qualityPanel).toContainText('LOCAL_DB');
        await expect(qualityPanel).toContainText(/Gap count[\s\S]*暂无稳定事实/);
        await expect(qualityPanel).toContainText(/Error rate[\s\S]*暂无稳定事实/);
        await expect(qualityPanel).toContainText(/Missing from[\s\S]*暂无稳定事实/);
        await expect(qualityPanel).toContainText(/Missing to[\s\S]*暂无稳定事实/);
        await expect(qualityPanel).toContainText(/Trace \/ Request[\s\S]*暂无稳定事实 \/ 暂无稳定事实/);
        await expect(qualityPanel).not.toContainText(/Gap count0/);

        await expectNoForbiddenCopy(page);
        expectNoForbiddenRequests(requests);
    });

    const statusScenarios: Array<{
        name: string;
        readiness: ReadinessFixture;
        bars?: MarketdataBarFixture[];
        expected: string[];
    }> = [
        {
            name: 'STALE',
            readiness: readinessFixture({
                status: 'STALE',
                sourceStatus: 'DEGRADED',
                freshnessStatus: 'STALE',
                sourceHealthStatus: 'STALE',
                sourceHealth: 'DEGRADED',
                gapStatus: 'NONE',
                errorCategory: 'STALE',
                degradedReason: 'last local bar is older than stale threshold',
            }),
            expected: ['STALE', 'DEGRADED'],
        },
        {
            name: 'NO_DATA',
            readiness: readinessFixture({
                status: 'NO_DATA',
                sourceStatus: 'DEGRADED',
                freshnessStatus: 'NO_DATA',
                sourceHealthStatus: 'NO_DATA',
                sourceHealth: 'UNKNOWN',
                gapStatus: 'UNKNOWN',
                errorCategory: 'UNKNOWN',
                barCount: 0,
                firstBarTime: null,
                lastBarTime: null,
                expectedBarCount: null,
                gapCount: null,
            }),
            bars: [],
            expected: ['NO_DATA', 'UNKNOWN', '暂无稳定事实'],
        },
        {
            name: 'ERROR',
            readiness: readinessFixture({
                status: 'ERROR',
                sourceStatus: 'ERROR',
                freshnessStatus: 'ERROR',
                sourceHealthStatus: 'ERROR',
                sourceHealth: 'ERROR',
                gapStatus: 'UNKNOWN',
                errorCategory: 'UNKNOWN',
                sourceHealthReason: 'latest ingestion failed before stable data facts were available',
                lastFailureAt: '2026-06-29T01:03:20Z',
            }),
            expected: ['ERROR', 'latest ingestion failed'],
        },
        {
            name: 'DISABLED',
            readiness: readinessFixture({
                status: 'DISABLED',
                sourceStatus: 'DISABLED',
                freshnessStatus: 'DISABLED',
                sourceHealthStatus: 'DISABLED',
                sourceHealth: 'UNKNOWN',
                gapStatus: 'UNKNOWN',
                errorCategory: 'DISABLED',
                disabledReason: 'manual source switch is disabled',
            }),
            expected: ['DISABLED', 'manual source switch is disabled'],
        },
        {
            name: 'GAP',
            readiness: readinessFixture({
                status: 'GAP',
                sourceStatus: 'DEGRADED',
                freshnessStatus: 'FRESH',
                sourceHealthStatus: 'GAP',
                sourceHealth: 'DEGRADED',
                gapStatus: 'GAP',
                errorCategory: 'GAP',
                gapCount: 2,
                missingFrom: '2026-06-29T01:03:00Z',
                missingTo: '2026-06-29T01:04:00Z',
                degradedReason: 'two bars are missing in the requested window',
            }),
            expected: ['GAP', '2', 'two bars are missing'],
        },
    ];

    for (const scenario of statusScenarios) {
        test(`覆盖 ${scenario.name} readiness 状态`, async ({page}) => {
            const {qualityPanel, requests} = await loadReadinessScenario(
                page,
                scenario.readiness,
                scenario.bars ?? SAMPLE_BARS,
            );

            for (const expectedText of scenario.expected) {
                await expect(qualityPanel).toContainText(expectedText);
            }
            await expect(qualityPanel).toContainText('Source health status');
            await expect(qualityPanel).toContainText('Gap status');
            await expect(qualityPanel).toContainText('Error category');

            await expectNoForbiddenCopy(page);
            expectNoForbiddenRequests(requests);
        });
    }
});
