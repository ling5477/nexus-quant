import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('RC1-6 account context smoke', () => {
    test('登录后渲染默认账户上下文并带入交易工作台默认账户', async ({page}) => {
        test.setTimeout(90_000);

        await loginToConsole(page);

        await expect(page.getByRole('button', {name: /OKX \/ SIM \/ rc1-admin-default/})).toBeVisible({timeout: 30_000});

        await page.getByRole('menuitem', {name: '账户管理'}).click();
        await expect(page).toHaveURL(/\/accounts$/);
        await expect(page.getByRole('heading', {name: '账户与凭证管理'})).toBeVisible();
        await expect(page.getByText('account-context-store 当前选中：900001')).toBeVisible();

        await page.getByRole('menuitem', {name: '交易工作台'}).click();
        await expect(page).toHaveURL(/\/trading$/);
        await expect(page.getByRole('cell', {name: 'OKX / SIM / rc1-admin-default（exchangeAccountId=900001）'})).toBeVisible();
        await expect(page.getByText('当前页面只使用正式 exchangeAccountId')).toBeVisible();
        await expect(page.getByText('订单列表')).toBeVisible();
    });
});
