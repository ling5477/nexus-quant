import {expect, test} from 'playwright/test';
import {
    capabilityRow,
    loginForReadOnlyChecks,
    selectCapabilityVenue,
} from './read-only-console-support';

// 正式登录使用已有隔离身份，trace不能记录密码或Bearer请求头。
test.use({trace: 'off'});

/** 正式只读联调不拦截业务API，也不通过旧登录helper创建或重设账户。 */
test('正式策略接口的45项能力完整呈现且失败关闭，详情保留全部原始字段', async ({page}) => {
    const texts = (await loginForReadOnlyChecks(page)).adapterCapabilities;
    const responsePromise = page.waitForResponse(
        (response) =>
            response.url().endsWith('/api/adapters/readiness') &&
            response.request().method() === 'GET',
    );
    await page.goto('/adapter-readiness');
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    const payload = await response.json();
    const items: Array<{
        venue: string;
        capability: string;
        status: string;
        allowed: boolean;
        liveAuthorized: boolean;
        reasons: string[];
        message: string;
    }> = payload.items;
    expect(items).toHaveLength(45);
    expect(new Set(items.map((item) => `${item.venue}:${item.capability}`)).size).toBe(45);
    expect(
        items.every(
            (item) =>
                item.allowed === false && item.liveAuthorized === false && item.status !== 'READY',
        ),
    ).toBe(true);
    const secrets = ['secret', 'apikey', 'api_key', 'token', 'signature', 'passphrase'];
    for (const word of secrets) expect(JSON.stringify(payload).toLowerCase()).not.toContain(word);
    await expect(page.getByRole('heading', {name: texts.title, exact: true})).toBeVisible();
    await expect(
        page.getByText(texts.rowCount.replace('{{shown}}', '45').replace('{{total}}', '45')),
    ).toBeVisible();
    // 默认分页只显示9项；按每个venue逐项核对，不能用首屏行数代替完整能力范围。
    for (const venue of ['OKX', 'BINANCE', 'NOOP', 'PAPER', 'SIM']) {
        await selectCapabilityVenue(page, venue);
        for (const item of items.filter((item) => item.venue === venue)) {
            const row = capabilityRow(page, item.venue, item.capability);
            await expect(row).toContainText(
                venue === 'OKX' || venue === 'BINANCE'
                    ? texts.states.notReady
                    : texts.states.noReal,
            );
            await expect(row).toContainText(texts.notPermitted);
            await expect(row).toContainText(texts.notAuthorized);
        }
    }
    await selectCapabilityVenue(page, 'BINANCE');
    const captured = items.find(
        (item) => item.venue === 'BINANCE' && item.capability === 'CANCEL_ORDER',
    )!;
    await capabilityRow(page, 'BINANCE', 'CANCEL_ORDER')
        .getByRole('button', {name: texts.view, exact: true})
        .click();
    const detail = page.getByRole('dialog', {
        name: `BINANCE · ${texts.capabilities.CANCEL_ORDER}`,
        exact: true,
    });
    await detail.getByRole('button', {name: texts.original}).click();
    for (const value of [
        captured.capability,
        captured.status,
        captured.message,
        ...captured.reasons,
        '"allowed":false',
        '"liveAuthorized":false',
    ])
        await expect(detail).toContainText(value);
    await detail.getByRole('button', {name: /^(关闭|Close)$/}).click();
    await expect(detail).not.toBeVisible();
    await expect(page.getByText(texts.states.ready, {exact: true})).toHaveCount(0);
    await expect(page.getByText(texts.permitted, {exact: true})).toHaveCount(0);
    await expect(page.getByText(texts.authorized, {exact: true})).toHaveCount(0);
    const body = (await page.locator('body').innerText()).toLowerCase();
    expect(body).not.toContain('可交易');
    for (const word of secrets) expect(body).not.toContain(word);
});
