import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';
import {prepareGateI3PaperTradingFixture} from '@/../tests/e2e/paper-trading-fixtures';

test.describe('GateJ-3 paper trading recovery smoke', () => {
    test('恢复事件创建、重试失败步骤、监控守护一次', async ({page}) => {
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
        const row = page.locator(`tr[data-row-key="${paperRunId}"]`);
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

        // --- 恢复事件面板（内联控制台右侧，始终可见，无需切换 Tab）---
        await expect(drawer.getByText('当前 Paper run 暂无恢复事件。')).toBeVisible({timeout: 10_000});

        // Execute recover
        const recoverResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/recover`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /执行恢复/}).click();
        const recoverRes = await recoverResponse;
        expect(recoverRes.ok(), `recover failed: ${recoverRes.status()}`).toBeTruthy();
        const recoverPayload = await recoverRes.json();
        expect(recoverPayload.recoveryEventId).toBeTruthy();
        expect(recoverPayload.paperRunId).toBe(paperRunId);
        expect(recoverPayload.recoveryType).toBe('MANUAL_RECOVER');
        expect(['SUCCEEDED', 'SKIPPED']).toContain(recoverPayload.status);

        // Verify recovery event in table
        await expect(drawer.getByText('MANUAL_RECOVER').first()).toBeVisible({timeout: 10_000});

        // Execute retry failed step
        const retryResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/retry-failed-step`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /重试失败步骤/}).click();
        const retryRes = await retryResponse;
        expect(retryRes.ok(), `retry failed: ${retryRes.status()}`).toBeTruthy();
        const retryPayload = await retryRes.json();
        expect(retryPayload.recoveryType).toBe('RETRY_FAILED_STEP');

        // Verify retry event in table
        await expect(drawer.getByText('RETRY_FAILED_STEP').first()).toBeVisible({timeout: 10_000});

        // Execute monitor run-once
        const monitorResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/monitor/run-once`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /执行监控守护/}).click();
        const monitorRes = await monitorResponse;
        expect(monitorRes.ok(), `monitor run-once failed: ${monitorRes.status()}`).toBeTruthy();
        const monitorPayload = await monitorRes.json();
        expect(monitorPayload.paperRunId).toBe(paperRunId);
        // RUNNING + no heartbeat -> HEARTBEAT_LAG alert expected
        expect(monitorPayload.createdAlertCount).toBeGreaterThanOrEqual(1);
        const alertTypes = (monitorPayload.createdAlerts ?? []).map((a: {alertType: string}) => a.alertType);
        expect(alertTypes).toContain('HEARTBEAT_LAG');

        // 告警面板始终可见；监控守护自动生成的 HEARTBEAT_LAG 告警应直接出现。
        await expect(drawer.getByText('HEARTBEAT_LAG').first()).toBeVisible({timeout: 10_000});
    });
});
