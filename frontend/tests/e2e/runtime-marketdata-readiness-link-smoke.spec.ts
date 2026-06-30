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
                message: 'paper-only / no-real runtime',
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
                    ? ['PERMISSION_PROBE_DISABLED', 'SKIPPED', 'LIVE_NOT_AUTHORIZED']
                    : ['CREDENTIAL_UNCONFIGURED', 'LIVE_NOT_AUTHORIZED'],
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
            reasons: [venue === 'FUTURE_REAL' ? 'FUTURE_REAL_DISABLED' : venue],
            message: `${venue} is not live-ready`,
        });
    }

    return items;
}

async function seedAuthAndRuntimeMarketdataStubs(page: Page): Promise<void> {
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'runtime-marketdata-link-smoke-session',
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
            defaultExchangeAccountId: null,
            defaultExchangeCode: null,
            defaultTradeEnv: null,
            defaultAccountAlias: null,
        },
    }));

    await page.route('**/api/exchange-accounts', (route: Route) => route.fulfill({
        status: 200,
        json: [],
    }));

    await page.route('**/api/adapters/readiness', (route: Route) => route.fulfill({
        status: 200,
        json: {generatedAt: '2026-06-30T08:00:00Z', items: buildReadinessItems()},
    }));
}

function formItem(page: Page, label: string) {
    return page.locator('.page-section').filter({hasText: '查询条件'}).locator('.ant-form-item').filter({hasText: label});
}

async function expectSelectValue(page: Page, label: string, value: string): Promise<void> {
    await expect(formItem(page, label).locator('.ant-select-selection-item')).toHaveText(value);
}

test.describe('runtime to marketdata readiness deep link', () => {
    test('runtime CTA opens MarketData with safe query prefill and no write endpoint calls', async ({page}) => {
        const apiWrites: string[] = [];
        const apiRequests: string[] = [];

        page.on('request', (request) => {
            const url = request.url();
            if (!url.includes('/api/')) {
                return;
            }
            apiRequests.push(`${request.method()} ${url}`);
            if (request.method() !== 'GET') {
                apiWrites.push(`${request.method()} ${url}`);
            }
        });

        await seedAuthAndRuntimeMarketdataStubs(page);

        await page.goto('/runtime/readiness');
        await expect(page.getByRole('heading', {name: 'Runtime Readiness Overview'})).toBeVisible();
        await expect(page.getByText('LIVE disabled').first()).toBeVisible();
        await expect(page.getByText('PERMISSION_PROBE_DISABLED / SKIPPED').first()).toBeVisible();

        await page.getByRole('link', {name: 'View MarketData readiness'}).click();

        await expect(page).toHaveURL(/\/marketdata\?exchangeCode=BINANCE&marketType=SPOT&symbol=BTC-USDT&interval=1m$/);
        await expect(page.getByRole('heading', {name: 'Marketdata'})).toBeVisible();
        await expect(page.getByTestId('marketdata-runtime-deep-link')).toContainText('Runtime readiness context applied');
        await expect(page.getByTestId('marketdata-runtime-deep-link')).toContainText('不会自动触发采集');

        await expectSelectValue(page, '交易所', 'BINANCE');
        await expectSelectValue(page, '市场', 'SPOT');
        await expectSelectValue(page, '交易对', 'BTC-USDT');
        await expectSelectValue(page, '周期', '1m');

        await expect(page.getByTestId('marketdata-kline-readiness-view')).toBeVisible();
        await expect(page.getByTestId('marketdata-quality-readiness-view')).toBeVisible();

        expect(apiWrites, 'runtime -> marketdata deep link must not call write endpoints').toEqual([]);
        expect(apiRequests.some((entry) => entry.includes('permission-probe')), 'must not call permission probe endpoints').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('ingestion-jobs') && entry.includes('run-once')), 'must not trigger ingestion run-once').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('/api/marketdata/bars')), 'deep link prefill must not auto query bars').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('/api/marketdata/readiness')), 'deep link prefill must not auto query readiness').toBeFalsy();
    });
});
