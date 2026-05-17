import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('marketdata dataset', () => {
    test('可创建 dataset 并查看质量状态', async ({page}) => {
        await loginToConsole(page);
        await page.goto('/marketdata');

        await expect(page.getByText('Datasets', {exact: true})).toBeVisible();

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
