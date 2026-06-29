import {expect, test} from 'playwright/test';

test.describe('design system chart foundation smoke (B0.4)', () => {
    test('NqKlineChart / NqVolumeChart 静态 mock 渲染 + 状态占位 + 行情惯例色切换', async ({page}) => {
        // Why: /dev/design-system 是公开自检路由,chart foundation 只用静态 mock 数据;
        // smoke 不连接后端 / WebSocket / 真实交易所,只验证 lightweight-charts 容器、状态和惯例色。
        await page.goto('/dev/design-system');

        const section = page.getByTestId('chart-foundation-section');
        await expect(section).toBeVisible();
        await expect(section).toContainText('静态 mock 数据');
        await expect(section).toContainText('不接真实 API / WebSocket / 交易入口');

        const kline = section.getByTestId('nq-kline-chart').filter({hasText: 'BTC-USDT 1m K-line'}).first();
        const volume = section.getByTestId('nq-volume-chart').filter({hasText: 'BTC-USDT 1m Volume'}).first();

        await expect(kline.locator('canvas').first()).toBeVisible({timeout: 15_000});
        await expect(volume.locator('canvas').first()).toBeVisible({timeout: 15_000});
        await expect(kline).toContainText('Mock Kline Source');
        await expect(kline).toContainText('Stale');

        await expect(section.getByText('暂无 chart foundation 样本数据')).toBeVisible();
        await expect(section.getByText('K 线加载中')).toBeVisible();
        await expect(section.getByText('SIMULATED_CHART_ERROR')).toBeVisible();

        const upSwatch = page
            .locator('.nq-ds-demo__swatch', {hasText: 'upColor'})
            .locator('.nq-ds-demo__swatch-chip')
            .first();
        await expect(upSwatch).toBeVisible();
        const intlUpColor = await upSwatch.evaluate((el) => getComputedStyle(el).backgroundColor);

        await page.getByText('红涨绿跌 (CN_STOCK)').click();
        const cnUpColor = await upSwatch.evaluate((el) => getComputedStyle(el).backgroundColor);

        expect(intlUpColor).toBe('rgb(51, 214, 166)');
        expect(cnUpColor).toBe('rgb(255, 92, 108)');
        expect(cnUpColor).not.toEqual(intlUpColor);
    });
});
