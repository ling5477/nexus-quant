import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('GateI-1 publish version smoke', () => {
    test('发布版本页面可展示策略版本绑定与快照入口', async ({page}) => {
        await loginToConsole(page);

        await page.getByRole('menuitem', {name: '发布结果'}).click();
        await expect(page).toHaveURL(/\/publishes$/);
        await expect(page.getByRole('heading', {name: '发布结果'})).toBeVisible();

        const responsePromise = page.waitForResponse((response) => (
            response.url().includes('/api/publishes') && response.request().method() === 'GET'
        ));
        await page.getByRole('button', {name: /查\s*询/}).click();
        const response = await responsePromise;
        expect(response.ok()).toBeTruthy();
        const payload = await response.json();

        await expect(page.getByText(/共 \d+ 条记录/)).toBeVisible();
        await expect(page.getByText('策略版本 ID').first()).toBeVisible();

        if (payload.length === 0) {
            await expect(page.getByText('当前筛选条件下没有匹配的发布结果。')).toBeVisible();
            return;
        }

        await page.getByRole('button', {name: '查看详情'}).first().click();
        await expect(page.getByText('发布详情')).toBeVisible();
        await expect(page.getByText('版本快照')).toBeVisible();
    });
});
