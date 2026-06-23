import {expect, test, type Locator, type Page} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

/**
 * GateM-5C adapter readiness panel —— 真实后端模式 smoke。
 *
 * Why:
 * GateM-5B 的 `adapter-readiness-panel-smoke.spec.ts` 用 route stub 证明前端渲染逻辑；本 spec 不做任何 readiness
 * stub，走真实登录链路（`loginToConsole` → 真实 `/api/auth/login`），让页面实际请求 GateM-5A 后端
 * `GET /api/adapters/readiness`（经 Vite `/api` 代理到本地 local 后端），证明前端确实消费真实后端 readiness 且仍
 * fail-closed：OKX/Binance NOT_READY / 不可用 / LIVE 未授权、Noop NO_REAL、permission probe 真实 provider 未实现，
 * 不显示 ready / 可用 / 可交易，不泄漏 secret。
 *
 * 运行前置：本地 local 后端（端口 18888，profile=local）需已启动；Vite dev server 由 run-e2e.mjs 拉起并代理 /api。
 * 不接真实交易所、不启用 LIVE、不读取真实 credential（readiness 为静态 fail-closed 决策，后端不外联）。
 */

const SECRET_TOKENS = ['secret', 'apikey', 'api_key', 'token', 'signature', 'passphrase'];

function venueCapabilityRow(page: Page, venue: string, capability: string): Locator {
    // 用 venue 精确单元格锁定真正的 venue 列，避免 reason 文案「Noop 无真实能力」让大小写不敏感的 hasText 误匹配。
    return page.getByRole('row')
        .filter({has: page.getByRole('cell', {name: venue, exact: true})})
        .filter({hasText: capability});
}

test.describe('adapter readiness panel (real backend)', () => {
    test('页面消费真实 GET /api/adapters/readiness 并 fail-closed 展示', async ({page}) => {
        await loginToConsole(page);

        // 在触发页面请求前挂监听，证明确有真实后端调用且返回 200。
        const readinessResponsePromise = page.waitForResponse(
            (response) => response.url().includes('/api/adapters/readiness') && response.request().method() === 'GET',
            {timeout: 30_000},
        );

        await page.getByRole('menuitem', {name: '适配器就绪'}).click();
        await expect(page).toHaveURL(/\/adapter-readiness$/);
        await expect(page.getByRole('heading', {name: 'Adapter Readiness'})).toBeVisible();

        // 真实后端响应断言：来自后端、200、payload 即 fail-closed 矩阵、无敏感字段。
        const readinessResponse = await readinessResponsePromise;
        expect(readinessResponse.status()).toBe(200);
        const payload = await readinessResponse.json();
        const items: Array<Record<string, unknown>> = payload.items ?? [];
        expect(items.length).toBeGreaterThan(0);
        expect(items.some((item) => item.allowed === true)).toBeFalsy();
        expect(items.some((item) => item.status === 'READY')).toBeFalsy();
        expect(items.some((item) => item.liveAuthorized === true)).toBeFalsy();
        const payloadLower = JSON.stringify(payload).toLowerCase();
        for (const token of SECRET_TOKENS) {
            expect(payloadLower.includes(token), `readiness payload must not leak ${token}`).toBeFalsy();
        }

        // 页面渲染断言：交易所未就绪、Noop NO_REAL、place/cancel 不可用、permission probe 真实 provider 未实现。
        const okxPlaceRow = venueCapabilityRow(page, 'OKX', 'PLACE_ORDER');
        await expect(okxPlaceRow).toContainText('未就绪 NOT_READY');
        await expect(okxPlaceRow).toContainText('不可用');
        await expect(okxPlaceRow).toContainText('LIVE 未授权');

        const binanceCancelRow = venueCapabilityRow(page, 'BINANCE', 'CANCEL_ORDER');
        await expect(binanceCancelRow).toContainText('未就绪 NOT_READY');
        await expect(binanceCancelRow).toContainText('不可用');
        await expect(binanceCancelRow).toContainText('LIVE 未授权');

        const okxProbeRow = venueCapabilityRow(page, 'OKX', 'PERMISSION_PROBE');
        await expect(okxProbeRow).toContainText('真实 provider 未实现');

        const noopRow = venueCapabilityRow(page, 'NOOP', 'PUBLIC_MARKETDATA');
        await expect(noopRow).toContainText('模拟·无真实 NO_REAL');

        // 全页不得出现 ready / 可用 / 可交易；不得泄漏 secret。
        await expect(page.getByText('就绪 READY')).toHaveCount(0);
        await expect(page.getByText('可用', {exact: true})).toHaveCount(0);
        const bodyText = (await page.locator('body').innerText()).toLowerCase();
        expect(bodyText.includes('可交易')).toBeFalsy();
        for (const token of SECRET_TOKENS) {
            expect(bodyText.includes(token), `panel must not leak ${token}`).toBeFalsy();
        }
    });
});
