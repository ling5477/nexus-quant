import {expect, test, type Page, type Route} from 'playwright/test';

const simAccount = {
    exchangeAccountId: 101,
    legacyAccountId: 1,
    exchangeCode: 'BINANCE',
    tradeEnv: 'SIM',
    accountAlias: 'dashboard-runtime-sim',
    externalAccountRef: null,
    isDefault: true,
    status: 'ENABLED',
};

async function seedDashboardRuntimeStubs(page: Page): Promise<void> {
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'dashboard-runtime-readiness-session',
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
}

test.describe('dashboard runtime readiness summary', () => {
    test('dashboard shows read-only runtime summary links without write endpoint calls', async ({page}) => {
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

        await seedDashboardRuntimeStubs(page);

        await page.goto('/dashboard');
        await expect(page).toHaveURL(/\/dashboard$/);
        await expect(page.getByRole('heading', {name: '控制台总览'})).toBeVisible();

        const card = page.getByTestId('dashboard-runtime-readiness-card');
        await expect(card).toContainText('Runtime Readiness');
        await expect(card).toContainText('Runtime guarded: LIVE disabled');
        await expect(card).toContainText('LIVE');
        await expect(card).toContainText('Disabled');
        await expect(card).toContainText('Real provider');
        await expect(card).toContainText('Not implemented');
        await expect(card).toContainText('Paper');
        await expect(card).toContainText('Simulated only');
        await expect(card).toContainText('Permission probe');
        await expect(card).toContainText('Skipped / NoReal');
        await expect(card).toContainText('NoReal/Fake/Stub/FutureReal not live-ready.');
        await expect(card).toContainText('Permission probe SKIPPED / disabled is not verified.');
        await expect(card.getByRole('link', {name: 'View Runtime Readiness'})).toHaveAttribute('href', '/runtime/readiness');
        await expect(card.getByRole('link', {name: 'View MarketData Readiness'})).toHaveAttribute('href', '/marketdata');
        await expect(card.getByText(/LIVE ready/i)).toHaveCount(0);
        await expect(card.getByText(/Permission probe verified/i)).toHaveCount(0);

        expect(apiWrites, 'dashboard runtime summary must not call write endpoints').toEqual([]);
        expect(apiRequests.some((entry) => entry.includes('permission-probe')), 'must not call permission probe endpoints').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('ingestions/run-once')), 'must not trigger ingestion run-once').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('/trading/orders') && entry.startsWith('POST')), 'must not submit orders').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('/cancel')), 'must not cancel orders').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('/transfer')), 'must not call transfer endpoints').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('/withdraw')), 'must not call withdraw endpoints').toBeFalsy();
    });
});
