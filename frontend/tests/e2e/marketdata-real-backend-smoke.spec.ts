import {expect, test, type Locator, type Page} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

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
    size: 5,
};

const candidateQueries: MarketdataBarsQuery[] = ['BINANCE', 'OKX'].flatMap((exchangeCode) =>
    ['BTC-USDT', 'ETH-USDT', 'SOL-USDT'].flatMap((symbol) =>
        ['1m', '5m', '15m', '1h', '4h', '1d'].map((interval) => ({
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
    expect(rawSession, 'real backend smoke requires browser login session').toBeTruthy();

    const session = JSON.parse(rawSession as string) as {accessToken?: string; tokenType?: string};
    expect(session.accessToken, 'real backend smoke requires an accessToken after login').toBeTruthy();
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

async function findRealBackendBarsScenario(page: Page): Promise<{query: MarketdataBarsQuery; bars: MarketdataBarPayload[]}> {
    const authorizationHeader = await readAuthorizationHeader(page);
    let defaultBars: MarketdataBarPayload[] | null = null;

    // Why: this preflight stays read-only and checks the fixed GateH/GateM bars dimensions
    // before deciding whether the UI smoke should assert positive chart canvases or empty/no-data state.
    for (const query of candidateQueries) {
        const bars = await listBars(page, authorizationHeader, query);
        if (query.exchangeCode === defaultQuery.exchangeCode
            && query.symbol === defaultQuery.symbol
            && query.interval === defaultQuery.interval) {
            defaultBars = bars;
        }
        if (bars.length > 0) {
            return {query, bars};
        }
    }

    return {query: defaultQuery, bars: defaultBars ?? []};
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

test.describe('marketdata real backend smoke', () => {
    test('真实 /api/marketdata/bars 驱动 K 线 / 成交量 / Data Quality readiness 渲染', async ({page}) => {
        test.setTimeout(120_000);

        const health = await checkBackendHealth();
        expect(health.available, `local backend unavailable for real bars smoke: ${health.detail}`).toBeTruthy();

        await loginToConsole(page);
        const scenario = await findRealBackendBarsScenario(page);

        const barsResponsePromise = page.waitForResponse((response) =>
            response.url().includes('/api/marketdata/bars') && response.request().method() === 'GET',
        );

        await page.getByRole('menuitem', {name: '行情查询'}).click();
        await expect(page).toHaveURL(/\/marketdata$/);
        await expect(page.getByRole('heading', {name: 'Marketdata'})).toBeVisible();

        const queryCard = page.locator('.ant-card').filter({hasText: '查询条件'});
        await fillBarsQuery(page, queryCard, scenario.query);
        await queryCard.getByRole('button', {name: /查\s*询/}).click();

        const barsResponse = await barsResponsePromise;
        expect(barsResponse.status()).toBe(200);
        const barsPayload = await barsResponse.json() as MarketdataBarPayload[];
        expect(Array.isArray(barsPayload), 'page query must receive real bars array payload').toBeTruthy();
        if (scenario.bars.length > 0) {
            expect(barsPayload.length).toBeGreaterThan(0);
        } else {
            expect(barsPayload.length).toBe(0);
        }

        const chartPanel = page.getByTestId('marketdata-kline-readiness-view');
        const qualityPanel = page.getByTestId('marketdata-quality-readiness-view');
        await expect(chartPanel).toBeVisible();
        await expect(qualityPanel).toBeVisible();

        await expect(chartPanel).toContainText(scenario.query.exchangeCode);
        await expect(chartPanel).toContainText(scenario.query.symbol);
        await expect(chartPanel).toContainText(scenario.query.interval);
        await expect(chartPanel).toContainText(/Bar count[\s\S]*\d+/);

        const kline = chartPanel.getByTestId('nq-kline-chart').filter({hasText: 'OHLCV K-line'}).first();
        const volume = chartPanel.getByTestId('nq-volume-chart').filter({hasText: 'Volume'}).first();
        await expect(kline).toBeVisible();
        await expect(volume).toBeVisible();

        await expect(qualityPanel).toContainText(/Bars loaded[\s\S]*\d+/);
        await expect(qualityPanel).toContainText('Last bar time');
        await expect(qualityPanel).toContainText('Freshness');
        await expect(qualityPanel).toContainText('Quality status');
        await expect(qualityPanel).toContainText('Gap count');
        await expect(qualityPanel).toContainText('source health: not available from current API');

        if (barsPayload.length > 0) {
            await expect(kline.locator('canvas').first()).toBeVisible({timeout: 15_000});
            await expect(volume.locator('canvas').first()).toBeVisible({timeout: 15_000});

            const firstQualityStatus = barsPayload
                .map((bar) => bar.qualityStatus?.trim())
                .find((status): status is string => Boolean(status));
            if (firstQualityStatus) {
                await expect(qualityPanel).toContainText(firstQualityStatus.toUpperCase());
            } else {
                await expect(qualityPanel).toContainText('qualityStatus unavailable');
            }
        } else {
            await expect(chartPanel).toContainText('当前查询没有返回 OHLCV bars');
            await expect(volume).toContainText('当前查询没有返回成交量 bars');
            await expect(qualityPanel).toContainText('No bars returned');
            await expect(qualityPanel).toContainText('no bars returned for the submitted window');
            await expect(qualityPanel).toContainText('qualityStatus unavailable');
        }

        await expect(page.getByText('Marketdata bars 查询失败')).toHaveCount(0);
    });
});
