import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('GateI-1 strategy version smoke', () => {
    test('策略详情可查看并创建策略版本', async ({page}) => {
        await loginToConsole(page);

        await page.getByRole('menuitem', {name: '策略定义'}).click();
        await expect(page).toHaveURL(/\/strategies$/);

        const listResponsePromise = page.waitForResponse((response) => (
            response.url().includes('/api/strategies') && response.request().method() === 'GET'
        ));
        await page.getByRole('button', {name: /查\s*询/}).click();
        let listResponse = await listResponsePromise;
        expect(listResponse.ok()).toBeTruthy();
        let strategies = await listResponse.json();

        if (strategies.length === 0) {
            const session = await page.evaluate(() => JSON.parse(
                window.localStorage.getItem('nexus-quant.console.auth') ?? '{}',
            ));
            expect(session.accessToken, '创建策略版本前需要登录态 accessToken').toBeTruthy();
            const headers = {
                Authorization: `${session.tokenType ?? 'Bearer'} ${session.accessToken}`,
            };
            const createAccountResponse = await page.request.post('/api/exchange-accounts', {
                headers,
                data: {
                    exchangeCode: 'OKX',
                    tradeEnv: 'SIM',
                    accountAlias: `strategy-version-e2e-${Date.now()}`,
                    externalAccountRef: null,
                },
                timeout: 30_000,
            });
            expect(createAccountResponse.ok(), await createAccountResponse.text()).toBeTruthy();
            const createdAccount = await createAccountResponse.json() as {legacyAccountId: number | null};
            expect(createdAccount.legacyAccountId).toBeGreaterThan(0);
            const strategyCode = `gatei1-e2e-${Date.now()}`;
            const createStrategyResponse = await page.request.post('/api/strategies', {
                headers,
                data: {
                    strategyCode,
                    strategyName: `GateI-1 E2E Strategy ${Date.now()}`,
                    strategyType: 'E2E_SMOKE',
                    exchangeCode: 'OKX',
                    accountId: createdAccount.legacyAccountId,
                    tradeEnv: 'SIM',
                    configSnapshot: '{"source":"gatei1-e2e"}',
                },
                timeout: 30_000,
            });
            expect(createStrategyResponse.ok()).toBeTruthy();

            const refreshedListResponsePromise = page.waitForResponse((response) => (
                response.url().includes('/api/strategies') && response.request().method() === 'GET'
            ));
            await page.getByRole('button', {name: /查\s*询/}).click();
            listResponse = await refreshedListResponsePromise;
            expect(listResponse.ok()).toBeTruthy();
            strategies = await listResponse.json();
        }

        expect(strategies.length).toBeGreaterThan(0);

        await page.getByRole('button', {name: '查看详情'}).first().click();
        await expect(page.getByText('策略版本', {exact: true})).toBeVisible();
        await expect(page.getByText('创建策略版本', {exact: true})).toBeVisible();

        const versionName = `GateI-1 E2E ${Date.now()}`;
        const createResponsePromise = page.waitForResponse((response) => (
            response.url().includes('/api/strategies/')
            && response.url().includes('/versions')
            && response.request().method() === 'POST'
        ));

        await page.getByLabel('版本名称').fill(versionName);
        await page
            .locator('.ant-form-item')
            .filter({hasText: '版本状态'})
            .locator('.ant-select-selector')
            .click();
        await page.getByTitle('ACTIVE').click();
        await page.getByLabel('参数快照 JSON').fill('{"e2e":true}');
        await page.getByLabel('来源快照 JSON').fill('{"source":"playwright"}');
        await page.getByRole('button', {name: '创建版本'}).click();

        const createResponse = await createResponsePromise;
        expect(createResponse.ok()).toBeTruthy();
        await expect(page.getByText(versionName)).toBeVisible();
        await expect(page.getByText('ACTIVE').first()).toBeVisible();
    });
});
