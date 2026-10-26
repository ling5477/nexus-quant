import {expect, test} from 'playwright/test';

/** 使用任务隔离后端与真实公开行情；失败保留正式任务状态，不能用响应替换制造成功。 */
for (const symbol of ['BTC-USDT', 'ETH-USDT']) {
    test(`${symbol}: 30 UTC days through ingestion, dataset quality and research selection`, async ({page}) => {
        test.setTimeout(180_000);
        await page.goto('/login');
        await page.getByLabel('账号', {exact: true}).fill(process.env.E2E_USERNAME ?? 'admin');
        await page.getByLabel('密码', {exact: true}).fill(process.env.E2E_PASSWORD ?? 'ChangeMe123!');
        await page.getByRole('button', {name: /登\s*录/}).click();
        await expect(page).toHaveURL(/\/dashboard$/, {timeout: 30_000});
        await page.getByRole('button', {name: '确认阅读', exact: true}).click();
        await page.goto('/marketdata?section=preparation&preparationTab=jobs');
        await page.getByRole('button', {name: '创建研究历史区间', exact: true}).click();
        const drawer = page.getByRole('dialog');
        await expect(drawer.getByText('开始日期（UTC，包含）')).toBeVisible();
        await expect(drawer.getByText('结束日期（UTC，不包含）')).toBeVisible();
        if (symbol === 'ETH-USDT') {
            await drawer.locator('.ant-select').filter({has: page.locator('#marketdata-stable-ingestion_symbol')}).click();
            await page.locator('.ant-select-item-option-content').filter({hasText: symbol}).click();
        }
        const createdResponse = page.waitForResponse((r) => r.request().method() === 'POST' && /\/api\/marketdata\/ingestion-jobs\/[^/]+$/.test(new URL(r.url()).pathname));
        await drawer.getByRole('button', {name: '创建采集任务', exact: true}).click();
        const response = await createdResponse;
        expect(response.ok(), await response.text()).toBeTruthy();
        const job = await response.json();
        expect(job.symbol).toBe(symbol);
        expect((Date.parse(job.endTime) + 1 - Date.parse(job.startTime)) / 3_600_000).toBe(720);
        const writes: string[] = [];
        const recordWrite = (request: {method(): string; url(): string}) => {
            if (['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method()) && !request.url().includes('/auth/')) writes.push(request.method() + ' ' + request.url());
        };
        page.on('request', recordWrite);
        await page.reload();
        await expect(page.getByRole('button', {name: '已查看，允许下一项操作', exact: true})).toBeVisible();
        expect(writes).toEqual([]);
        page.off('request', recordWrite);
        await page.getByRole('button', {name: '已查看，允许下一项操作', exact: true}).click();
        const runResponse = page.waitForResponse((r) => r.request().method() === 'POST' && r.url().includes(`/ingestion-jobs/${job.jobId}/runs/`), {timeout: 120_000});
        await page.getByRole('button', {name: '执行一次采集', exact: true}).last().click();
        const executed = await runResponse;
        expect(executed.ok(), await executed.text()).toBeTruthy();
        const run = await executed.json();
        expect(run.jobId).toBe(job.jobId);
        expect(run.status, run.errorMessage ?? '').toBe('SUCCEEDED');
        expect(run.fetchedBars).toBe(720);
        await page.getByRole('link', {name: '使用此区间创建数据集并检查质量'}).click();
        const form = page.locator('form').filter({has: page.locator('#marketdata-dataset_symbol')});
        await expect(form.getByText(symbol, {exact: true})).toBeVisible();
        const datasetResponse = page.waitForResponse((r) => r.request().method() === 'POST' && /\/api\/marketdata\/datasets\/[^/]+\/creation$/.test(new URL(r.url()).pathname));
        await page.getByRole('button', {name: '创建 Dataset', exact: true}).click();
        const datasetReceipt = await datasetResponse;
        expect(datasetReceipt.ok(), await datasetReceipt.text()).toBeTruthy();
        const operation = await datasetReceipt.json();
        expect(operation.state).toBe('SUCCEEDED');
        expect(operation.result.coverage.actualBars).toBe(720);
        expect(operation.result.coverage.missingBars).toBe(0);
        expect(operation.result.coverage.qualityStatus).toBe('OK');
        await page.getByTestId('dataset-operation-recovery').getByRole('link', {name: '使用此数据集创建研究'}).click();
        await expect(page.getByRole('dialog')).toBeVisible();
        await expect(page.getByRole('dialog').getByText('当前规则、费用及滑点均不代表历史当时事实。', {exact: false})).toBeVisible();
        await page.reload();
        await expect(page.getByRole('dialog')).toBeVisible();
        await expect(page.getByRole('dialog').getByLabel('选择数据集', {exact: true})).toHaveValue(new RegExp(symbol));
        await page.keyboard.press('Escape');
        await page.goto('/marketdata?section=preparation&preparationTab=jobs');
        await page.getByRole('button', {name: '已查看，允许下一项操作', exact: true}).click();
        await page.getByTestId('language-select').click();
        await page.getByText('English', {exact: true}).last().click();
        await page.getByRole('button', {name: 'Create research history window', exact: true}).click();
        await expect(page.getByRole('dialog').getByText('Start date (UTC, inclusive)')).toBeVisible();
        await expect(page.getByRole('dialog').getByText('End date (UTC, exclusive)')).toBeVisible();
        await page.keyboard.press('Escape');
        await page.getByTestId('language-select').click();
        await page.getByText('简体中文', {exact: true}).last().click();
    });
}
