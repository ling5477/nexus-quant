import {expect, test, type Page, type Route} from 'playwright/test';
import {capabilityRow, selectCapabilityVenue} from './read-only-console-support';

/**
 * 本组使用明确fixture验证翻译、筛选、未知能力和失败关闭；正式接口联调由独立的backend smoke证明。
 * 原始状态、执行许可与LIVE授权分别核对，不能因业务文案调整弱化权限断言。
 */

const SECRET_TOKENS = ['secret', 'apikey', 'api_key', 'token', 'signature', 'passphrase'];

const NO_REAL_VENUES = ['NOOP', 'PAPER', 'SIM'];
const EXCHANGE_VENUES = ['OKX', 'BINANCE'];
const CAPABILITIES = [
    'PUBLIC_MARKETDATA',
    'SUBSCRIBE_BARS',
    'SUBSCRIBE_TRADES',
    'SUBSCRIBE_ORDERBOOK',
    'PLACE_ORDER',
    'CANCEL_ORDER',
    'QUERY_ORDER',
    'ACCOUNT_BALANCE',
    'PERMISSION_PROBE',
];

// 镜像后端 GateM-0 capability 属性，构造与真实 readiness 一致的 fail-closed fixture。
const REQUIRES_CREDENTIALS = new Set([
    'PLACE_ORDER',
    'CANCEL_ORDER',
    'QUERY_ORDER',
    'ACCOUNT_BALANCE',
    'PERMISSION_PROBE',
]);
const LIVE_MUTATING = new Set(['PLACE_ORDER', 'CANCEL_ORDER']);
const MARKET_DATA = new Set([
    'PUBLIC_MARKETDATA',
    'SUBSCRIBE_BARS',
    'SUBSCRIBE_TRADES',
    'SUBSCRIBE_ORDERBOOK',
]);

interface ReadinessItem {
    venue: string;
    capability: string;
    status: string;
    allowed: boolean;
    liveAuthorized: boolean;
    reasons: string[];
    message: string;
}

function exchangeReasons(capability: string): string[] {
    const reasons = ['ENDPOINT_DISABLED_SENTINEL'];
    if (REQUIRES_CREDENTIALS.has(capability)) {
        reasons.push('CREDENTIALS_MISSING');
    }
    if (LIVE_MUTATING.has(capability)) {
        reasons.push('LIVE_DISABLED');
    }
    if (capability === 'PERMISSION_PROBE' || MARKET_DATA.has(capability)) {
        reasons.push('REAL_PROVIDER_NOT_IMPLEMENTED');
    }
    return reasons;
}

function buildReadinessItems(): ReadinessItem[] {
    const items: ReadinessItem[] = [];
    for (const venue of NO_REAL_VENUES) {
        for (const capability of CAPABILITIES) {
            items.push({
                venue,
                capability,
                status: 'NO_REAL',
                allowed: false,
                liveAuthorized: false,
                reasons: ['NO_REAL_DISABLED'],
                message: 'no-real stub adapter; not a real provider',
            });
        }
    }
    for (const venue of EXCHANGE_VENUES) {
        for (const capability of CAPABILITIES) {
            items.push({
                venue,
                capability,
                status: 'NOT_READY',
                allowed: false,
                liveAuthorized: false,
                reasons: exchangeReasons(capability),
                message:
                    'exchange adapter not authorized: endpoint sentinel / credential unconfigured / live disabled',
            });
        }
    }
    return items;
}

/** 失败策略不得出现就绪/授权语义或敏感值；详情展开后也受同一断言约束。 */
async function assertNoReadyOrSecret(page: Page): Promise<void> {
    // 绝不出现 READY / 可用 / 可交易 等可用语义。
    await expect(page.getByText('就绪', {exact: true})).toHaveCount(0);
    await expect(page.getByText('允许', {exact: true})).toHaveCount(0);
    await expect(page.getByText('已授权', {exact: true})).toHaveCount(0);
    await expect(page.getByText('可用', {exact: true})).toHaveCount(0);
    const bodyText = (await page.locator('body').innerText()).toLowerCase();
    expect(bodyText.includes('可交易')).toBeFalsy();
    for (const token of SECRET_TOKENS) {
        expect(bodyText.includes(token), `panel must not leak ${token}`).toBeFalsy();
    }
}

