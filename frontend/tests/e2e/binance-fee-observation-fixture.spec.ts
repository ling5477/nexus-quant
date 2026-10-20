import {expect, test, type Page} from 'playwright/test';
import zh from '@/i18n/locales/zh-CN/pages.json' with {type: 'json'};
import en from '@/i18n/locales/en-US/pages.json' with {type: 'json'};

// 全部响应是明示页面fixture，无正式登录、账户创建、费率同步或私有供应方访问。
test.use({trace: 'off'});
const accountId = 900001;
const rates = {
    maker: '-0.000000000001',
    taker: '0.001000000000',
    buyer: '0.000200000000',
    seller: '0.000300000000',
};

async function setup(page: Page, locale: 'zh-CN' | 'en-US', env = 'LIVE') {
    let writes = 0;
    const requestedTypes: string[] = [];
    await page.addInitScript((language) => {
        localStorage.setItem('nq.locale', language);
        localStorage.setItem(
            'nexus-quant.console.auth',
            JSON.stringify({
                accessToken: 'synthetic-fee-ui-session',
                tokenType: 'Bearer',
                expiresAt: '2999-01-01T00:00:00Z',
                username: 'fee-ui-fixture',
                roles: ['OPERATOR'],
            }),
        );
    }, locale);
    await page.route(/\/api\//, async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const path = url.pathname;
        if (!['GET', 'HEAD'].includes(request.method())) {
            writes++;
            return route.fulfill({status: 405, json: {code: 'FIXTURE_READ_ONLY'}});
        }
        const account = {
            exchangeAccountId: accountId,
            ownerUserId: 1,
            legacyAccountId: null,
            exchangeCode: 'BINANCE',
            tradeEnv: env,
            accountAlias: 'BINANCE fee UI fixture',
            externalAccountRef: null,
            isDefault: false,
            status: 'ACTIVE',
        };
        if (path === '/api/auth/me')
            return route.fulfill({
                json: {
                    userId: 1,
                    username: 'fee-ui-fixture',
                    roles: ['OPERATOR'],
                    authenticated: true,
                    defaultExchangeAccountId: null,
                    defaultExchangeCode: null,
                    defaultTradeEnv: null,
                    defaultAccountAlias: null,
                },
            });
        if (path === '/api/user/preferences')
            return route.fulfill({
                json: {
                    locale,
                    hideLoginDisclosures: true,
                    revision: 0,
                    watchlist: [],
                    defaultExchangeCode: 'BINANCE',
                    defaultSymbol: 'BTC-USDT',
                    notificationsReadAt: null,
                },
            });
        if (path === '/api/user/notifications')
            return route.fulfill({
                json: {
                    items: [],
                    offset: 0,
                    hasMore: false,
                    windowLimited: false,
                    checkedAt: new Date().toISOString(),
                    scope: 'OWNED_SIM_RUN_EVENTS',
                },
            });
        if (path === '/api/exchange-accounts') return route.fulfill({json: [account]});
        if (path === `/api/exchange-accounts/${accountId}/credentials/active`) {
            const type = url.searchParams.get('credentialType') ?? '';
            requestedTypes.push(type);
            return route.fulfill({
                json: {
                    exchangeAccountId: accountId,
                    activeCredential: {
                        credentialId: type === 'BINANCE_ED25519' ? 17 : 7,
                        exchangeAccountId: accountId,
                        credentialType: type,
                        maskedAccessKey: 'syn***ey',
                        isActive: true,
                        credentialStatus: 'ACTIVE',
                        verificationStatus: 'VERIFIED',
                    },
                },
            });
        }
        if (path === '/api/instruments')
            return route.fulfill({
                json: [
                    {
                        instrumentId: 1,
                        exchangeCode: 'BINANCE',
                        instrumentType: 'SPOT',
                        exchangeSymbol: 'BTCUSDT',
                        internalSymbol: 'BTC-USDT',
                        baseAsset: 'BTC',
                        quoteAsset: 'USDT',
                        status: 'TRADING',
                    },
                ],
            });
        if (path === `/api/exchange-accounts/${accountId}/fees`) {
            const observedAt = new Date().toISOString();
            return route.fulfill({
                json: {
                    items: [
                        {
                            observationId: '06c639e1-a738-4e2d-9f82-19a455555bf6',
                            operationId: '07c639e1-a738-4e2d-9f82-19a455555bf6',
                            exchangeAccountId: accountId,
                            credentialId: 7,
                            exchangeCode: 'BINANCE',
                            tradeEnv: env,
                            symbol: 'BTC-USDT',
                            source: 'BINANCE_PRIVATE_SPOT_COMMISSION',
                            rateConvention: 'BINANCE_POSITIVE_COST_V1',
                            makerProviderRate: null,
                            takerProviderRate: null,
                            makerCostRate: null,
                            takerCostRate: null,
                            tier: null,
                            providerAt: null,
                            providerTimeBasis: 'NOT_PROVIDED',
                            observedAt,
                            expiresAt: new Date(Date.now() + 300000).toISOString(),
                            sourceSha256: 'a'.repeat(64),
                            status: 'SUCCEEDED',
                            scope: 'CURRENT_ACCOUNT_AT_OBSERVATION',
                            commissionTerms: {
                                nativeSymbol: 'BTCUSDT',
                                standard: rates,
                                tax: {
                                    ...rates,
                                    maker: '0.000000000000',
                                    taker: '0.000000000000',
                                    buyer: '0.000000000000',
                                    seller: '0.000000000000',
                                },
                                special: rates,
                                discount: {
                                    enabledForAccount: true,
                                    enabledForSymbol: false,
                                    asset: 'BNB',
                                    multiplier: '0.250000000000',
                                },
                                responseSha256: 'b'.repeat(64),
                            },
                        },
                    ],
                    hasMore: false,
                    nextBefore: null,
                    synchronizationAvailable: false,
                    generatedAt: observedAt,
                    scope: 'CURRENT_OBSERVATION_NOT_HISTORICAL_FEE',
                },
            });
        }
        return route.fulfill({status: 404, json: {code: 'NOT_FOUND'}});
    });
    await page.goto('/accounts');
    const texts = locale === 'zh-CN' ? zh : en;
    const row = page.getByRole('row').filter({hasText: 'BINANCE fee UI fixture'});
    await row.getByRole('button', {name: texts.accountFees.title, exact: true}).click();
    const drawer = page.getByRole('dialog');
    await drawer
        .getByRole('combobox', {name: texts.accountFees.symbol, exact: true})
        .press('Enter');
    await page.getByText('BTC-USDT', {exact: true}).last().click();
    await drawer.getByRole('button', {name: texts.accountFees.details, exact: true}).click();
    return {texts, drawer, requestedTypes, writes: () => writes};
}

