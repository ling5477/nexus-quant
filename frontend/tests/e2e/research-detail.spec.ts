import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('GateG-4B research detail', () => {
    test('登录后打开研究配置详情抽屉', async ({page}) => {
        await loginToConsole(page);

        await page.getByRole('menuitem', {name: '研究配置'}).click();
        await expect(page).toHaveURL(/\/research$/);

        const responsePromise = page.waitForResponse((response) => (
            response.url().includes('/api/research-configs') && response.request().method() === 'GET'
        ));

        await page.getByRole('button', {name: /查\s*询/}).click();

        const response = await responsePromise;
        expect(response.ok()).toBeTruthy();

        const payload = await response.json();
        expect(Array.isArray(payload)).toBeTruthy();
        test.skip(payload.length === 0, '当前环境没有预置研究配置数据，无法验证列表进入详情链路。');

        await page.getByRole('button', {name: '查看详情'}).first().click();
        await expect(page.getByText('研究配置详情')).toBeVisible();
        await expect(page.getByText('动作区')).toBeVisible();
    });
});