async function seedAuthAndCommonStubs(page: Page): Promise<void> {
    // 预置登录态，跳过真实登录链路（不接触真实后端 / 凭证）。
    await page.addInitScript(() => {
        window.localStorage.setItem('nq.locale', 'zh-CN');
        window.localStorage.setItem(
            'nexus-quant.console.auth',
            JSON.stringify({
                accessToken: 'e2e-stub-session',
                tokenType: 'Bearer',
                expiresAt: '2999-01-01T00:00:00Z',
                username: 'e2e-operator',
                roles: ['ADMIN'],
            }),
        );
    });

    // 兜底：任何未显式 stub 的后端 /api 请求返回空数组，避免打到真实后端。
    // 用 origin 锚定的正则只匹配 `http(s)://host/api/...`，避免误伤 Vite dev 的 `/src/api/*` 源码模块。
    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) =>
        route.fulfill({status: 200, json: []}),
    );

    await page.route('**/api/auth/me', (route: Route) =>
        route.fulfill({
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
        }),
    );
    await page.route('**/api/user/preferences', (route) =>
        route.fulfill({
            status: 200,
            json: {
                version: 0,
                locale: 'zh-CN',
                hideLoginDisclosures: true,
                defaultMarket: null,
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
}

test.describe('adapter readiness panel', () => {
    test('展示 OKX/Binance 未就绪、不可用、LIVE 未授权且不暗示可交易', async ({page}) => {
        await seedAuthAndCommonStubs(page);
        await page.route('**/api/adapters/readiness', (route: Route) =>
            route.fulfill({
                status: 200,
                json: {generatedAt: '2026-06-23T00:00:00Z', items: buildReadinessItems()},
            }),
        );

        await page.goto('/adapter-readiness');

        await expect(page.getByRole('heading', {name: '交易所与适配器能力'})).toBeVisible();
        await expect(page.getByText('当前对象 45 项 · 总计 45 项')).toBeVisible();

        // OKX PLACE_ORDER 行：未就绪 / 不可用 / LIVE 未授权。
        const okxPlaceRow = capabilityRow(page, 'OKX', 'PLACE_ORDER');
        await expect(okxPlaceRow).toContainText('未就绪');
        await expect(okxPlaceRow).toContainText('不允许');
        await expect(okxPlaceRow).toContainText('未授权');
        await expect(capabilityRow(page, 'OKX', 'PERMISSION_PROBE')).toContainText(
            '真实连接或权限检查未实现',
        );
        await okxPlaceRow.getByRole('button', {name: '查看详情', exact: true}).click();
        const detail = page.getByRole('dialog', {name: 'OKX · 发送订单', exact: true});
        await detail.getByRole('button', {name: '查看原始状态'}).click();
        await expect(detail).toContainText('NOT_READY');
        await expect(detail).toContainText('"allowed":false');
        await expect(detail).toContainText('"liveAuthorized":false');
        for (const reason of exchangeReasons('PLACE_ORDER'))
            await expect(detail).toContainText(reason);
        await detail.getByRole('button', {name: '关闭', exact: true}).click();
        await expect(detail).not.toBeVisible();

        // BINANCE CANCEL_ORDER 行：同样 fail-closed。
        await selectCapabilityVenue(page, 'BINANCE');
        const binanceCancelRow = capabilityRow(page, 'BINANCE', 'CANCEL_ORDER');
        await expect(binanceCancelRow).toContainText('未就绪');
        await expect(binanceCancelRow).toContainText('不允许');
        await expect(binanceCancelRow).toContainText('未授权');

        // permission probe：真实 provider 未实现。
        for (const venue of NO_REAL_VENUES) {
            await selectCapabilityVenue(page, venue);
            for (const capability of CAPABILITIES)
                await expect(capabilityRow(page, venue, capability)).toContainText('无真实连接');
        }

        await assertNoReadyOrSecret(page);
    });

    test('readiness API 失败时显示 unavailable 且不回退成 ready', async ({page}) => {
        await seedAuthAndCommonStubs(page);
        await page.route('**/api/adapters/readiness', (route: Route) =>
            route.fulfill({
                status: 500,
                json: {code: 'INTERNAL_ERROR', message: 'readiness backend unavailable'},
            }),
        );

        await page.goto('/adapter-readiness');

        await expect(page.getByRole('heading', {name: '交易所与适配器能力'})).toBeVisible();
        await expect(page.getByText('无法读取当前策略快照，不能确认任何调用许可。')).toBeVisible();
        await expect(page.locator('tr[data-venue]')).toHaveCount(0);

        await assertNoReadyOrSecret(page);
    });
    test('未知能力保留原始身份并显示待核查，不能丢行或推断授权', async ({page}) => {
        await seedAuthAndCommonStubs(page);
        await page.route('**/api/adapters/readiness', (route) =>
            route.fulfill({
                status: 200,
                json: {
                    generatedAt: '2026-06-23T00:00:00Z',
                    items: [
                        {
                            venue: 'NEW_VENUE',
                            capability: 'NEW_CAPABILITY',
                            status: 'NEW_STATUS',
                            allowed: false,
                            liveAuthorized: false,
                            reasons: ['NEW_REASON'],
                            message: 'unrecognized capability',
                        },
                    ],
                },
            }),
        );
        await page.goto('/adapter-readiness');
        const row = capabilityRow(page, 'NEW_VENUE', 'NEW_CAPABILITY');
        await expect(row).toContainText('未知，待核查');
        await row.getByRole('button', {name: '查看详情', exact: true}).click();
        const detail = page.getByRole('dialog');
        await detail.getByRole('button', {name: '查看原始状态'}).click();
        for (const value of [
            'NEW_VENUE',
            'NEW_CAPABILITY',
            'NEW_STATUS',
            'NEW_REASON',
            '"allowed":false',
            '"liveAuthorized":false',
        ])
            await expect(detail).toContainText(value);
        await assertNoReadyOrSecret(page);
    });
});
