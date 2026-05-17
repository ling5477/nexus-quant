import {expect, test} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';

test.describe('backtest dataset binding', () => {
    test('可把 marketdata dataset 绑定到已有 backtest config', async ({page}) => {
        await loginToConsole(page);
        const session = await page.evaluate(() => JSON.parse(
            window.localStorage.getItem('nexus-quant.console.auth') ?? '{}',
        ));
        const authHeaders = {
            Authorization: `${session.tokenType ?? 'Bearer'} ${session.accessToken}`,
        };

        const datasetResponse = await page.request.post('http://127.0.0.1:18888/api/marketdata/datasets', {
            headers: authHeaders,
            timeout: 30_000,
            data: {
                datasetName: `e2e-dataset-${Date.now()}`,
                exchangeCode: 'BINANCE',
                marketType: 'SPOT',
                symbol: 'BTC-USDT',
                interval: '1m',
                startTime: '2025-01-01T00:00:00Z',
                endTime: '2025-01-01T00:05:59Z',
            },
        });
        expect(datasetResponse.ok()).toBeTruthy();
        const dataset = await datasetResponse.json();

        await page.goto('/backtests');
        await expect(page.getByRole('heading', {name: '回测配置'})).toBeVisible();

        const listResponsePromise = page.waitForResponse((response) =>
            response.url().includes('/api/backtest-configs') && response.request().method() === 'GET',
        );
        await page.getByRole('button', {name: /查\s*询/}).click();
        const listResponse = await listResponsePromise;
        expect(listResponse.ok()).toBeTruthy();
        const configs = await listResponse.json();
        test.skip(configs.length === 0, '当前环境没有可绑定的 backtest config，需先保留 GateF/G 的回测配置种子数据。');

        const configId = configs[0].backtestConfigId;
        await page.locator('tr').filter({hasText: configId}).getByRole('button', {name: '查看详情'}).click();
        await expect(page.getByText('回测配置详情')).toBeVisible();

        const bindResponsePromise = page.waitForResponse((response) =>
            response.url().includes(`/api/backtest-configs/${configId}/dataset`)
            && response.request().method() === 'PATCH',
        );
        await page.getByRole('combobox', {name: 'Dataset'}).click();
        await page.getByText(dataset.datasetName).click();
        await page.getByRole('button', {name: '绑定 Dataset'}).click();
        const bindResponse = await bindResponsePromise;
        expect(bindResponse.ok()).toBeTruthy();
        const bound = await bindResponse.json();
        expect(bound.datasetId).toBe(dataset.datasetId);

        await expect(page.getByText(dataset.datasetId).first()).toBeVisible({timeout: 60_000});
        await expect(page.getByText('Dataset Snapshot')).toBeVisible();
    });
});
