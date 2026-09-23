import {expect, test} from 'playwright/test';

import {prepareGateI2BacktestTraceFixture} from '@/../tests/e2e/gatei2-fixtures';
import {loginToConsole} from '@/../tests/e2e/support';

test.describe('GateI-2 backtest config enhanced smoke', () => {
    test('backtests 页面展示 strategy version、dataset 与 run 快照追溯信息', async ({page}) => {
        test.setTimeout(180_000);

        await loginToConsole(page);
        const fixture = await prepareGateI2BacktestTraceFixture(page);

        await page.goto('/backtests');
        await expect(page.getByRole('heading', {name: '回测配置'})).toBeVisible();

        const listResponsePromise = page.waitForResponse((response) =>
            response.url().includes('/api/backtest-configs') && response.request().method() === 'GET',
        );
        await page.getByRole('button', {name: /查\s*询/}).click();
        const listResponse = await listResponsePromise;
        expect(listResponse.ok()).toBeTruthy();

        await expect(page.getByText(fixture.backtestConfigId)).toBeVisible({timeout: 60_000});
        await expect(page.getByText(fixture.strategyVersionId)).toBeVisible();
        await expect(page.getByText(fixture.datasetId)).toBeVisible();

        await page.locator('tr').filter({hasText: fixture.backtestConfigId})
            .getByRole('button', {name: '查看详情'})
            .click();
        await expect(page.getByText('回测配置详情')).toBeVisible();
        await expect(page.getByText('策略版本快照', {exact: true})).toBeVisible();
        await expect(page.getByText('参数快照', {exact: true})).toBeVisible();
        await expect(page.getByText('数据集快照', {exact: true})).toBeVisible();
        await expect(page.getByText('配置快照 JSON', {exact: true})).toBeVisible();
        await expect(page.getByText(fixture.strategyVersionId).first()).toBeVisible();
        await expect(page.getByText(fixture.datasetId).first()).toBeVisible();

        const runCreateResponsePromise = page.waitForResponse((response) =>
            response.url().includes('/api/backtest-runs') && response.request().method() === 'POST',
        );
        await page.getByRole('button', {name: '创建回测运行'}).click();
        const runCreateResponse = await runCreateResponsePromise;
        expect(runCreateResponse.ok()).toBeTruthy();
        const run = await runCreateResponse.json();

        await expect(page.getByText('回测运行详情', {exact: true})).toBeVisible();
        await expect(page.getByText(run.backtestRunId)).toBeVisible({timeout: 60_000});
        await expect(page.getByText('策略版本快照', {exact: true}).last()).toBeVisible();
        await expect(page.getByText('数据集快照', {exact: true}).last()).toBeVisible();
        await expect(page.getByText('参数快照', {exact: true}).last()).toBeVisible();
        await expect(page.getByText('配置快照', {exact: true}).last()).toBeVisible();
        await expect(page.getByText(fixture.datasetId).last()).toBeVisible();
    });
});
