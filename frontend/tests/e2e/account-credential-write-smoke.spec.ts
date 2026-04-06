import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

/**
 * RC1-4 最小写侧 smoke 只覆盖账户/凭证/默认上下文闭环。
 * Why:
 * 这条用例的目标是确认“创建账户 + 切换默认账户 + 凭证轮换 + 结构性校验 + header/trade-validation 联动”成立，
 * 不把回归范围扩展成多账户矩阵或真实交易所探活。
 */
test.describe('RC1-4 account credential write smoke', () => {
    test('创建账户并切换默认账户后，结构性校验与上下文联动保持闭环', async ({page}) => {
        test.setTimeout(120_000);
        const suffix = Date.now().toString().slice(-8);
        const createdAlias = `rc1-smoke-created-${suffix}`;
        const externalAccountRef = `rc1-smoke-ref-${suffix}`;
        const apiKey = `smoke-api-key-${suffix}`;

        await loginToConsole(page);
        await expect(page.getByRole('button', {name: /OKX \/ SIM \/ rc1-admin-(default|alt)/})).toBeVisible({timeout: 30_000});

        await page.getByRole('menuitem', {name: '账户管理'}).click();
        await expect(page).toHaveURL(/\/accounts$/);
        await expect(page.getByRole('heading', {name: '账户与凭证管理'})).toBeVisible();

        await page.getByRole('button', {name: '新建账户'}).click();
        const accountDrawer = page.getByRole('dialog', {name: '新建账户'});
        await accountDrawer.getByLabel('账户别名').fill(createdAlias);
        await accountDrawer.getByLabel('外部账户引用').fill(externalAccountRef);
        await accountDrawer.getByRole('button', {name: /保\s*存/}).click();
        await expect(page.getByRole('cell', {name: createdAlias})).toBeVisible({timeout: 30_000});

        const altRow = page.locator('tr').filter({hasText: 'rc1-admin-alt'}).first();
        const setDefaultButton = altRow.getByRole('button', {name: '设为默认'});
        if (await setDefaultButton.isEnabled()) {
            await setDefaultButton.click();
        }

        await expect(page.getByRole('button', {name: /OKX \/ SIM \/ rc1-admin-alt/})).toBeVisible({timeout: 30_000});
        await expect(page.getByText('当前默认账户上下文：OKX / SIM / rc1-admin-alt（exchangeAccountId=900002）')).toBeVisible({timeout: 30_000});

        const refreshedAltRow = page.locator('tr').filter({hasText: 'rc1-admin-alt'}).first();
        await refreshedAltRow.getByRole('button', {name: '凭证'}).click();

        const drawer = page.locator('.ant-drawer-content').last();
        await expect(drawer.getByText('凭证管理：rc1-admin-alt')).toBeVisible();
        await drawer.getByLabel('API Key').fill(apiKey);
        await drawer.getByLabel('Secret Key').fill(`smoke-secret-${suffix}`);
        await drawer.getByLabel('Passphrase').fill(`smoke-pass-${suffix}`);
        await drawer.getByRole('button', {name: '保存凭证'}).click();

        await expect(drawer.getByText('待校验')).toBeVisible({timeout: 30_000});
        await expect(drawer.getByRole('button', {name: '测试连接（结构性校验）'})).toBeEnabled({timeout: 30_000});
        await drawer.getByRole('button', {name: '测试连接（结构性校验）'}).click();
        await expect(drawer.getByText('已校验')).toBeVisible({timeout: 30_000});
        await page.getByRole('dialog', {name: /凭证管理：rc1-admin-alt/}).getByRole('button', {name: 'Close'}).click();
        await expect(page.getByRole('dialog', {name: /凭证管理：rc1-admin-alt/})).toBeHidden({timeout: 30_000});

        await page.getByRole('menuitem', {name: '交易验证'}).click();
        await expect(page).toHaveURL(/\/trade-validation$/);
        await expect(page.getByText('当前账户上下文：OKX / SIM / rc1-admin-alt（legacyAccountId=900002）')).toBeVisible({timeout: 30_000});
        await expect(page.getByLabel('账户 ID（默认当前上下文）')).toHaveValue('900002');
    });
});
