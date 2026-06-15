import {expect, test} from 'playwright/test';

test.describe('design system backtest chart smoke (B1)', () => {
    test('BacktestCurveChart 样本渲染 + 无序列 unavailable(不编造曲线)', async ({page}) => {
        // Why: /dev/design-system 是公开自检路由,不依赖登录/后端;
        // 这里只验证 BacktestCurveChart 组件:有序列渲染 canvas、无序列显式 unavailable。
        // 真实回测详情页 /backtests/:id 依赖后端 + 鉴权,需在带后端环境验证(见 TESTING.md 说明)。
        await page.goto('/dev/design-system');

        const section = page.locator('section', {hasText: '回测曲线(BacktestCurveChart)'}).first();
        await expect(section).toBeVisible();

        // 样本曲线渲染为 ECharts canvas(至少 2 个:权益 + 回撤)。
        await expect(section.locator('canvas').first()).toBeVisible();
        expect(await section.locator('canvas').count()).toBeGreaterThanOrEqual(2);

        // 无序列时显式 unavailable 占位(不编造曲线)。
        await expect(section.getByText('后端暂未提供回测权益时间序列(仅有聚合指标)。')).toBeVisible();
    });
});
