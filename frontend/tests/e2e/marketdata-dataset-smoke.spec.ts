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

test.describe('marketdata dataset', () => {
    test('可创建 dataset 并查看质量状态', async ({page}) => {
        await loginToConsole(page);
        await page.goto('/marketdata');

        await expect(page.getByText('Datasets', {exact: true})).toBeVisible();

        // Why: 页面三个表单都有同名“开始时间/结束时间”字段，必须限定在 Datasets 卡片内定位。
        // 时间窗口沿用 dc1288e0 之前表单默认值的量级（6 分钟 1m bars）；后端只校验 endTime >= startTime，
        // 质量状态断言本身容忍 OK / GAP_DETECTED / INCOMPLETE / INVALID。
        const datasetCard = page.locator('.ant-card').filter({has: page.getByText('Datasets', {exact: true})});
        await fillDateTimePicker(datasetCard, '开始时间', '2025-01-01 00:00:00');
        await fillDateTimePicker(datasetCard, '结束时间', '2025-01-01 00:05:59');

        const createResponsePromise = page.waitForResponse((response) =>
            response.url().includes('/api/marketdata/datasets') && response.request().method() === 'POST',
        );
        await page.getByRole('button', {name: '创建 Dataset'}).click();
        const createResponse = await createResponsePromise;
        expect(createResponse.ok()).toBeTruthy();
        const dataset = await createResponse.json();

        await expect(page.getByText(dataset.datasetId)).toBeVisible({timeout: 60_000});
        await expect(page.getByText(/OK|GAP_DETECTED|INCOMPLETE|INVALID/).first()).toBeVisible();

        const refreshResponsePromise = page.waitForResponse((response) =>
            response.url().includes(`/api/marketdata/datasets/${dataset.datasetId}/refresh-quality`)
            && response.request().method() === 'POST',
        );
        await page.locator('tr').filter({hasText: dataset.datasetId}).getByRole('button', {name: 'Refresh quality'}).click();
        const refreshResponse = await refreshResponsePromise;
        expect(refreshResponse.ok()).toBeTruthy();
        const refreshed = await refreshResponse.json();
        expect(refreshed.datasetId).toBe(dataset.datasetId);
    });
});
