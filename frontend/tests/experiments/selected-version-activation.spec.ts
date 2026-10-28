import {readFileSync} from 'node:fs';
import {expect, test, type Page} from 'playwright/test';

interface SelectedFixture {sourceId: string; validationId: string; trialId: string; strategyVersionId: string; configId: string; runId: string}
const fixture = JSON.parse(readFileSync(process.env.NQ_ACTIVATION_E2E_READY!, 'utf8')) as {selected: Record<string, SelectedFixture>};
test.use({actionTimeout: 10_000, trace: 'off'});
async function login(page: Page, firstLogin = false) {
    await page.goto('/login');
    await page.getByLabel('账号', {exact: true}).fill('experiment-owner');
    await page.getByLabel('密码', {exact: true}).fill('test-password');
    await page.getByRole('button', {name: /登\s*录/}).click();
    await expect(page).toHaveURL(/dashboard$/);
    if (firstLogin) {
        await expect(page.getByRole('button', {name: '确认阅读', exact: true})).toBeVisible();
        await page.getByRole('checkbox', {name: '下次登录不再自动提示', exact: true}).check();
        await page.getByRole('button', {name: '确认阅读', exact: true}).click();
    }
    if (await page.getByRole('button', {name: '已阅读', exact: true}).count())
        await page.getByRole('button', {name: '已阅读', exact: true}).click();
}

test('真实SMA及通道WF选中版本经人工激活和正式发布；丢失响应只读恢复，中英文390px', async ({page}, testInfo) => {
    test.setTimeout(180_000);
    await login(page, true);
    for (const [index, type] of ['SPOT_SMA_TARGET_V1', 'SPOT_CHANNEL_BREAKOUT_V1'].entries()) {
        const selected = fixture.selected[type];
        const writes: string[] = [];
        page.on('request', request => {if (request.method() === 'POST' && request.url().endsWith('/selected-version/activate')) writes.push(request.url());});
        const url = `/experiments?experimentId=${selected.sourceId}&validationId=${selected.validationId}`;
        await page.goto(url);
        let card = page.getByTestId('selected-version-activation');
        await expect(card.getByText('DRAFT', {exact: true})).toBeVisible();
        await expect(card.getByText(selected.strategyVersionId, {exact: true})).toBeVisible();
        await page.reload();
        await expect(card.getByText('DRAFT', {exact: true})).toBeVisible();
        expect(writes).toHaveLength(0);
        if (index === 1) {
            await page.setViewportSize({width: 390, height: 844});
            await page.getByTestId('language-select').click();
            await page.getByText('English', {exact: true}).last().click();
            await expect(card.getByText('Activate final selected version', {exact: true})).toBeVisible();
        }
        if (index === 0) {
            await page.route(`**/validations/${selected.validationId}/selected-version/activate`, async route => {
                const response = await route.fetch();
                expect(response.status()).toBe(200);
                await route.abort('failed');
            }, {times: 1});
        }
        await card.getByRole('button', {name: index === 0 ? '激活选中版本' : 'Activate selected version', exact: true}).click();
        const dialog = page.getByRole('tooltip');
        await dialog.getByRole('button', {name: index === 0 ? '激活选中版本' : 'Activate selected version', exact: true}).click();
        await expect(card.getByText('ACTIVE', {exact: true})).toBeVisible({timeout: 20_000});
        expect(writes).toHaveLength(1);
        await page.reload();
        await expect(card.getByText('ACTIVE', {exact: true})).toBeVisible();
        expect(writes).toHaveLength(1);
        const box = await card.boundingBox();
        expect(box).not.toBeNull();
        expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width + 1);
        await card.screenshot({path: testInfo.outputPath(`selected-version-activation-${index === 0 ? 'desktop-zh' : '390-en'}.png`)});
        if (index === 0) {
            await page.getByRole('button', {name: '退出登录', exact: true}).click();
            await login(page);
            await page.goto(url);
            await expect(card.getByText('ACTIVE', {exact: true})).toBeVisible();
            expect(writes).toHaveLength(1);
        }
        await card.getByRole('link', {name: index === 0 ? '进入原回测的正式发布流程' : 'Open the original backtest publish workflow', exact: true}).click();
        await expect(page).toHaveURL(new RegExp(`/backtests\\?backtestConfigId=${selected.configId}&backtestRunId=${selected.runId}`));
        const [response] = await Promise.all([
            page.waitForResponse(response => {
                const url = new URL(response.url());
                return response.request().method() === 'POST' && url.pathname === '/api/publishes'
                    && url.searchParams.get('backtestRunId') === selected.runId;
            }),
            page.getByRole('button', {name: index === 0 ? '发布此运行' : 'Publish this run', exact: true}).click(),
        ]);
        expect(response.ok(), await response.text()).toBeTruthy();
        expect((await response.json()).publishStatus).toBe('SUCCEEDED');
        expect(writes).toHaveLength(1);
        await page.goto(url);
        await expect(card.getByText('ACTIVE', {exact: true})).toBeVisible();
        expect(writes).toHaveLength(1);
    }
});
