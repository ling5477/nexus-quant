import {expect, test} from 'playwright/test';

test.use({timezoneId: 'UTC'});

test('正式登录后从行情页面捕获 OKX 公开已关闭小时线并生成 Dataset', async ({page}) => {
    test.setTimeout(90_000);
    const username = process.env.E2E_USERNAME;
    const password = process.env.E2E_PASSWORD;
    expect(username, 'E2E_USERNAME must identify a formally bootstrapped user').toBeTruthy();
    expect(password, 'E2E_PASSWORD must be provided for that user').toBeTruthy();

    await page.goto('/login');
    await page.getByLabel('账号').fill(username!);
    await page.getByLabel('密码').fill(password!);
    await page.getByRole('button', {name: /登\s*录/}).click();
    await expect(page).toHaveURL(/\/dashboard$/, {timeout: 15_000});

    await page.goto('/marketdata');
    const capture = page.getByTestId('public-market-capture');
    await expect(capture.getByText('OKX 公开历史行情捕获')).toBeVisible();

    const hourMs = 3_600_000;
    const end = new Date(Math.floor((Date.now() - 2 * hourMs) / hourMs) * hourMs);
    const start = new Date(end.getTime() - 72 * hourMs);
    const utcText = (date: Date) => date.toISOString().slice(0, 19).replace('T', ' ');
    const startInput = capture.locator('.ant-form-item').filter({hasText: '开始时间'}).locator('input');
    const endInput = capture.locator('.ant-form-item').filter({hasText: '结束时间'}).locator('input');
    await startInput.click();
    await startInput.fill(utcText(start));
    await startInput.press('Enter');
    await endInput.click();
    await endInput.fill(utcText(end));
    await endInput.press('Enter');

    const responsePromise = page.waitForResponse((response) =>
        response.url().includes('/api/marketdata/public-captures')
        && response.request().method() === 'POST', {timeout: 45_000});
    await capture.getByRole('button', {name: '捕获公开行情'}).click();
    const response = await responsePromise;
    expect(response.ok(), await response.text()).toBeTruthy();
    const result = await response.json();
    expect(result.venue).toBe('OKX');
    expect(result.instrument).toBe('BTC-USDT');
    expect(result.interval).toBe('1h');
    expect(result.barCount).toBe(72);
    await expect(capture.getByText(result.datasetId)).toBeVisible();
    await expect(page.locator('.ant-table-row').filter({hasText: result.datasetId})).toBeVisible();
});
