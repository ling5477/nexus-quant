import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('GateG-3 strategies query', () => {
    test('登录后查询策略列表并校验列表渲染', async ({page}) => {
        await loginToConsole(page);

        await page.getByRole('menuitem', {name: '策略定义'}).click();
        await expect(page).toHaveURL(/\/strategies$/);
        await expect(page.getByRole('heading', {name: '策略定义'})).toBeVisible();

        const responsePromise = page.waitForResponse((response) => (
            response.url().includes('/api/strategies') && response.request().method() === 'GET'
        ));

        await page.getByRole('button', {name: /查\s*询/}).click();

        const response = await responsePromise;
        expect(response.ok()).toBeTruthy();

        const payload = await response.json();
        expect(Array.isArray(payload)).toBeTruthy();

        await expect(page.getByText(/共 \d+ 条记录/)).toBeVisible();
        await expect(page.locator('.ant-table')).toBeVisible();

        if (payload.length === 0) {
            await expect(page.getByText('当前筛选条件下没有匹配的策略记录。')).toBeVisible();
        } else {
            await expect(page.getByRole('button', {name: '查看详情'}).first()).toBeVisible();
        }
    });
});
