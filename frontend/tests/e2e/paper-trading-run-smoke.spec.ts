import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';
import {prepareGateI3PaperTradingFixture} from '@/../tests/e2e/paper-trading-fixtures';

test.describe('GateI-3 paper trading run smoke', () => {
    test('Paper Trading 页面可创建、启动、停止 run，并展示订单/成交/持仓区域', async ({page}) => {
        await loginToConsole(page);

        const fixture = await prepareGateI3PaperTradingFixture(page);

        await page.goto('/paper-trading');
        await expect(page).toHaveURL(/\/paper-trading$/);
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        await page.getByRole('button', {name: /查\s*询/}).click();
        await expect(page.getByText(/共 \d+ 条记录/)).toBeVisible();

        await page.getByRole('button', {name: /创建\s*Paper\s*Run/i}).click();
        const dialog = page.getByRole('dialog', {name: /创建\s*Paper\s*Trading/});
        await expect(dialog).toBeVisible();
        await dialog.getByPlaceholder('发布记录 ID（publishId）').fill(fixture.publishId);

        const createResponse = page.waitForResponse((response) => (
            response.url().includes('/api/paper-trading/runs')
            && response.request().method() === 'POST'
            && !response.url().endsWith('/start')
            && !response.url().endsWith('/stop')
        ));
        await page.getByRole('button', {name: 'OK', exact: true}).click();
        const created = await createResponse;
        expect(created.ok(), `create paper run failed: ${created.status()} ${await created.text()}`).toBeTruthy();
        const createdPayload = await created.json();
        const paperRunId: string = createdPayload.paperRunId;
        expect(paperRunId).toBeTruthy();
        expect(createdPayload.publishId).toBe(fixture.publishId);
        expect(createdPayload.status).toBe('CREATED');
        expect(createdPayload.strategyVersionId).toBe(fixture.strategyVersionId);

        const row = page.locator('tr').filter({hasText: paperRunId});
        await expect(row).toBeVisible({timeout: 15_000});

        const startResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/start`)
            && response.request().method() === 'POST'
        ));
        await row.getByRole('link', {name: '启动'}).or(row.getByRole('button', {name: '启动'})).click();
        const started = await startResponse;
        expect(started.ok()).toBeTruthy();
        const startedPayload = await started.json();
        expect(startedPayload.status).toBe('RUNNING');

        const runningRow = page.locator('tr').filter({hasText: paperRunId});
        await expect(runningRow.getByText('RUNNING')).toBeVisible({timeout: 15_000});

        const stopResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/stop`)
            && response.request().method() === 'POST'
        ));
        await runningRow.getByRole('link', {name: '停止'}).or(runningRow.getByRole('button', {name: '停止'})).click();
        const stopped = await stopResponse;
        expect(stopped.ok()).toBeTruthy();
        const stoppedPayload = await stopped.json();
        expect(stoppedPayload.status).toBe('STOPPED');

        const stoppedRow = page.locator('tr').filter({hasText: paperRunId});
        await expect(stoppedRow.getByText('STOPPED')).toBeVisible({timeout: 15_000});
        await stoppedRow.getByRole('link', {name: '查看详情'}).or(stoppedRow.getByRole('button', {name: '查看详情'})).click();

        const drawer = page.getByLabel('Paper Trading 详情');
        await expect(drawer.getByText('Paper Run ID')).toBeVisible({timeout: 10_000});

        await page.getByRole('tab', {name: '订单'}).click();
        await expect(page.getByText('当前 Paper run 暂无订单事实。')).toBeVisible();

        await page.getByRole('tab', {name: '成交'}).click();
        await expect(page.getByText('当前 Paper run 暂无成交事实。')).toBeVisible();

        await page.getByRole('tab', {name: '持仓'}).click();
        await expect(page.getByText('当前 Paper run 暂无持仓事实。')).toBeVisible();

        await page.getByRole('tab', {name: '快照'}).click();
        await expect(page.getByText('Publish Snapshot')).toBeVisible();
        await expect(page.getByText('Strategy Version Snapshot')).toBeVisible();
    });
});
