import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('GateG-3B research query', () => {
    test('登录后查询研究配置列表并校验列表渲染', async ({page}) => {
        await loginToConsole(page);

        await page.getByRole('menuitem', {name: '研究配置'}).click();
        await expect(page).toHaveURL(/\/research$/);
        await expect(page.getByRole('heading', {name: '研究配置'})).toBeVisible();

        const responsePromise = page.waitForResponse((response) => (
            response.url().includes('/api/research-configs') && response.request().method() === 'GET'
        ));

        await page.getByRole('button', {name: /查\s*询/}).click();

        const response = await responsePromise;
        expect(response.ok()).toBeTruthy();

        const payload = await response.json();
        expect(Array.isArray(payload)).toBeTruthy();

        await expect(page.getByText(/共 \d+ 条记录/)).toBeVisible();
        await expect(page.locator('.ant-table')).toBeVisible();

        if (payload.length === 0) {
            await expect(page.getByText('当前筛选条件下没有匹配的研究配置。')).toBeVisible();
        } else {
            await expect(page.locator('.ant-table-tbody tr').first()).toBeVisible();
        }
    });
});
