import {expect, test, type Locator, type Page} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';
import {
    cleanupPositiveMarketdataBarsFixture,
    POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS,
    POSITIVE_MARKETDATA_FIXTURE_INPUT,
    POSITIVE_MARKETDATA_FIXTURE_QUERY,
    POSITIVE_MARKETDATA_FIXTURE_SOURCE,
    preparePositiveMarketdataBarsFixture,
} from './marketdata-positive-bars-fixture';

/**
 * GateM-2I MarketData positive bars fixture smoke.
 *
 * Why:
 * GateM-2G proved the real-backend empty/no-data branch. This smoke prepares a bounded fake
 * `marketdata_bars` fixture in the local test DB, keeps the browser on real `/api/marketdata/bars`
 * and `/api/marketdata/readiness`, and verifies the positive K-line / volume / Data Quality branch.
 *
 * Boundaries:
 * - No route stub for `/api/marketdata/bars` or `/api/marketdata/readiness`.
 * - No backend code, migration, production API, ingestion run, provider, WebSocket, LIVE, AI, or DH.
 * - Fixture cleanup is limited to `E2E_POSITIVE_FIXTURE` plus exact scope/window.
 */

interface MarketdataBarsQuery {
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    startTime: string;
    endTime: string;
    page: number;
    size: number;
}

interface MarketdataReadinessQuery {
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    from: string;
    to: string;
}

interface MarketdataBarPayload {
    exchangeCode?: string;
    marketType?: string;
    symbol?: string;
    interval?: string;
    openTime?: string;
    closeTime?: string | null;
    closePrice?: number | string | null;
    volume?: number | string | null;
    qualityStatus?: string | null;
}

interface MarketdataReadinessPayload {
    status?: string;
    freshnessStatus?: string;
    sourceHealthStatus?: string;
    sourceHealthReason?: string;
    backendSupportLevel?: string;
    barCount?: number;
    firstBarTime?: string | null;
    lastBarTime?: string | null;
    expectedBarCount?: number | null;
    gapCount?: number | null;
    unknownQualityCount?: number;
    qualityStatusSummary?: {
        okCount?: number;
        gapSignalCount?: number;
        invalidCount?: number;
        unknownQualityCount?: number;
        statuses?: Record<string, number>;
    };
    generatedAt?: string;
}

interface BackendHealthCheck {
    available: boolean;
    detail: string;
}

const backendBaseUrl = process.env.E2E_BACKEND_BASE_URL ?? 'http://127.0.0.1:18888';

async function checkBackendHealth(): Promise<BackendHealthCheck> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3_000);

    try {
        const response = await fetch(`${backendBaseUrl}/actuator/health`, {signal: controller.signal});
        const body = await response.json().catch(() => ({})) as {status?: string};
        return {
            available: response.ok && body.status === 'UP',
            detail: `status=${response.status}, health=${body.status ?? 'UNKNOWN'}`,
        };
    } catch (error) {
        return {
            available: false,
            detail: error instanceof Error ? error.message : String(error),
        };
    } finally {
        clearTimeout(timeout);
    }
}

async function readAuthorizationHeader(page: Page): Promise<string> {
    const rawSession = await page.evaluate(() => window.localStorage.getItem('nexus-quant.console.auth'));
    expect(rawSession, 'positive fixture smoke requires browser login session').toBeTruthy();

    const session = JSON.parse(rawSession as string) as {accessToken?: string; tokenType?: string};
    expect(session.accessToken, 'positive fixture smoke requires an accessToken after login').toBeTruthy();
    return `${session.tokenType ?? 'Bearer'} ${session.accessToken}`;
}

function barsQueryPath(query: MarketdataBarsQuery): string {
    const params = new URLSearchParams({
        exchangeCode: query.exchangeCode,
        marketType: query.marketType,
        symbol: query.symbol,
        interval: query.interval,
        startTime: query.startTime,
        endTime: query.endTime,
        page: String(query.page),
        size: String(query.size),
    });

    return `/api/marketdata/bars?${params.toString()}`;
}

function readinessQueryPath(query: MarketdataReadinessQuery): string {
    const params = new URLSearchParams({
        exchangeCode: query.exchangeCode,
        marketType: query.marketType,
        symbol: query.symbol,
        interval: query.interval,
        from: query.from,
        to: query.to,
    });

    return `/api/marketdata/readiness?${params.toString()}`;
}

function toReadinessQuery(query: MarketdataBarsQuery): MarketdataReadinessQuery {
    return {
        exchangeCode: query.exchangeCode,
        marketType: query.marketType,
        symbol: query.symbol,
        interval: query.interval,
        from: query.startTime,
        to: query.endTime,
    };
}

