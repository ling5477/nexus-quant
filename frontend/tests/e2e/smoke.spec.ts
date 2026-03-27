import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('GateG-1 smoke', () => {
    test('登录成功并跳转至少一个菜单页', async ({page}) => {
        await loginToConsole(page);
        await expect(page.getByRole('heading', {name: '控制台总览'})).toBeVisible();

        await page.getByRole('menuitem', {name: '策略定义'}).click();

        await expect(page).toHaveURL(/\/strategies$/);
        await expect(page.getByRole('heading', {name: '策略定义'})).toBeVisible();
    });
});