for (const locale of ['zh-CN', 'en-US'] as const)
    for (const width of [1440, 390]) {
        test(`明示fixture：完整Binance费用分量、缺失时点与优惠边界 ${locale} ${width}px`, async ({
            page,
        }, info) => {
            await page.setViewportSize({width, height: 950});
            const {texts, drawer, writes} = await setup(page, locale);
            await expect(
                drawer.getByText(texts.accountFees.commissionFormula, {exact: true}),
            ).toBeVisible();
            await expect(
                drawer
                    .getByRole('row')
                    .filter({hasText: texts.accountFees.commission_standard})
                    .getByText('-0.0000000001%', {exact: true}),
            ).toBeVisible();
            await expect(
                drawer.getByText(texts.accountFees.discountBoundary, {exact: true}),
            ).toBeVisible();
            await expect(
                drawer.getByText(texts.accountFees.directionalConsumptionPending, {exact: true}),
            ).toBeVisible();
            await expect(
                drawer.getByText(texts.accountFees.notProvided, {exact: true}),
            ).toHaveCount(2);
            await expect(
                drawer.getByRole('button', {name: texts.accountFees.sync, exact: true}),
            ).toBeDisabled();
            expect(writes()).toBe(0);
            const bounds = await drawer.boundingBox();
            expect(bounds!.width).toBeLessThanOrEqual(width);
            expect(bounds!.x).toBeGreaterThanOrEqual(0);
            await page.screenshot({
                path: info.outputPath('native-fee-fixture.png'),
                fullPage: false,
            });
        });
    }
test('明示fixture：凭据类型有标签并精确选择版本，SIM说明真实来源限制', async ({page}) => {
    const {texts, drawer, requestedTypes, writes} = await setup(page, 'zh-CN', 'SIM');
    await expect(
        drawer.getByText(texts.accountFees.binanceTestnetUnavailable, {exact: true}),
    ).toBeVisible();
    await drawer
        .getByRole('combobox', {name: texts.accountFees.credentialType, exact: true})
        .press('Enter');
    await page
        .getByText(texts.credentialManager.type_BINANCE_ED25519, {exact: true})
        .last()
        .click();
    await expect.poll(() => requestedTypes.at(-1)).toBe('BINANCE_ED25519');
    await expect(drawer.getByText(/凭据版本 #17/)).toBeVisible();
    expect(requestedTypes).not.toContain('OKX_API_V5');
    expect(writes()).toBe(0);
});
