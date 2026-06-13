import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';
import {prepareGateI3PaperTradingFixture} from '@/../tests/e2e/paper-trading-fixtures';

test.describe('GateJ-1 paper trading schedule and heartbeat smoke', () => {
    test('调度计划创建、执行一次、禁用；心跳执行一次', async ({page}) => {
        await loginToConsole(page);

        const fixture = await prepareGateI3PaperTradingFixture(page);

        await page.goto('/paper-trading');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        // Create paper run via UI
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
        expect(created.ok(), `create paper run failed: ${created.status()}`).toBeTruthy();
        const createdPayload = await created.json();
        const paperRunId: string = createdPayload.paperRunId;
        expect(paperRunId).toBeTruthy();

        // Start paper run
        const row = page.locator('tr').filter({hasText: paperRunId});
        await expect(row).toBeVisible({timeout: 15_000});
        const startResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/start`)
            && response.request().method() === 'POST'
        ));
        await row.getByRole('link', {name: '启动'}).or(row.getByRole('button', {name: '启动'})).click();
        const started = await startResponse;
        expect(started.ok()).toBeTruthy();
        await expect(row.getByText('RUNNING')).toBeVisible({timeout: 15_000});

        // Open detail drawer
        await row.getByRole('link', {name: '查看详情'}).or(row.getByRole('button', {name: '查看详情'})).click();
        const drawer = page.getByRole('region', {name: 'Paper Trading 详情'});
        await expect(drawer.getByText('Paper Run ID')).toBeVisible({timeout: 10_000});

        // --- 调度计划面板（内联控制台右侧，始终可见，无需切换 Tab）---
        await expect(drawer.getByText('当前 Paper run 暂无调度计划。')).toBeVisible({timeout: 10_000});

        // Create schedule
        await drawer.getByRole('button', {name: /创建调度/}).click();
        const scheduleDialog = page.getByRole('dialog', {name: /创建调度计划/});
        await expect(scheduleDialog).toBeVisible();
        await scheduleDialog.getByLabel('调度名称').fill('smoke-schedule');
        await scheduleDialog.getByLabel('Cron 表达式').clear();
        await scheduleDialog.getByLabel('Cron 表达式').fill('0 */5 * * * *');

        const scheduleCreateResponse = page.waitForResponse((response) => (
            response.url().includes('/api/paper-trading/schedules')
            && response.request().method() === 'POST'
            && !response.url().includes('/run-once')
        ));
        await page.getByRole('button', {name: 'OK', exact: true}).click();
        const scheduleCreated = await scheduleCreateResponse;
        expect(scheduleCreated.ok(), `create schedule failed: ${scheduleCreated.status()}`).toBeTruthy();
        const schedulePayload = await scheduleCreated.json();
        expect(schedulePayload.scheduleId).toBeTruthy();
        expect(schedulePayload.status).toBe('ENABLED');
        expect(schedulePayload.scheduleName).toBe('smoke-schedule');

        // Verify schedule appears in table
        await expect(page.getByText('smoke-schedule')).toBeVisible({timeout: 10_000});
        await expect(page.getByText('ENABLED').first()).toBeVisible({timeout: 5_000});

        // Run-once
        const runOnceResponse = page.waitForResponse((response) => (
            response.url().includes('/run-once')
            && response.request().method() === 'POST'
            && response.url().includes('/schedules/')
        ));
        await page.getByRole('link', {name: '执行一次'}).or(page.getByRole('button', {name: '执行一次'})).click();
        const runOnceRes = await runOnceResponse;
        expect(runOnceRes.ok(), `run-once failed: ${runOnceRes.status()}`).toBeTruthy();
        const firePayload = await runOnceRes.json();
        expect(firePayload.fireId).toBeTruthy();
        expect(firePayload.status).toBe('SUCCEEDED');

        // View fires
        await page.getByRole('link', {name: '触发记录'}).or(page.getByRole('button', {name: '触发记录'})).click();
        await expect(page.getByText('SUCCEEDED').first()).toBeVisible({timeout: 10_000});

        // Disable schedule
        const disableResponse = page.waitForResponse((response) => (
            response.url().includes('/status')
            && response.request().method() === 'PATCH'
        ));
        await page.getByRole('link', {name: '禁用'}).or(page.getByRole('button', {name: '禁用'})).click();
        const disableRes = await disableResponse;
        expect(disableRes.ok()).toBeTruthy();
        const disabledPayload = await disableRes.json();
        expect(disabledPayload.status).toBe('DISABLED');

        // --- 心跳面板（内联控制台右侧，始终可见，无需切换 Tab）---
        await expect(drawer.getByText('当前 Paper run 暂无心跳记录。')).toBeVisible({timeout: 10_000});

        // Run heartbeat once
        const heartbeatResponse = page.waitForResponse((response) => (
            response.url().includes(`/api/paper-trading/runs/${paperRunId}/heartbeats/run-once`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /执行心跳检查/}).click();
        const hbRes = await heartbeatResponse;
        expect(hbRes.ok(), `heartbeat run-once failed: ${hbRes.status()}`).toBeTruthy();
        const hbPayload = await hbRes.json();
        expect(hbPayload.heartbeatId).toBeTruthy();
        expect(hbPayload.status).toBe('OK');

        // Verify heartbeat appears in table
        await expect(page.getByText('OK').first()).toBeVisible({timeout: 10_000});
    });
});
