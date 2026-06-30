import {expect, test, type Page, type Route} from 'playwright/test';

const simAccount = {
    exchangeAccountId: 101,
    legacyAccountId: 1,
    exchangeCode: 'BINANCE',
    tradeEnv: 'SIM',
    accountAlias: 'runtime-guard-sim',
    externalAccountRef: null,
    isDefault: true,
    status: 'ENABLED',
};

async function seedAuthAndGuardStubs(page: Page): Promise<void> {
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'runtime-paper-boundary-smoke-session',
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

    await page.route(/^https?:\/\/[^/]+\/api\/paper-trading\/runs(?:\?.*)?$/, (route: Route) => route.fulfill({
        status: 200,
        json: [],
    }));

    await page.route(/^https?:\/\/[^/]+\/api\/trading\/orders(?:\?.*)?$/, (route: Route) => route.fulfill({
        status: 200,
        json: {items: [], page: 0, size: 20, total: 0},
    }));
}

test.describe('runtime paper and trading boundary banners', () => {
    test('paper and trading pages show read-only runtime guard banners without write endpoint calls', async ({page}) => {
        const apiWrites: string[] = [];
        const apiRequests: string[] = [];

        page.on('request', (request) => {
            const url = request.url();
            if (!url.includes('/api/')) {
                return;
            }
            const entry = `${request.method()} ${url}`;
            apiRequests.push(entry);
            if (request.method() !== 'GET') {
                apiWrites.push(entry);
            }
        });

        await seedAuthAndGuardStubs(page);

        await page.goto('/paper-trading');
        await expect(page).toHaveURL(/\/paper-trading\/runs$/);
        const paperBanner = page.getByTestId('paper-real-boundary-banner');
        await expect(paperBanner).toContainText('Paper-only boundary');
        await expect(paperBanner).toContainText('Paper Trading is simulated.');
        await expect(paperBanner).toContainText('Paper order ≠ real order.');
        await expect(paperBanner).toContainText('Paper fill ≠ real fill.');
        await expect(paperBanner).toContainText('Paper balance/position ≠ real account balance/position.');
        await expect(paperBanner).toContainText('Paper risk pass ≠ LIVE authorization.');
        await expect(paperBanner).toContainText('permission probe SKIPPED do not authorize LIVE trading.');
        await expect(page.getByText(/LIVE ready/i)).toHaveCount(0);
        await expect(page.getByText(/Permission probe verified/i)).toHaveCount(0);

        await page.goto('/trading');
        await expect(page).toHaveURL(/\/trading$/);
        const tradingBanner = page.getByTestId('runtime-guarded-live-disabled-banner');
        await expect(tradingBanner).toContainText('Runtime guarded: LIVE disabled');
        await expect(tradingBanner).toContainText('LIVE disabled.');
        await expect(tradingBanner).toContainText('Real provider not implemented.');
        await expect(tradingBanner).toContainText('NoReal/Fake/Stub/FutureReal not live-ready.');
        await expect(tradingBanner).toContainText('Permission probe SKIPPED / disabled is not verified.');
        await expect(page.getByText(/LIVE ready/i)).toHaveCount(0);
        await expect(page.getByText(/Permission probe verified/i)).toHaveCount(0);

        expect(apiWrites, 'guard banners must not call write endpoints').toEqual([]);
        expect(apiRequests.some((entry) => entry.includes('permission-probe')), 'must not call permission probe endpoints').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('ingestions/run-once')), 'must not trigger ingestion run-once').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('/transfer')), 'must not call transfer endpoints').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('/withdraw')), 'must not call withdraw endpoints').toBeFalsy();
    });
});
