import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {expect, test} from 'playwright/test';

const notices = readFileSync(new URL('../../../THIRD_PARTY_NOTICES.md', import.meta.url), 'utf8');
const notice = /```text\n([\s\S]*?)```/.exec(notices)![1];

test('正式行情页保留默认 TradingView 链接和 canonical 第三方声明', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    // 仅替换只读数据，真实产品页面、图表库、CSS 和构建产物保持不变；禁止外网与 mutation。
    await page.route('**/*', async route => {
        const url = new URL(route.request().url());
        if (url.origin !== new URL(test.info().project.use.baseURL!).origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        expect(route.request().method()).toBe('GET');
        let json: unknown = [];
        if (url.pathname === '/api/auth/me') {
            json = {userId: 1, username: 'fixture', roles: ['ADMIN'], authenticated: true};
        }
        if (url.pathname === '/api/marketdata/bars') {
            json = Array.from({length: 8}, (_, i) => ({
                exchangeCode: 'BINANCE', marketType: 'SPOT', symbol: 'BTC-USDT', interval: '1m',
                openTime: new Date(Date.UTC(2026, 5, 29, 1, i)).toISOString(),
                closeTime: new Date(Date.UTC(2026, 5, 29, 1, i, 59)).toISOString(),
                openPrice: 100 + i, highPrice: 105 + i, lowPrice: 99 + i, closePrice: 103 + i,
                volume: 1000 + i, qualityStatus: 'OK',
            }));
        }
        return route.fulfill({json});
    });
    await page.addInitScript(() => {
        localStorage.setItem('nq.locale', 'zh-CN');
        localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'synthetic-attribution', tokenType: 'Bearer', expiresAt: '2999-01-01T00:00:00Z',
            username: 'fixture', roles: ['ADMIN'],
        }));
    });
    await page.setViewportSize({width: 1440, height: 1000});
    await page.goto('/marketdata');
    const dateInputs = page.locator('.ant-picker-input input');
    await dateInputs.nth(0).fill('2026-06-29 01:00:00');
    await dateInputs.nth(0).press('Enter');
    await dateInputs.nth(1).fill('2026-06-29 01:08:00');
    await dateInputs.nth(1).press('Enter');
    await page.getByRole('button', {name: /查\s*询/}).first().click();

    const proofs = [];
    for (const viewport of [{width: 1440, height: 1000}, {width: 390, height: 844}, {width: 1024, height: 768}]) {
        await page.setViewportSize(viewport);
        for (const colorScheme of ['dark', 'light'] as const) {
            // 当前产品使用固定深色 tokens；浏览器色彩偏好不能隐藏 attribution。
            await page.emulateMedia({colorScheme});
            for (const id of ['nq-kline-chart', 'nq-volume-chart']) {
                const chart = page.getByTestId(id);
                const logo = chart.locator('a#tv-attr-logo');
                await expect(chart.locator('canvas').first()).toBeVisible();
                await expect(logo).toBeVisible();
                await logo.scrollIntoViewIfNeeded();
                // trial 执行真实可点击性检查，不导航到 TradingView。
                await logo.click({trial: true});
                await expect.poll(() => chart.evaluate(el => {
                    const container = el.querySelector('.nq-chart__canvas')!.getBoundingClientRect();
                    const table = el.querySelector('table')!.getBoundingClientRect();
                    return Math.abs(container.width - table.width);
                })).toBeLessThanOrEqual(2);
                const proof = await logo.evaluate(el => {
                    const box = el.getBoundingClientRect();
                    const hit = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2);
                    const clipped = [];
                    for (let parent: Element | null = el; parent; parent = parent.parentElement) {
                        const style = getComputedStyle(parent), bounds = parent.getBoundingClientRect();
                        if (style.display === 'none' || style.visibility !== 'visible' || Number(style.opacity) === 0) clipped.push('hidden');
                        if (['hidden', 'clip', 'auto', 'scroll'].includes(style.overflowY)
                            && (box.top < bounds.top || box.bottom > bounds.bottom)) clipped.push('vertical clipping');
                        if (['hidden', 'clip', 'auto', 'scroll'].includes(style.overflowX)
                            && (box.left < bounds.left || box.right > bounds.right)) clipped.push('horizontal clipping');
                    }
                    return {href: (el as HTMLAnchorElement).href, clickable: el.contains(hit), clipped};
                });
                const url = new URL(proof.href);
                expect(url.origin).toBe('https://www.tradingview.com');
                expect(url.pathname).toBe('/');
                expect(proof.clickable).toBe(true);
                expect(proof.clipped).toEqual([]);
                proofs.push({id, viewport, colorScheme, ...proof});
                if (viewport.width === 1440 && colorScheme === 'dark') {
                    await chart.screenshot({path: test.info().outputPath(`after-${id}.png`)});
                }
            }
        }
    }
    const surface = page.getByTestId('third-party-notices');
    const summary = surface.locator('summary');
    await expect(summary).toBeVisible();
    await summary.focus();
    await page.keyboard.press('Enter');
    await expect(surface.locator('pre')).toBeVisible();
    expect(await surface.locator('pre').textContent()).toBe(notices);
    expect(createHash('sha256').update(notice).digest('hex')).toBe('f76c6afab94884448f0426e30d6e9d555ca7247894cd3484e477d2f87513036e');
    await expect(surface.locator('pre')).toContainText('TradingView Lightweight Charts™');
    await surface.screenshot({path: test.info().outputPath('third-party-notices.png')});
    expect(await surface.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
    expect(errors).toEqual([]);
    await test.info().attach('attribution-proof', {body: JSON.stringify(proofs, null, 2), contentType: 'application/json'});
});