async function listBars(page: Page, authorizationHeader: string, query: MarketdataBarsQuery): Promise<MarketdataBarPayload[]> {
    const response = await page.request.get(barsQueryPath(query), {
        headers: {Authorization: authorizationHeader},
        timeout: 30_000,
    });
    expect(response.ok(), await response.text()).toBeTruthy();

    const payload = await response.json();
    expect(Array.isArray(payload), 'GET /api/marketdata/bars must return an array payload').toBeTruthy();
    return payload as MarketdataBarPayload[];
}

async function getReadiness(
    page: Page,
    authorizationHeader: string,
    query: MarketdataBarsQuery,
): Promise<MarketdataReadinessPayload> {
    const response = await page.request.get(readinessQueryPath(toReadinessQuery(query)), {
        headers: {Authorization: authorizationHeader},
        timeout: 30_000,
    });
    expect(response.ok(), await response.text()).toBeTruthy();

    const payload = await response.json();
    expect(payload && typeof payload === 'object' && !Array.isArray(payload), 'readiness payload must be an object').toBeTruthy();
    return payload as MarketdataReadinessPayload;
}

async function selectAntdValue(page: Page, formCard: Locator, label: string, value: string): Promise<void> {
    const formItem = formCard.locator('.ant-form-item').filter({hasText: label});
    await formItem.locator('.ant-select-selector').click();
    await page.locator('.ant-select-dropdown:visible').last().getByTitle(value, {exact: true}).click();
}

async function fillDateTimePicker(formCard: Locator, label: string, value: string): Promise<void> {
    const input = formCard.locator('.ant-form-item').filter({hasText: label}).locator('input');
    await input.click();
    await input.fill(value);
    await input.press('Enter');
}

async function fillBarsQuery(page: Page, formCard: Locator, query: MarketdataBarsQuery): Promise<void> {
    await selectAntdValue(page, formCard, '交易所', query.exchangeCode);
    await selectAntdValue(page, formCard, '交易对', query.symbol);
    await selectAntdValue(page, formCard, '周期', query.interval);
    await fillDateTimePicker(formCard, '开始时间', POSITIVE_MARKETDATA_FIXTURE_INPUT.startTime);
    await fillDateTimePicker(formCard, '结束时间', POSITIVE_MARKETDATA_FIXTURE_INPUT.endTime);
}

