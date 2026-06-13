import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';
import {prepareGateI3PaperTradingFixture} from '@/../tests/e2e/paper-trading-fixtures';

test.describe('GateJ-3 paper trading stability check smoke', () => {
    test('稳定性验收生成与列表查询', async ({page}) => {
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

        // --- 稳定性验收面板（内联控制台中部，始终可见，无需切换 Tab）---
        await expect(drawer.getByText('当前 Paper run 暂无稳定性验收。')).toBeVisible({timeout: 10_000});

        // Generate first stability check (24h window)
        const generateResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/stability-checks/generate`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /生成最近 24h 稳定性验收/}).click();
        const generated = await generateResponse;
        expect(generated.ok(), `generate stability check failed: ${generated.status()}`).toBeTruthy();
        const payload = await generated.json();
        expect(payload.stabilityCheckId).toBeTruthy();
        expect(payload.paperRunId).toBe(paperRunId);
        expect(['PASSED', 'PARTIAL', 'FAILED']).toContain(payload.status);
        // No heartbeat yet -> FAILED expected
        expect(payload.status).toBe('FAILED');
        expect(Number(payload.heartbeatCount)).toBe(0);

        // Verify stability check appears in table
        await expect(drawer.getByText('FAILED').first()).toBeVisible({timeout: 10_000});

        // Generate again with same window -> idempotent (size still 1)
        const generateAgainResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/stability-checks/generate`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /生成最近 24h 稳定性验收/}).click();
        const generatedAgain = await generateAgainResponse;
        expect(generatedAgain.ok()).toBeTruthy();
    });
});
