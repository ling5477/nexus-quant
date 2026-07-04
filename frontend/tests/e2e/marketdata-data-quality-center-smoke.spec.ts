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

interface QualityMetricFixture {
    value: number | null;
    status: string;
    reason: string | null;
}

interface QualityOverviewFixture {
    scope: {
        exchangeCode: string | null;
        marketType: string | null;
        symbol: string | null;
        interval: string | null;
        sourceType: string | null;
        dataOrigin: string | null;
        datasetId: string | null;
        from: string | null;
        to: string | null;
    };
    totalBars: number;
    expectedBars: number | null;
    gapCount: number | null;
    duplicateCount: QualityMetricFixture;
    outOfOrderCount: QualityMetricFixture;
    staleCount: QualityMetricFixture;
    latestBarTime: string | null;
    earliestBarTime: string | null;
    lastSuccessAt: string | null;
    lastFailureAt: string | null;
    lastIngestionRunId: string | null;
    sourceHealth: string;
    freshnessStatus: string;
    qualityStatus: string;
    dataOriginSummary: {
        requestedDataOrigin: string | null;
        effectiveDataOrigin: string;
        localDbBars: number;
        fixtureBars: number;
        unknownOriginBars: number;
        supportLevel: string;
    };
    datasetCoverageSummary: {
        datasetCount: number;
        expectedBars: number | null;
        actualBars: number | null;
        missingBars: number | null;
        duplicateBars: number | null;
        invalidBars: number | null;
        latestDatasetId: string | null;
        latestCoverageAt: string | null;
    };
    topIssues: Array<{
        code: string;
        severity: string;
        count: number;
        message: string;
    }>;
    generatedAt: string;
}

type QualityOverviewOverrides = Partial<Omit<
    QualityOverviewFixture,
    'scope' | 'dataOriginSummary' | 'datasetCoverageSummary'
>> & {
    scope?: Partial<QualityOverviewFixture['scope']>;
    dataOriginSummary?: Partial<QualityOverviewFixture['dataOriginSummary']>;
    datasetCoverageSummary?: Partial<QualityOverviewFixture['datasetCoverageSummary']>;
};

type ReadinessFixture = Record<string, unknown>;

const SAMPLE_BARS: MarketdataBarFixture[] = [
    {
        exchangeCode: 'BINANCE',
        marketType: 'SPOT',
        symbol: 'BTC-USDT',
        interval: '1m',
        openTime: '2026-07-04T01:00:00Z',
        closeTime: '2026-07-04T01:00:59Z',
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
        openTime: '2026-07-04T01:01:00Z',
        closeTime: '2026-07-04T01:01:59Z',
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
        openTime: '2026-07-04T01:02:00Z',
        closeTime: '2026-07-04T01:02:59Z',
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

function metric(status: string, value: number | null, reason: string | null): QualityMetricFixture {
    return {status, value, reason};
}

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
            statuses: {OK: 3},
        },
        barCount: 3,
        unknownQualityCount: 0,
        errorCategory: 'NONE',
        backendSupportLevel: 'NO_MIGRATION_MVP',
        generatedAt: '2026-07-04T01:04:00Z',
        updatedAt: '2026-07-04T01:04:00Z',
        ...overrides,
    };
}

function qualityOverviewFixture(overrides: QualityOverviewOverrides = {}): QualityOverviewFixture {
    const base: QualityOverviewFixture = {
        scope: {
            exchangeCode: 'BINANCE',
            marketType: 'SPOT',
            symbol: 'BTC-USDT',
            interval: '1m',
            sourceType: null,
            dataOrigin: null,
            datasetId: null,
            from: '2026-07-04T01:00:00Z',
            to: '2026-07-04T01:04:00Z',
        },
        totalBars: 3,
        expectedBars: 4,
        gapCount: 1,
        duplicateCount: metric('NOT_AVAILABLE', null, 'duplicate facts require dataset coverage support'),
        outOfOrderCount: metric('NOT_AVAILABLE', null, 'out-of-order facts are not persisted in current schema'),
        staleCount: metric('AVAILABLE', 0, 'computed from latest local bar time'),
        latestBarTime: '2026-07-04T01:02:59Z',
        earliestBarTime: '2026-07-04T01:00:00Z',
        lastSuccessAt: '2026-07-04T01:03:00Z',
        lastFailureAt: null,
        lastIngestionRunId: '11111111-1111-1111-1111-111111111111',
        sourceHealth: 'HEALTHY',
        freshnessStatus: 'FRESH',
        qualityStatus: 'OK',
        dataOriginSummary: {
            requestedDataOrigin: null,
            effectiveDataOrigin: 'LOCAL_DB',
            localDbBars: 3,
            fixtureBars: 0,
            unknownOriginBars: 0,
            supportLevel: 'LOCAL_DB_ONLY_READ_MODEL',
        },
        datasetCoverageSummary: {
            datasetCount: 1,
            expectedBars: 4,
            actualBars: 3,
            missingBars: 1,
            duplicateBars: null,
            invalidBars: 0,
            latestDatasetId: '22222222-2222-2222-2222-222222222222',
            latestCoverageAt: '2026-07-04T01:05:00Z',
        },
        topIssues: [
            {
                code: 'GAP_DETECTED',
                severity: 'WARNING',
                count: 1,
                message: 'one local bar is missing in the requested window',
            },
        ],
        generatedAt: '2026-07-04T01:06:00Z',
    };

    return {
        ...base,
        ...overrides,
        scope: {...base.scope, ...overrides.scope},
        duplicateCount: overrides.duplicateCount ?? base.duplicateCount,
        outOfOrderCount: overrides.outOfOrderCount ?? base.outOfOrderCount,
        staleCount: overrides.staleCount ?? base.staleCount,
        dataOriginSummary: {...base.dataOriginSummary, ...overrides.dataOriginSummary},
        datasetCoverageSummary: {...base.datasetCoverageSummary, ...overrides.datasetCoverageSummary},
        topIssues: overrides.topIssues ?? base.topIssues,
    };
}

