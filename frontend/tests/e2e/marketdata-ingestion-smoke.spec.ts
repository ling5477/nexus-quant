import {expect, test, type Locator} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

// Why: fix(freeze) dc1288e0 把开始/结束时间改成必填 DatePicker，不先填日期则 AntD 表单校验
// 拦截提交，POST 不会发出。AntD DatePicker 无法直接 setValue，需点击输入框、键入完整
// 'YYYY-MM-DD HH:mm:ss' 文本后回车，picker 才会把文本提交为表单值。
async function fillDateTimePicker(card: Locator, label: string, value: string): Promise<void> {
    const input = card.locator('.ant-form-item').filter({hasText: label}).locator('input');
    await input.click();
    await input.fill(value);
    await input.press('Enter');
}

test.describe('marketdata ingestion', () => {
    test('可创建接入任务并执行 run-once，外网失败时保留可查询失败状态', async ({page}) => {
        test.setTimeout(120_000);

        await loginToConsole(page);
        await page.goto('/marketdata');

        await expect(page.getByText('接入任务', {exact: true})).toBeVisible();

        // Why: 页面三个表单都有同名“开始时间/结束时间”字段，必须限定在接入任务卡片内定位。
        // 时间窗口沿用 dc1288e0 之前表单默认值的量级（6 分钟 1m bars）；后端只校验 endTime >= startTime，
        // run-once 状态断言本身容忍 SUCCEEDED / FAILED / PARTIAL（外网不可达时为 FAILED）。
        const jobCard = page.locator('.ant-card').filter({has: page.getByText('接入任务', {exact: true})});
        await fillDateTimePicker(jobCard, '开始时间', '2025-01-01 00:00:00');
        await fillDateTimePicker(jobCard, '结束时间', '2025-01-01 00:05:59');

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
