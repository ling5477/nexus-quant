import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('RC1-6 account context smoke', () => {
    test('登录后渲染默认账户上下文并带入 trade-validation 默认账户', async ({page}) => {
        test.setTimeout(90_000);

        await loginToConsole(page);

        await expect(page.getByRole('button', {name: /OKX \/ SIM \/ rc1-admin-default/})).toBeVisible({timeout: 30_000});

        await page.getByRole('menuitem', {name: '账户管理'}).click();
        await expect(page).toHaveURL(/\/accounts$/);
        await expect(page.getByRole('heading', {name: '账户与凭证管理'})).toBeVisible();
        await expect(page.getByText('当前已选择 exchangeAccountId: 900001')).toBeVisible();

        await page.getByRole('menuitem', {name: '交易验证'}).click();
        await expect(page).toHaveURL(/\/trade-validation$/);
        await expect(page.getByText('当前账户上下文：OKX / SIM / rc1-admin-default（legacyAccountId=900001）')).toBeVisible();
        await expect(page.getByLabel('账户 ID（默认当前上下文）')).toHaveValue('900001');
    });
});
