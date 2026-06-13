import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';
import {prepareGateI3PaperTradingFixture} from '@/../tests/e2e/paper-trading-fixtures';

test.describe('GateJ-2 paper trading daily report smoke', () => {
    test('日报生成与列表查询', async ({page}) => {
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

        // --- 最新日报摘要（内联控制台中部，始终可见，无需切换 Tab）---
        await expect(drawer.getByText('当前 Paper run 暂无日报。')).toBeVisible({timeout: 10_000});

        // Generate today's daily report
        const generateResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/daily-reports/generate`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /生成今日日报/}).click();
        const generated = await generateResponse;
        expect(generated.ok(), `generate daily report failed: ${generated.status()}`).toBeTruthy();
        const reportPayload = await generated.json();
        expect(reportPayload.reportId).toBeTruthy();
        expect(reportPayload.paperRunId).toBe(paperRunId);
        expect(reportPayload.status).toBe('GENERATED');

        // Verify report appears in table
        await expect(drawer.getByText(reportPayload.reportDate).first()).toBeVisible({timeout: 10_000});
        await expect(drawer.getByText('GENERATED').first()).toBeVisible({timeout: 5_000});

        // Idempotent: regenerate same date should not error
        const regenerateResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/daily-reports/generate`)
            && response.request().method() === 'POST'
        ));
        await drawer.getByRole('button', {name: /生成今日日报/}).click();
        const regenerated = await regenerateResponse;
        expect(regenerated.ok(), `regenerate daily report failed: ${regenerated.status()}`).toBeTruthy();
        const regenPayload = await regenerated.json();
        expect(regenPayload.paperRunId).toBe(paperRunId);
        expect(regenPayload.reportDate).toBe(reportPayload.reportDate);
    });
});