async function seedAuthAndQualityStubs(
    page: Page,
    overview: QualityOverviewFixture,
    bars: MarketdataBarFixture[] = SAMPLE_BARS,
    readiness: ReadinessFixture = readinessFixture(),
): Promise<{apiWrites: string[]; apiRequests: string[]; externalExchangeRequests: string[]}> {
    const apiWrites: string[] = [];
    const apiRequests: string[] = [];
    const externalExchangeRequests: string[] = [];

    // Why: 该 smoke 验证前端只读 Data Quality Center，不启动后端、不触达真实交易所或私有接口。
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'marketdata-quality-center-smoke-session',
            tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z',
            username: 'e2e-operator',
            roles: ['ADMIN'],
        }));
    });

    page.on('request', (request) => {
        const url = request.url();
        const entry = `${request.method()} ${url}`;
        const host = new URL(url).hostname;

        if (/okx|binance|bybit|gate|coinbase|kraken/i.test(host)) {
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
            defaultAccountAlias: 'marketdata-quality-center-smoke',
        },
    }));

    await page.route('**/api/exchange-accounts', (route: Route) => route.fulfill({
        status: 200,
        json: [{
            exchangeAccountId: 101,
            legacyAccountId: null,
            exchangeCode: 'BINANCE',
            tradeEnv: 'SIM',
            accountAlias: 'marketdata-quality-center-smoke',
            externalAccountRef: null,
            isDefault: true,
            status: 'ACTIVE',
        }],
    }));

    await page.route('**/api/marketdata/bars**', (route: Route) => route.fulfill({status: 200, json: bars}));
    await page.route('**/api/marketdata/readiness**', (route: Route) => route.fulfill({status: 200, json: readiness}));
    await page.route('**/api/marketdata/quality/overview**', (route: Route) => route.fulfill({
        status: 200,
        json: overview,
    }));

    return {apiWrites, apiRequests, externalExchangeRequests};
}

async function fillQueryWindow(page: Page): Promise<void> {
    const dateInputs = page.locator('.ant-picker-input input');

    await dateInputs.nth(0).fill('2026-07-04 01:00:00');
    await dateInputs.nth(0).press('Enter');
    await dateInputs.nth(1).fill('2026-07-04 01:04:00');
    await dateInputs.nth(1).press('Enter');
}

async function loadQualityCenter(
    page: Page,
    overview: QualityOverviewFixture,
    bars: MarketdataBarFixture[] = SAMPLE_BARS,
): Promise<{
    center: ReturnType<Page['getByTestId']>;
    apiWrites: string[];
    apiRequests: string[];
    externalExchangeRequests: string[];
}> {
    const requestState = await seedAuthAndQualityStubs(page, overview, bars);
    await page.goto('/marketdata');

    const center = page.getByTestId('marketdata-data-quality-center');
    await expect(center).toBeVisible();
    await expect(center).toContainText('Data Quality diagnostic only');

    await fillQueryWindow(page);
    const overviewResponse = page.waitForResponse((response) => (
        response.url().includes('/api/marketdata/quality/overview')
        && response.request().method() === 'GET'
        && response.status() === 200
    ));
    await page.getByRole('button', {name: /查\s*询/}).first().click();
    await overviewResponse;

    return {center, ...requestState};
}

