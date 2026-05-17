import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('marketdata bars query', () => {
    test('可打开行情页并按 GateH-2 固定维度查询 K 线', async ({page}) => {
        await loginToConsole(page);

        await page.getByRole('menuitem', {name: '行情查询'}).click();
        await expect(page).toHaveURL(/\/marketdata$/);
        await expect(page.getByRole('heading', {name: 'Marketdata'})).toBeVisible();

        await expect(page.getByText(/OKX|BINANCE/).first()).toBeVisible();
        await expect(page.getByText('SPOT').first()).toBeVisible();
        await expect(page.getByText('BTC-USDT').first()).toBeVisible();
        await expect(page.getByText('1m').first()).toBeVisible();

        const queryCard = page.locator('.ant-card').filter({hasText: '查询条件'});
        await queryCard.getByRole('button', {name: /查\s*询/}).click();

        await expect(page.getByText('Bars 结果')).toBeVisible();
        await expect(page.locator('.ant-table').first()).toBeVisible({timeout: 60_000});
        await expect(page.getByText('Marketdata bars 查询失败')).toHaveCount(0);
    });
});
