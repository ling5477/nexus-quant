import {expect, test, type Locator, type Page} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

/**
 * GateM-2G MarketData readiness real-backend smoke.
 *
 * Why:
 * GateM-2F only proved the MarketData readiness UI with backend-free route stubs. This smoke keeps
 * the browser connected to the real local backend and verifies that the page issues both readonly
 * requests, `/api/marketdata/bars` and `/api/marketdata/readiness`, before rendering K-line,
 * volume, and Data Quality / Readiness evidence. It intentionally does not seed bars or call any
 * exchange adapter, so an empty local DB remains an accepted no-data branch.
 *
 * Boundaries:
 * - No route stub for `/api/marketdata/bars` or `/api/marketdata/readiness`.
 * - No backend mutation except the existing E2E login/account fixture in `loginToConsole`.
 * - No external exchange call, LIVE enablement, WebSocket, order flow, AI, or DH runtime.
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
    lastSuccessAt?: string | null;
    lastFailureAt?: string | null;
    generatedAt?: string;
}

const backendBaseUrl = process.env.E2E_BACKEND_BASE_URL ?? 'http://127.0.0.1:18888';
const queryStart = '2025-01-01T00:00:00Z';
const queryEnd = '2026-12-31T23:59:59Z';
const queryStartInput = '2025-01-01 00:00:00';
const queryEndInput = '2026-12-31 23:59:59';
const defaultQuery: MarketdataBarsQuery = {
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    interval: '1m',
    startTime: queryStart,
    endTime: queryEnd,
    page: 0,
    size: 100,
};

const candidateQueries: MarketdataBarsQuery[] = ['BINANCE', 'OKX'].flatMap((exchangeCode) =>
    ['BTC-USDT', 'ETH-USDT', 'SOL-USDT'].flatMap((symbol) =>
        ['1m', '5m', '15m', '1h', '1d'].map((interval) => ({
            ...defaultQuery,
            exchangeCode,
            symbol,
            interval,
        })),
    ),
);

interface BackendHealthCheck {
    available: boolean;
    detail: string;
}

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
    expect(rawSession, 'real backend readiness smoke requires browser login session').toBeTruthy();

    const session = JSON.parse(rawSession as string) as {accessToken?: string; tokenType?: string};
    expect(session.accessToken, 'real backend readiness smoke requires an accessToken after login').toBeTruthy();
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

async function findRealBackendScenario(
    page: Page,
): Promise<{query: MarketdataBarsQuery; bars: MarketdataBarPayload[]; readiness: MarketdataReadinessPayload}> {
    const authorizationHeader = await readAuthorizationHeader(page);
    let defaultScenario: {query: MarketdataBarsQuery; bars: MarketdataBarPayload[]; readiness: MarketdataReadinessPayload} | null = null;

    // Why: this is the DB data precheck for 2G. It stays read-only, searches only UI-supported
    // MarketData dimensions, and decides whether the browser assertions should use the positive
    // bars branch or the accepted empty/no-data real-backend branch.
    for (const query of candidateQueries) {
        const [bars, readiness] = await Promise.all([
            listBars(page, authorizationHeader, query),
            getReadiness(page, authorizationHeader, query),
        ]);

        if (query.exchangeCode === defaultQuery.exchangeCode
            && query.symbol === defaultQuery.symbol
            && query.interval === defaultQuery.interval) {
            defaultScenario = {query, bars, readiness};
        }

        if (bars.length > 0) {
            return {query, bars, readiness};
        }
    }

    return defaultScenario ?? {
        query: defaultQuery,
        bars: [],
        readiness: await getReadiness(page, authorizationHeader, defaultQuery),
    };
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
    await fillDateTimePicker(formCard, '开始时间', queryStartInput);
    await fillDateTimePicker(formCard, '结束时间', queryEndInput);
}

function visibleReadinessFields(payload: MarketdataReadinessPayload): string[] {
    return [
        payload.status,
        payload.freshnessStatus,
        payload.sourceHealthStatus,
        payload.backendSupportLevel,
    ].filter((value): value is string => Boolean(value));
}

test.describe('marketdata readiness real backend smoke', () => {
    test('真实 bars + readiness API 驱动 MarketData K 线、成交量和 Data Quality 区域', async ({page}) => {
        test.setTimeout(150_000);

        const health = await checkBackendHealth();
        expect(health.available, `local backend unavailable for real marketdata readiness smoke: ${health.detail}`).toBeTruthy();

        await loginToConsole(page);
        const scenario = await findRealBackendScenario(page);
        console.info(
            [
                `marketdata-readiness-real-backend-smoke branch=${scenario.bars.length > 0 ? 'positive-bars' : 'empty-no-data'}`,
                `scope=${scenario.query.exchangeCode}/${scenario.query.marketType}/${scenario.query.symbol}/${scenario.query.interval}`,
                `preflightBars=${scenario.bars.length}`,
                `readinessBarCount=${scenario.readiness.barCount ?? 'unknown'}`,
                `readinessStatus=${scenario.readiness.status ?? 'unknown'}`,
            ].join(' '),
        );

        await page.getByRole('menuitem', {name: '行情查询'}).click();
        await expect(page).toHaveURL(/\/marketdata$/);
        await expect(page.getByRole('heading', {name: 'Marketdata'})).toBeVisible();

        const queryCard = page.locator('.ant-card').filter({hasText: '查询条件'});
        await fillBarsQuery(page, queryCard, scenario.query);

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
        expect(readinessPayload && typeof readinessPayload === 'object', 'page query must receive real readiness object payload').toBeTruthy();

        const chartPanel = page.getByTestId('marketdata-kline-readiness-view');
        const qualityPanel = page.getByTestId('marketdata-quality-readiness-view');
        await expect(chartPanel).toBeVisible();
        await expect(qualityPanel).toBeVisible();
        await expect(page.getByText('Data Quality / Readiness')).toBeVisible();

        await expect(chartPanel).toContainText(scenario.query.exchangeCode);
        await expect(chartPanel).toContainText(scenario.query.symbol);
        await expect(chartPanel).toContainText(scenario.query.interval);

        const kline = chartPanel.getByTestId('nq-kline-chart').filter({hasText: 'OHLCV K-line'}).first();
        const volume = chartPanel.getByTestId('nq-volume-chart').filter({hasText: 'Volume'}).first();
        await expect(kline).toBeVisible();
        await expect(volume).toBeVisible();

        await expect(qualityPanel).toContainText('from /api/marketdata/readiness');
        const readinessFields = visibleReadinessFields(readinessPayload);
        expect(readinessFields.length, 'real readiness payload must expose at least one displayable readiness field').toBeGreaterThan(0);
        const qualityText = await qualityPanel.innerText();
        expect(
            readinessFields.some((field) => qualityText.includes(field)),
            `Data Quality panel must display one backend readiness field from ${readinessFields.join(', ')}`,
        ).toBeTruthy();

        if (scenario.bars.length > 0) {
            expect(barsPayload.length).toBeGreaterThan(0);
            expect(readinessPayload.barCount ?? 0).toBeGreaterThan(0);
            await expect(kline.locator('canvas').first()).toBeVisible({timeout: 15_000});
            await expect(volume.locator('canvas').first()).toBeVisible({timeout: 15_000});
            await expect(qualityPanel).toContainText(new RegExp(`Bars loaded[\\s\\S]*${readinessPayload.barCount}`));
            await expect(qualityPanel).toContainText('Last bar time');
            if (readinessPayload.lastBarTime) {
                await expect(qualityPanel).toContainText(readinessPayload.lastBarTime);
            }
            await expect(qualityPanel).toContainText('Quality status');
            await expect(qualityPanel).toContainText('Gap count');
        } else {
            expect(barsPayload.length).toBe(0);
            expect(readinessPayload.barCount ?? 0).toBe(0);
            await expect(chartPanel).toContainText('当前查询没有返回 OHLCV bars');
            await expect(volume).toContainText('当前查询没有返回成交量 bars');
            await expect(qualityPanel).toContainText('No bars returned');
            await expect(qualityPanel).toContainText(/Bars loaded[\s\S]*0/);
            await expect(qualityPanel).toContainText('no data');
        }

        await expect(page.getByText('Marketdata bars 查询失败')).toHaveCount(0);
        await expect(page.getByText('MarketData source health unavailable')).toHaveCount(0);
    });
});
