import {expect, test, type Page, type Route} from 'playwright/test';

/**
 * GateM Runtime UI 5A backend-free smoke。
 *
 * Why:
 * `/runtime/readiness` 只允许消费只读 `GET /api/adapters/readiness`，展示 LIVE disabled、NoReal/Fake/Stub/FutureReal
 * blocked、permission probe disabled / skipped、MarketData readiness 入口和 PENDING_BACKEND_SUPPORT。该 smoke 明确记录
 * `/api/**` 写请求，防止页面误触发 permission probe POST、ingestion run-once、order/cancel/withdraw/transfer 等写端点。
 */

interface ReadinessItem {
    venue: string;
    capability: string;
    status: string;
    allowed: boolean;
    liveAuthorized: boolean;
    reasons: string[];
    message: string;
}

const SECRET_TOKENS = ['apiKey', 'api_key', 'secret', 'token', 'signature', 'passphrase', 'private key', 'mnemonic'];

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
                message: 'no-real / paper-only runtime; not real authorization',
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
            message: `${venue} is blocked for current GateM runtime`,
        });
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
                message: 'exchange adapter not authorized: no real provider / LIVE disabled',
            });
        }
    }

    return items;
}

async function seedAuthAndRuntimeStubs(page: Page): Promise<void> {
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'runtime-readiness-smoke-session',
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
        json: {generatedAt: '2026-06-30T00:00:00Z', items: buildReadinessItems()},
    }));
}

test.describe('runtime readiness overview', () => {
    test('展示 LIVE disabled、permission probe skipped、no-real blockers 和 MarketData readiness 入口', async ({page}) => {
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

        await seedAuthAndRuntimeStubs(page);

        const readinessResponse = page.waitForResponse((response) => (
            response.url().includes('/api/adapters/readiness')
            && response.request().method() === 'GET'
            && response.status() === 200
        ));

        await page.goto('/runtime/readiness');
        await readinessResponse;

        await expect(page.getByRole('heading', {name: 'Runtime Readiness Overview'})).toBeVisible();
        await expect(page.getByText('LIVE disabled').first()).toBeVisible();
        await expect(page.getByText('READY_FOR_PAPER_ONLY').first()).toBeVisible();
        await expect(page.getByText('NO_REAL').first()).toBeVisible();
        await expect(page.getByText('PERMISSION_PROBE_DISABLED / SKIPPED').first()).toBeVisible();
        await expect(page.getByText('NoReal / Fake / Stub / FutureReal').first()).toBeVisible();
        await expect(page.getByText('RealClient / real provider / real exchange adapter not implemented')).toBeVisible();
        await expect(page.getByText('PENDING_BACKEND_SUPPORT').first()).toBeVisible();
        await expect(page.getByRole('link', {name: 'Open MarketData'})).toHaveAttribute('href', '/marketdata?exchangeCode=BINANCE&marketType=SPOT&symbol=BTC-USDT&interval=1m');
        await expect(page.getByRole('link', {name: 'View MarketData readiness'})).toHaveAttribute('href', '/marketdata?exchangeCode=BINANCE&marketType=SPOT&symbol=BTC-USDT&interval=1m');

        await expect(page.getByText(/verified/i)).toHaveCount(0);
        await expect(page.getByText(/live-ready/i)).toHaveCount(0);
        await expect(page.getByText('LIVE 已授权')).toHaveCount(0);

        const bodyText = (await page.locator('body').innerText()).toLowerCase();
        for (const token of SECRET_TOKENS) {
            expect(bodyText.includes(token.toLowerCase()), `runtime page must not leak ${token}`).toBeFalsy();
        }

        expect(apiWrites, 'runtime readiness overview must not call write endpoints').toEqual([]);
        expect(apiRequests.some((entry) => entry.includes('permission-probe')), 'runtime page must not call permission probe endpoints').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('ingestion-jobs') && entry.includes('run-once')), 'runtime page must not trigger ingestion').toBeFalsy();
    });
});
