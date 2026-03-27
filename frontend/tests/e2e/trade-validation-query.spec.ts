import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

const orderId = process.env.E2E_TRADE_ORDER_ID;

test.describe('GateG-4C trade validation query', () => {
    test('登录后查询订单并打开交易验证详情', async ({page}) => {
        test.setTimeout(90_000);
        test.skip(!orderId, '未配置 E2E_TRADE_ORDER_ID，跳过真实 trade-validation 查询链路。');

        await loginToConsole(page);

        await page.getByRole('menuitem', {name: '交易验证'}).click();
        await expect(page).toHaveURL(/\/trade-validation$/);

        await page.getByLabel('订单 ID').fill(orderId ?? '');
        await page.getByRole('button', {name: /查\s*询/}).click();

        await expect(page.getByText('共 1 条记录')).toBeVisible({timeout: 60_000});
        await expect(page.getByText(orderId ?? '')).toBeVisible();
        await expect(page.locator('.ant-table')).toBeVisible();
        await page.getByRole('button', {name: '查看详情'}).click();
        await expect(page.getByText('交易验证详情')).toBeVisible();
        await expect(page.getByText('订单详情')).toBeVisible();
    });
});
