import {expect, test, type Locator, type Page, type Route} from 'playwright/test';

/**
 * GateM-5B adapter readiness panel smoke。
 *
 * Why:
 * 该 panel 是只读安全边界视图，必须证明：OKX / Binance 明显 NOT_READY / not allowed、place/cancel 不显示为可用、
 * Noop 显示 NO_REAL、permission probe 显示 real provider 未实现、错误态显示 "readiness API unavailable" 且绝不回退成
 * ready，且页面不泄漏 secret。本 smoke 自带 stub（seed 登录态 + 拦截 /api/auth/me、/api/exchange-accounts、
 * /api/adapters/readiness），不依赖真实后端、真实交易所、真实凭证或 LIVE。
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
const REQUIRES_CREDENTIALS = new Set(['PLACE_ORDER', 'CANCEL_ORDER', 'QUERY_ORDER', 'ACCOUNT_BALANCE', 'PERMISSION_PROBE']);
const LIVE_MUTATING = new Set(['PLACE_ORDER', 'CANCEL_ORDER']);
const MARKET_DATA = new Set(['PUBLIC_MARKETDATA', 'SUBSCRIBE_BARS', 'SUBSCRIBE_TRADES', 'SUBSCRIBE_ORDERBOOK']);

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
                message: 'exchange adapter not authorized: endpoint sentinel / credential unconfigured / live disabled',
            });
        }
    }
    return items;
}

/**
 * 通过「venue 精确单元格 + capability 子串」定位唯一行。
 * Why: reasons 文案含 "Noop"，而 Playwright hasText 大小写不敏感，会让 hasText('NOOP') 误匹配 PAPER/SIM 行；
 * 用 venue 单元格精确名锁定真正的 venue 列。
 */
function venueCapabilityRow(page: Page, venue: string, capability: string): Locator {
    return page.getByRole('row')
        .filter({has: page.getByRole('cell', {name: venue, exact: true})})
        .filter({hasText: capability});
}

async function assertNoReadyOrSecret(page: Page): Promise<void> {
    // 绝不出现 READY / 可用 / 可交易 等可用语义。
    await expect(page.getByText('就绪 READY')).toHaveCount(0);
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
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'e2e-stub-session',
            tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z',
            username: 'e2e-operator',
            roles: ['ADMIN'],
        }));
    });

    // 兜底：任何未显式 stub 的后端 /api 请求返回空数组，避免打到真实后端。
    // 用 origin 锚定的正则只匹配 `http(s)://host/api/...`，避免误伤 Vite dev 的 `/src/api/*` 源码模块。
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
}

test.describe('adapter readiness panel', () => {
    test('展示 OKX/Binance 未就绪、不可用、LIVE 未授权且不暗示可交易', async ({page}) => {
        await seedAuthAndCommonStubs(page);
        await page.route('**/api/adapters/readiness', (route: Route) => route.fulfill({
            status: 200,
            json: {generatedAt: '2026-06-23T00:00:00Z', items: buildReadinessItems()},
        }));

        await page.goto('/adapter-readiness');

        await expect(page.getByRole('heading', {name: 'Adapter Readiness'})).toBeVisible();
        await expect(page.getByText('当前所有交易所适配器未就绪（NOT READY / NOT FROZEN / NOT AUTHORIZED）')).toBeVisible();

        // OKX PLACE_ORDER 行：未就绪 / 不可用 / LIVE 未授权。
        const okxPlaceRow = venueCapabilityRow(page, 'OKX', 'PLACE_ORDER');
        await expect(okxPlaceRow).toContainText('未就绪 NOT_READY');
        await expect(okxPlaceRow).toContainText('不可用');
        await expect(okxPlaceRow).toContainText('LIVE 未授权');

        // BINANCE CANCEL_ORDER 行：同样 fail-closed。
        const binanceCancelRow = venueCapabilityRow(page, 'BINANCE', 'CANCEL_ORDER');
        await expect(binanceCancelRow).toContainText('未就绪 NOT_READY');
        await expect(binanceCancelRow).toContainText('不可用');
        await expect(binanceCancelRow).toContainText('LIVE 未授权');

        // permission probe：真实 provider 未实现。
        const okxProbeRow = venueCapabilityRow(page, 'OKX', 'PERMISSION_PROBE');
        await expect(okxProbeRow).toContainText('真实 provider 未实现');

        // Noop 家族：模拟·无真实 NO_REAL。
        const noopRow = venueCapabilityRow(page, 'NOOP', 'PUBLIC_MARKETDATA');
        await expect(noopRow).toContainText('模拟·无真实 NO_REAL');

        await assertNoReadyOrSecret(page);
    });

    test('readiness API 失败时显示 unavailable 且不回退成 ready', async ({page}) => {
        await seedAuthAndCommonStubs(page);
        await page.route('**/api/adapters/readiness', (route: Route) => route.fulfill({
            status: 500,
            json: {code: 'INTERNAL_ERROR', message: 'readiness backend unavailable'},
        }));

        await page.goto('/adapter-readiness');

        await expect(page.getByRole('heading', {name: 'Adapter Readiness'})).toBeVisible();
        await expect(page.getByText('readiness API unavailable')).toBeVisible();
        await expect(page.getByText('未就绪（fail-closed）')).toBeVisible();

        await assertNoReadyOrSecret(page);
    });
});
