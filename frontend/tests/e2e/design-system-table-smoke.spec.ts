import {expect, test} from 'playwright/test';

test.describe('design system table smoke (B0.2)', () => {
    test('表格密度切换 + 列格式(右对齐数字 / 涨跌方向色)', async ({page}) => {
        // Why: /dev/design-system 是公开自检路由,不依赖登录/后端;
        // smoke 只验证 B0.2 表格密度与列格式渲染,不伪造数据、不打后端。
        await page.goto('/dev/design-system');

        const table = page.getByRole('table', {name: '表格密度自检'});
        await expect(table).toBeVisible();

        // 表头与样本行存在。
        await expect(table.getByRole('columnheader', {name: '最新价'})).toBeVisible();
        await expect(table.getByRole('columnheader', {name: '浮动盈亏'})).toBeVisible();
        await expect(table.getByRole('cell', {name: 'BTC-USDT'})).toBeVisible();

        // 金额列带货币后缀且右对齐(等宽)。
        await expect(table.getByText('64,231.50 USDT')).toBeVisible();

        // 密度切换:默认 standard,切到紧凑后 table 应带 compact class。
        // AntD Segmented 的 radio input 视觉隐藏,点可见 label 文本更稳。
        await expect(table).toHaveClass(/nq-ds-table--standard/);
        await page.getByText('紧凑 28', {exact: true}).click();
        await expect(table).toHaveClass(/nq-ds-table--compact/);

        // 涨跌列方向色:盈利(up)与亏损(down)颜色必须不同(独立于 success/danger)。
        const upColor = await table.getByText('+1,284.50').evaluate((el) => getComputedStyle(el).color);
        const downColor = await table.getByText('-642.18').evaluate((el) => getComputedStyle(el).color);
        expect(upColor).not.toEqual(downColor);

        // 默认 INTL_CRYPTO:up = 绿 rgb(51,214,166)。
        expect(upColor).toBe('rgb(51, 214, 166)');
    });
});
