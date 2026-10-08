import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {expect, test} from 'playwright/test';

const disclaimer = readFileSync(new URL('../../../DISCLAIMER.md', import.meta.url), 'utf8');
const notices = readFileSync(new URL('../../../THIRD_PARTY_NOTICES.md', import.meta.url), 'utf8');

for (const marketdataState of ['empty', 'unavailable'] as const) {
    test(`无行情且外网不可用时完整金融风险声明仍可访问：${marketdataState}`, async ({page}) => {
        const errors: string[] = [];
        const externalRequests: string[] = [];
        page.on('pageerror', error => errors.push(error.message));
        // 仅替换鉴权和只读 API；真实页面及 canonical 原文由构建产物提供。
        await page.route('**/*', async route => {
            const url = new URL(route.request().url());
            if (url.origin !== new URL(test.info().project.use.baseURL!).origin) {
                externalRequests.push(url.origin);
                return route.abort();
            }
            if (!url.pathname.startsWith('/api/')) return route.continue();
            expect(route.request().method()).toBe('GET');
            if (url.pathname === '/api/auth/me') {
                return route.fulfill({json: {userId: 1, username: 'fixture', roles: ['ADMIN'], authenticated: true}});
            }
            if (marketdataState === 'unavailable' && url.pathname.startsWith('/api/marketdata/')) return route.abort();
            return route.fulfill({json: []});
        });
        await page.addInitScript(() => {
            localStorage.setItem('nq.locale', 'zh-CN');
            localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
                accessToken: 'synthetic-legal-ui', tokenType: 'Bearer', expiresAt: '2999-01-01T00:00:00Z',
                username: 'fixture', roles: ['ADMIN'],
            }));
        });
        await page.goto('/marketdata');
        // 显式空集查询或网络失败均不能使法律入口依赖图表状态。
        await page.getByRole('button', {name: /查\s*询/}).first().click();
        for (const viewport of [{width: 1440, height: 1000}, {width: 390, height: 844}]) {
            await page.setViewportSize(viewport);
            const surface = page.getByTestId('financial-risk-disclaimer');
            const summary = surface.locator('summary');
            await expect(summary).toHaveText('金融风险声明');
            await summary.scrollIntoViewIfNeeded();
            await summary.focus();
            await page.keyboard.press('Enter');
            await expect(surface.locator('pre')).toBeVisible();
            const text = await surface.locator('pre').textContent();
            expect(text).toBe(disclaimer);
            expect(text).toContain('## 中文');
            expect(text).toContain('## English');
            expect(text).toContain('or alter the software licensing terms.');
            expect(await surface.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
            await surface.screenshot({path: test.info().outputPath(`disclaimer-${viewport.width}.png`)});
            await summary.click();
            await expect(surface.locator('pre')).not.toBeVisible();
        }
        const thirdParty = page.getByTestId('third-party-notices');
        await thirdParty.locator('summary').click();
        await expect(thirdParty.locator('pre')).toBeVisible();
        expect(await thirdParty.locator('pre').textContent()).toBe(notices);
        expect(errors).toEqual([]);
        expect(externalRequests).toEqual([]);
        await test.info().attach('canonical-disclaimer-proof', {body: JSON.stringify({
            marketdataState, sha256: createHash('sha256').update(disclaimer).digest('hex'), externalRequests,
        }), contentType: 'application/json'});
    });
}