test.describe('marketdata positive bars fixture smoke', () => {
    test('受控 fake bars 驱动真实 bars/readiness positive 分支', async ({page}) => {
        test.setTimeout(180_000);

        const health = await checkBackendHealth();
        expect(health.available, `local backend unavailable for positive marketdata fixture smoke: ${health.detail}`).toBeTruthy();

        const preparedCount = await preparePositiveMarketdataBarsFixture();
        expect(preparedCount, 'fixture helper must prepare the expected fake bars').toBe(POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS);

        try {
            await loginToConsole(page);
            const authorizationHeader = await readAuthorizationHeader(page);
            const [preflightBars, preflightReadiness] = await Promise.all([
                listBars(page, authorizationHeader, POSITIVE_MARKETDATA_FIXTURE_QUERY),
                getReadiness(page, authorizationHeader, POSITIVE_MARKETDATA_FIXTURE_QUERY),
            ]);

            expect(preflightBars.length).toBeGreaterThan(0);
            expect(preflightReadiness.barCount ?? 0).toBeGreaterThan(0);
            expect(preflightReadiness.barCount).toBe(POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS);
            expect(preflightReadiness.expectedBarCount).toBe(POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS);
            expect(preflightReadiness.gapCount).toBe(0);
            expect(preflightReadiness.qualityStatusSummary?.okCount).toBe(POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS);
            expect(preflightReadiness.freshnessStatus).toBe('FRESH');
            expect(preflightReadiness.sourceHealthStatus).toBe('FRESH');

            console.info(
                [
                    'marketdata-positive-bars-fixture-smoke',
                    `source=${POSITIVE_MARKETDATA_FIXTURE_SOURCE}`,
                    `scope=${POSITIVE_MARKETDATA_FIXTURE_QUERY.exchangeCode}/${POSITIVE_MARKETDATA_FIXTURE_QUERY.marketType}/${POSITIVE_MARKETDATA_FIXTURE_QUERY.symbol}/${POSITIVE_MARKETDATA_FIXTURE_QUERY.interval}`,
                    `fixtureBars=${preparedCount}`,
                    `preflightBars=${preflightBars.length}`,
                    `readinessBarCount=${preflightReadiness.barCount ?? 'unknown'}`,
                    `readinessStatus=${preflightReadiness.status ?? 'unknown'}`,
                    `freshnessStatus=${preflightReadiness.freshnessStatus ?? 'unknown'}`,
                    `sourceHealthStatus=${preflightReadiness.sourceHealthStatus ?? 'unknown'}`,
                ].join(' '),
            );

            await page.getByRole('menuitem', {name: '行情查询'}).click();
            await expect(page).toHaveURL(/\/marketdata$/);
            await expect(page.getByRole('heading', {name: 'Marketdata'})).toBeVisible();

            const queryCard = page.locator('.ant-card').filter({hasText: '查询条件'});
            await fillBarsQuery(page, queryCard, POSITIVE_MARKETDATA_FIXTURE_QUERY);

            const barsResponsePromise = page.waitForResponse((response) =>
                response.url().includes('/api/marketdata/bars') && response.request().method() === 'GET',
            );
            const readinessResponsePromise = page.waitForResponse((response) =>
                response.url().includes('/api/marketdata/readiness') && response.request().method() === 'GET',
            );
            await queryCard.getByRole('button', {name: /查\s*询/}).click();

            const [barsResponse, readinessResponse] = await Promise.all([barsResponsePromise, readinessResponsePromise]);
            expect(barsResponse.status()).toBe(200);
            expect(readinessResponse.status()).toBe(200);

            const barsPayload = await barsResponse.json() as MarketdataBarPayload[];
            const readinessPayload = await readinessResponse.json() as MarketdataReadinessPayload;
            expect(Array.isArray(barsPayload), 'page query must receive real bars array payload').toBeTruthy();
            expect(barsPayload.length).toBeGreaterThan(0);
            expect(readinessPayload.barCount ?? 0).toBeGreaterThan(0);
            expect(readinessPayload.barCount).toBe(POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS);
            expect(readinessPayload.expectedBarCount).toBe(POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS);
            expect(readinessPayload.gapCount).toBe(0);

            const chartPanel = page.getByTestId('marketdata-kline-readiness-view');
            const qualityPanel = page.getByTestId('marketdata-quality-readiness-view');
            await expect(chartPanel).toBeVisible();
            await expect(qualityPanel).toBeVisible();
            await expect(page.getByText('Data Quality / Readiness')).toBeVisible();

            await expect(chartPanel).toContainText(POSITIVE_MARKETDATA_FIXTURE_QUERY.exchangeCode);
            await expect(chartPanel).toContainText(POSITIVE_MARKETDATA_FIXTURE_QUERY.symbol);
            await expect(chartPanel).toContainText(POSITIVE_MARKETDATA_FIXTURE_QUERY.interval);
            await expect(chartPanel).toContainText(new RegExp(`Bar count[\\s\\S]*${POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS}`));

            const kline = chartPanel.getByTestId('nq-kline-chart').filter({hasText: 'OHLCV K-line'}).first();
            const volume = chartPanel.getByTestId('nq-volume-chart').filter({hasText: 'Volume'}).first();
            await expect(kline).toBeVisible();
            await expect(volume).toBeVisible();
            await expect(kline.locator('canvas').first()).toBeVisible({timeout: 15_000});
            await expect(volume.locator('canvas').first()).toBeVisible({timeout: 15_000});

            await expect(qualityPanel).toContainText('from /api/marketdata/readiness');
            await expect(qualityPanel).toContainText(new RegExp(`Bars loaded[\\s\\S]*${POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS}`));
            await expect(qualityPanel).toContainText('Last bar time');
            await expect(qualityPanel).toContainText(POSITIVE_MARKETDATA_FIXTURE_QUERY.endTime);
            await expect(qualityPanel).toContainText('Readiness status');
            await expect(qualityPanel).toContainText('FRESH');
            await expect(qualityPanel).toContainText('freshness: FRESH');
            await expect(qualityPanel).toContainText('source health: FRESH');
            await expect(qualityPanel).toContainText('NO_MIGRATION_MVP');
            await expect(qualityPanel).toContainText(new RegExp(`Quality status[\\s\\S]*ok=${POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS}`));
            await expect(qualityPanel).toContainText(/Gap count[\s\S]*0/);
            await expect(qualityPanel).toContainText(`expected=${POSITIVE_MARKETDATA_FIXTURE_EXPECTED_BARS}`);
            await expect(qualityPanel).toContainText('Local bars satisfy the requested readiness window using DB-only aggregation.');
            await expect(qualityPanel).toContainText(/Unknown quality count[\s\S]*0/);
            await expect(qualityPanel).toContainText('Latest close');
            await expect(qualityPanel).toContainText('Latest volume');

            await expect(page.getByText('No bars returned')).toHaveCount(0);
            await expect(page.getByText('Marketdata bars 查询失败')).toHaveCount(0);
            await expect(page.getByText('MarketData source health unavailable')).toHaveCount(0);
        } finally {
            const remainingCount = await cleanupPositiveMarketdataBarsFixture();
            expect(remainingCount, 'fixture cleanup must leave no E2E_POSITIVE_FIXTURE rows in the scoped window').toBe(0);
        }
    });
});