async function expectNoMisleadingTradingCopy(page: Page): Promise<void> {
    await expect(page.locator('body')).not.toContainText(/tradingReady|liveReady|authorizedForTrading/i);
}

function expectNoForbiddenCalls(state: {
    apiWrites: string[];
    apiRequests: string[];
    externalExchangeRequests: string[];
}): void {
    expect(state.apiWrites, 'Data Quality Center must not call API write endpoints').toEqual([]);
    expect(
        state.apiRequests.some((entry) => /permission-probe|credential|\/order|\/cancel|withdraw|transfer|\/private/i.test(entry)),
        'Data Quality Center must not call private, credential, permission probe, order, cancel, withdraw or transfer endpoints',
    ).toBeFalsy();
    expect(state.externalExchangeRequests, 'Data Quality Center must not call real exchange hosts').toEqual([]);
}

test.describe('marketdata data quality center', () => {
    test('renders overview fields, topIssues and diagnostic-only boundary', async ({page}) => {
        const state = await loadQualityCenter(page, qualityOverviewFixture());
        const {center} = state;

        await expect(center).toContainText('scope.exchangeCode');
        await expect(center).toContainText('BINANCE');
        await expect(center).toContainText('totalBars');
        await expect(center).toContainText('expectedBars');
        await expect(center).toContainText('gapCount');
        await expect(center).toContainText('duplicateCount');
        await expect(center).toContainText('NOT_AVAILABLE');
        await expect(center).toContainText('outOfOrderCount');
        await expect(center).toContainText('staleCount');
        await expect(center).toContainText('latestBarTime');
        await expect(center).toContainText('earliestBarTime');
        await expect(center).toContainText('lastSuccessAt');
        await expect(center).toContainText('lastFailureAt');
        await expect(center).toContainText('lastIngestionRunId');
        await expect(center).toContainText('sourceHealth');
        await expect(center).toContainText('HEALTHY');
        await expect(center).toContainText('freshnessStatus');
        await expect(center).toContainText('FRESH');
        await expect(center).toContainText('qualityStatus');
        await expect(center).toContainText('OK');
        await expect(center).toContainText('dataOriginSummary');
        await expect(center).toContainText('LOCAL_DB');
        await expect(center).toContainText('LOCAL_DB_ONLY_READ_MODEL');
        await expect(center).toContainText('datasetCoverageSummary');
        await expect(center).toContainText('GAP_DETECTED');
        await expect(center).toContainText('one local bar is missing');
        await expect(center).toContainText('数据质量通过不等于 trading authorization');

        await expectNoMisleadingTradingCopy(page);
        expectNoForbiddenCalls(state);
    });

    test('keeps NO_DATA / UNKNOWN / NOT_AVAILABLE / INCOMPLETE visible without coercing them to zero', async ({page}) => {
        const state = await loadQualityCenter(page, qualityOverviewFixture({
            totalBars: 0,
            expectedBars: null,
            gapCount: null,
            duplicateCount: metric('NOT_AVAILABLE', null, 'duplicate coverage facts are unavailable'),
            outOfOrderCount: metric('UNKNOWN', null, 'out-of-order diagnostics are unknown'),
            staleCount: metric('NOT_AVAILABLE', null, 'stale count is not available without latest bar time'),
            latestBarTime: null,
            earliestBarTime: null,
            lastSuccessAt: null,
            lastFailureAt: null,
            lastIngestionRunId: null,
            sourceHealth: 'UNKNOWN',
            freshnessStatus: 'NO_DATA',
            qualityStatus: 'INCOMPLETE',
            dataOriginSummary: {
                localDbBars: 0,
                unknownOriginBars: 0,
            },
            datasetCoverageSummary: {
                datasetCount: 0,
                expectedBars: null,
                actualBars: null,
                missingBars: null,
                duplicateBars: null,
                invalidBars: null,
                latestDatasetId: null,
                latestCoverageAt: null,
            },
            topIssues: [{
                code: 'NO_DATA',
                severity: 'WARNING',
                count: 1,
                message: 'No local bars were found for this scope.',
            }],
        }), []);
        const {center} = state;

        await expect(center).toContainText('NO_DATA');
        await expect(center).toContainText('UNKNOWN');
        await expect(center).toContainText('NOT_AVAILABLE');
        await expect(center).toContainText('INCOMPLETE');
        await expect(center).toContainText('暂无稳定事实');
        await expect(center).toContainText('No local bars were found');
        await expect(center).toContainText('null 不等于无缺口');
        await expect(center).toContainText('out-of-order diagnostics are unknown');

        await expectNoMisleadingTradingCopy(page);
        expectNoForbiddenCalls(state);
    });
});
