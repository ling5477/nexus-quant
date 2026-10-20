import {expect, test, type Page} from 'playwright/test';
import {loginForReadOnlyChecks} from './read-only-console-support';

// 同文件含正式登录用例，不将真实隔离密码或认证请求写入trace。
test.use({trace: 'off'});

/** 边界fixture只验证前端行为；下方正式联调测试不拦截NQ业务API。 */
async function seedCurrencyFixture(page: Page, accountId: number | null, status = 'ACTIVE') {
    await page.addInitScript(() => {
        localStorage.setItem('nq.locale', 'zh-CN');
        localStorage.setItem(
            'nexus-quant.console.auth',
            JSON.stringify({
                accessToken: 'e2e-currency-fixture',
                tokenType: 'Bearer',
                expiresAt: '2999-01-01T00:00:00Z',
                username: 'currency-check',
                roles: ['ADMIN'],
            }),
        );
    });
    await page.route(/^https?:\/\/[^/]+\/api\//, (route) => route.fulfill({status: 200, json: []}));
    await page.route('**/api/auth/me', (route) =>
        route.fulfill({
            status: 200,
            json: {
                userId: 1,
                username: 'currency-check',
                roles: ['ADMIN'],
                authenticated: true,
                defaultExchangeAccountId: accountId,
                defaultExchangeCode: 'OKX',
                defaultTradeEnv: 'SIM',
                defaultAccountAlias: '计价测试',
            },
        }),
    );
    await page.route('**/api/user/preferences', (route) =>
        route.fulfill({
            status: 200,
            json: {
                version: 0,
                locale: 'zh-CN',
                hideLoginDisclosures: true,
                defaultMarket: {exchangeCode: 'OKX', marketType: 'SPOT', symbol: 'BTC-USDT'},
                watchlist: [],
                notificationsReadAt: null,
            },
        }),
    );
    await page.route('**/api/user/notifications?*', (route) =>
        route.fulfill({
            status: 200,
            json: {
                items: [],
                offset: 0,
                hasMore: false,
                windowLimited: false,
                checkedAt: '2026-06-23T00:00:00Z',
                scope: 'OWNED_SIM_RUN_EVENTS',
            },
        }),
    );
    await page.route('**/api/system/version', (route) =>
        route.fulfill({
            status: 200,
            json: {
                serviceVersion: '0.1.0-SNAPSHOT',
                serviceArtifact: 'nq-app',
                installedPackageVersion: null,
                sourceCommit: null,
                releaseSourceHash: null,
                packageManifestSha256: null,
                packagedSchemaVersion: null,
                packageIdentityStatus: 'UNAVAILABLE',
                automaticUpdate: false,
                updateExecutor: 'HOST_INSTALLER',
                reasons: ['INSTALLED_PACKAGE_INFO_UNAVAILABLE'],
            },
        }),
    );
    await page.route('**/api/exchange-accounts', (route) =>
        route.fulfill({
            status: 200,
            json: accountId
                ? [
                      {
                          exchangeAccountId: accountId,
                          exchangeCode: 'OKX',
                          tradeEnv: 'SIM',
                          accountAlias: '计价测试',
                          isDefault: true,
                          status,
                      },
                  ]
                : [],
        }),
    );
}

test.describe('默认账户计价边界fixture', () => {
    test('无有效默认账户不发起估值读取，也不以默认交易对猜币种', async ({page}) => {
        await seedCurrencyFixture(page, null);
        let reads = 0;
        await page.route('**/api/exchange-accounts/*/performance?*', (route) => {
            reads++;
            return route.fulfill({status: 500, json: {}});
        });
        await page.goto('/settings');
        await expect(page.getByText('请先选择有效的默认账户。')).toBeVisible();
        expect(reads).toBe(0);
    });
    test('错误账户或不支持币种不能展示为默认计价；重读恢复正式USDT', async ({page}) => {
        await seedCurrencyFixture(page, 7);
        let reads = 0;
        await page.route('**/api/exchange-accounts/7/performance?*', (route) => {
            reads++;
            return route.fulfill({
                status: 200,
                json: {
                    summary: {
                        exchangeAccountId: reads === 1 ? 8 : 7,
                        quoteCurrency: reads === 1 ? 'CNY' : 'USDT',
                        samples: [],
                    },
                },
            });
        });
        await page.goto('/settings');
        await expect(page.getByText('当前无法读取默认账户的资产计价口径。')).toBeVisible();
        await expect(page.getByText('CNY', {exact: true})).toHaveCount(0);
        await page
            .getByRole('alert')
            .filter({hasText: '当前无法读取默认账户的资产计价口径。'})
            .getByRole('button', {name: '重新读取', exact: true})
            .click();
        await expect(page.getByText('USDT', {exact: true})).toBeVisible();
        expect(reads).toBe(2);
    });
    test('停用的默认账户不读取旧估值结果', async ({page}) => {
        await seedCurrencyFixture(page, 7, 'DISABLED');
        let reads = 0;
        await page.route('**/api/exchange-accounts/7/performance?*', (route) => {
            reads++;
            return route.fulfill({
                status: 200,
                json: {summary: {exchangeAccountId: 7, quoteCurrency: 'USDT'}},
            });
        });
        await page.goto('/settings');
        await expect(page.getByText('请先选择有效的默认账户。')).toBeVisible();
        expect(reads).toBe(0);
    });
});

test('正式账户计价读取指定默认账户，期间有界且与默认查看交易对分离', async ({page}) => {
    const texts = await loginForReadOnlyChecks(page);
    const responsePromise = page.waitForResponse(
        (response) =>
            /\/api\/exchange-accounts\/\d+\/performance\?/.test(response.url()) &&
            response.request().method() === 'GET',
    );
    await page.goto('/settings');
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    const result = await response.json();
    const url = new URL(response.url());
    expect(Number(url.pathname.split('/')[3])).toBe(result.summary.exchangeAccountId);
    expect(url.searchParams.get('valuationCurrency')).toBe('USDT');
    expect(
        Date.parse(url.searchParams.get('to')!) - Date.parse(url.searchParams.get('from')!),
    ).toBe(60000);
    expect(result.summary.quoteCurrency).toBe('USDT');
    await expect(page.getByText(texts.defaultAssets.title, {exact: true})).toBeVisible();
    await expect(page.getByText('USDT', {exact: true})).toBeVisible();
    await page.getByRole('button', {name: texts.markets.editWatchlist, exact: true}).click();
    const dialog = page.getByRole('dialog', {name: texts.markets.editWatchlist, exact: true});
    await expect(
        dialog.getByRole('combobox', {name: texts.markets.defaultMarket, exact: true}),
    ).toBeVisible();
    await dialog.getByRole('button', {name: /^(取\s*消|Cancel)$/}).click();
    await expect(dialog).not.toBeVisible();
});
