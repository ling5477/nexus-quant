import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('marketdata ingestion', () => {
    test('可创建接入任务并执行 run-once，外网失败时保留可查询失败状态', async ({page}) => {
        test.setTimeout(120_000);

        await loginToConsole(page);
        await page.goto('/marketdata');

        await expect(page.getByText('接入任务', {exact: true})).toBeVisible();
        const createResponsePromise = page.waitForResponse((response) =>
            response.url().includes('/api/marketdata/ingestion-jobs') && response.request().method() === 'POST',
        );
        await page.getByRole('button', {name: '创建任务'}).click();
        const createResponse = await createResponsePromise;
        expect(createResponse.ok()).toBeTruthy();
        const createdJob = await createResponse.json();
        await expect(page.getByText(createdJob.jobId)).toBeVisible({timeout: 60_000});

        const runResponsePromise = page.waitForResponse((response) =>
            response.url().includes(`/api/marketdata/ingestion-jobs/${createdJob.jobId}/run-once`)
            && response.request().method() === 'POST',
        );
        await page.locator('tr').filter({hasText: createdJob.jobId}).getByRole('button', {name: 'Run once'}).click();
        const runResponse = await runResponsePromise;
        expect(runResponse.ok()).toBeTruthy();
        const run = await runResponse.json();
        await expect(page.getByText('运行结果')).toBeVisible();
        await expect(page.locator('.ant-table').last()).toBeVisible({timeout: 60_000});
        await expect(page.getByText(run.runId)).toBeVisible({timeout: 60_000});
        await expect(page.getByText(/SUCCEEDED|FAILED|PARTIAL/).first()).toBeVisible();
    });
});
