import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

const orderId = process.env.E2E_TRADE_ORDER_ID;

test.describe('trading workspace', () => {
    test('登录后进入正式交易工作台并查看账户上下文与风控摘要', async ({page}) => {
        test.setTimeout(90_000);

        await loginToConsole(page);

        await page.getByRole('menuitem', {name: '交易工作台'}).click();
        await expect(page).toHaveURL(/\/trading$/);
        await expect(page.getByRole('heading', {name: '交易工作台'})).toBeVisible();
        await expect(page.getByText('账户上下文', {exact: true})).toBeVisible();
        await expect(page.getByText('SIM', {exact: true})).toBeVisible();
        await expect(page.getByText('订单列表', {exact: true})).toBeVisible();
        await expect(page.locator('.ant-table')).toBeVisible({timeout: 60_000});

        await page.getByRole('button', {name: '下单前检查'}).click();
        await expect(page.getByText('风控摘要')).toBeVisible();
        await expect(page.getByText('服务端风控不可绕过状态')).toBeVisible();
        await page.getByRole('button', {name: /取\s*消/}).click();
    });

    test('旧 trade-validation 路由保留过渡提示', async ({page}) => {
        await loginToConsole(page);

        await page.goto('/trade-validation');
        await expect(page).toHaveURL(/\/trade-validation$/);
        await expect(page.getByText('/trade-validation 是过渡入口')).toBeVisible();
        await expect(page.getByRole('heading', {name: '交易工作台'})).toBeVisible();
    });

    test('配置订单 ID 时可打开订单详情', async ({page}) => {
        test.setTimeout(90_000);
        test.skip(!orderId, '未配置 E2E_TRADE_ORDER_ID，跳过真实订单详情查询链路。');

        await loginToConsole(page);
        await page.goto('/trading');

        await page.getByLabel('订单 ID').fill(orderId ?? '');
        await page.getByRole('button', {name: /查\s*询/}).click();

        await expect(page.getByText(/共\s+1\s+条记录/)).toBeVisible({timeout: 60_000});
        await expect(page.getByText(orderId ?? '')).toBeVisible();
        await expect(page.locator('.ant-table')).toBeVisible();
        await page.getByRole('button', {name: '查看详情'}).click();
        await expect(page.getByText('订单详情')).toBeVisible();
        await expect(page.getByText('订单', {exact: true})).toBeVisible();
    });
});
