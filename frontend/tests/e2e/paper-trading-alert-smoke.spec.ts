import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';
import {prepareGateI3PaperTradingFixture} from '@/../tests/e2e/paper-trading-fixtures';

test.describe('GateJ-2 paper trading alert smoke', () => {
    test('告警创建、确认、解决', async ({page}) => {
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
        const drawer = page.getByLabel('Paper Trading 详情');
        await expect(drawer.getByText('Paper Run ID')).toBeVisible({timeout: 10_000});

        // --- Alert Tab ---
        await drawer.getByRole('tab', {name: '告警'}).click();
        await expect(drawer.getByText('当前 Paper run 暂无告警。')).toBeVisible({timeout: 5_000});

        // Create test alert
        const createAlertResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/alerts`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /创建测试告警/}).click();
        const alertCreated = await createAlertResponse;
        expect(alertCreated.ok(), `create alert failed: ${alertCreated.status()}`).toBeTruthy();
        const alertPayload = await alertCreated.json();
        const alertId: string = alertPayload.alertId;
        expect(alertId).toBeTruthy();
        expect(alertPayload.paperRunId).toBe(paperRunId);
        expect(alertPayload.status).toBe('OPEN');
        expect(alertPayload.severity).toBe('LOW');

        // Verify alert appears in table
        await expect(drawer.getByText('SYSTEM_NOTICE').first()).toBeVisible({timeout: 10_000});
        await expect(drawer.getByText('OPEN').first()).toBeVisible({timeout: 5_000});

        // Ack alert
        const ackResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/alerts/${alertId}/ack`)
            && response.request().method() === 'PATCH'
        ));
        const alertRow = drawer.locator('tr').filter({hasText: '手动测试告警'});
        await alertRow.getByRole('link', {name: '确认'}).or(alertRow.getByRole('button', {name: '确认'})).click();
        const ackRes = await ackResponse;
        expect(ackRes.ok(), `ack alert failed: ${ackRes.status()}`).toBeTruthy();
        const ackPayload = await ackRes.json();
        expect(ackPayload.status).toBe('ACKED');
        expect(ackPayload.acknowledgedBy).toBeTruthy();

        // Verify ACKED status displayed
        await expect(drawer.getByText('ACKED').first()).toBeVisible({timeout: 10_000});

        // Resolve alert
        const resolveResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/alerts/${alertId}/resolve`)
            && response.request().method() === 'PATCH'
        ));
        await alertRow.getByRole('link', {name: '解决'}).or(alertRow.getByRole('button', {name: '解决'})).click();
        const resolveRes = await resolveResponse;
        expect(resolveRes.ok(), `resolve alert failed: ${resolveRes.status()}`).toBeTruthy();
        const resolvePayload = await resolveRes.json();
        expect(resolvePayload.status).toBe('RESOLVED');
        expect(resolvePayload.resolvedAt).toBeTruthy();

        // Verify RESOLVED status displayed
        await expect(drawer.getByText('RESOLVED').first()).toBeVisible({timeout: 10_000});
    });
});
