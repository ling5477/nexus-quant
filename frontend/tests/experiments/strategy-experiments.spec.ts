import {readFileSync} from 'node:fs';
import {expect, test, type Page} from 'playwright/test';

const fixture = JSON.parse(readFileSync(process.env.NQ_EXPERIMENT_E2E_READY!, 'utf8')) as {templates: Record<string, string>};
test.use({actionTimeout: 10_000});
async function login(page: Page) {
    await page.goto('/login');
    await page.getByLabel('账号', {exact: true}).fill('experiment-owner');
    await page.getByLabel('密码', {exact: true}).fill('test-password');
    await page.getByRole('button', {name: /登\s*录/}).click();
    await expect(page).toHaveURL(/dashboard$/);
    await page.getByRole('button', {name: '确认阅读', exact: true}).click();
}

test('真实后端的两类四候选实验、取消、比较、详情与只读恢复', async ({page}) => {
    await login(page);
    await page.goto('/experiments');
    await expect(page.getByRole('heading', {name: '策略参数实验'})).toBeVisible();
    for (const type of ['SPOT_SMA_TARGET_V1', 'SPOT_CHANNEL_BREAKOUT_V1']) {
        console.info(`EXPERIMENT_UI_SELECT ${type}`);
        await page.getByLabel('回测配置模板（已绑定版本和数据集）').press('ArrowDown');
        await page.getByLabel('回测配置模板（已绑定版本和数据集）').fill(fixture.templates[type]);
        await page.getByTitle(`Experiment template ${type} · ${fixture.templates[type]}`, {exact: true}).click();
        await page.getByLabel('策略类型', {exact: true}).press('ArrowDown');
        await page.getByTitle(type, {exact: true}).last().click();
        console.info(`EXPERIMENT_UI_PREVIEW ${type}`);
        await page.getByRole('button', {name: '预览试验数量'}).click();
        await expect(page.getByText('共 4 条合法试验')).toBeVisible();
        const created = page.waitForResponse(r => r.url().includes('/strategy-experiments/operations/') && r.request().method() === 'POST');
        await page.getByRole('button', {name: '提交实验'}).click();
        const response = await created;
        expect(response.ok(), await response.text()).toBeTruthy();
        const result = await response.json();
        console.info(`EXPERIMENT_UI_SUBMITTED ${type} ${result.experimentId}`);
        await expect(page).toHaveURL(new RegExp(result.experimentId));
        const results = page.getByRole('table').filter({has: page.getByRole('columnheader', {name: '参数组合', exact: true})});
        await expect(results.getByText('全部成功', {exact: true})).toHaveCount(4, {timeout: 35_000});
        await expect(results.getByText('回测详情', {exact: true})).toHaveCount(4);
        await expect(results.getByText('评价详情', {exact: true})).toHaveCount(4);
        expect(result.trials).toHaveLength(4);
        const writes: string[] = [];
        const listener = (r: import('playwright/test').Request) => {if (r.url().includes('/strategy-experiments') && r.method() !== 'GET') writes.push(r.url());};
        page.on('request', listener);
        await page.reload();
        await expect(page.getByRole('table').filter({has: page.getByRole('columnheader', {name: '参数组合', exact: true})}).getByText('全部成功', {exact: true})).toHaveCount(4);
        expect(writes).toHaveLength(0);
        page.off('request', listener);
        await page.getByRole('combobox', {name: '选择试验进行对比（留空显示全部）'}).click();
        for (const trial of result.trials.slice(0, 2)) await page.getByTitle(`${trial.ordinal} / ${trial.attempt} · ${trial.parameterIdentity}`, {exact: true}).click();
        await page.keyboard.press('Escape');
        await expect(page.getByRole('table').filter({has: page.getByRole('columnheader', {name: '参数组合', exact: true})}).getByText('回测详情', {exact: true})).toHaveCount(2);
        await page.getByRole('link', {name: '回测详情', exact: true}).first().click();
        await expect(page).toHaveURL(/\/backtests\/bcf-.*backtestRunId=brn-/);
        await page.goto(`/experiments?experimentId=${result.experimentId}`);
        await page.getByRole('link', {name: '评价详情', exact: true}).first().click();
        await expect(page).toHaveURL(/\/evaluations\?evaluationId=/);
        await page.goto('/experiments');
    }
    await page.getByLabel('回测配置模板（已绑定版本和数据集）').press('ArrowDown');
    await page.getByLabel('回测配置模板（已绑定版本和数据集）').fill(fixture.templates.SPOT_SMA_TARGET_V1);
    await page.getByTitle(`Experiment template SPOT_SMA_TARGET_V1 · ${fixture.templates.SPOT_SMA_TARGET_V1}`, {exact: true}).click();
    await page.getByRole('button', {name: '预览试验数量'}).click();
    await expect(page.getByText('共 4 条合法试验')).toBeVisible();
    await page.getByRole('button', {name: '提交实验'}).click();
    await page.getByRole('button', {name: '取消未开始任务'}).click();
    await page.getByRole('button', {name: /确\s*定/}).click();
    await expect(page.getByRole('table').filter({has: page.getByRole('columnheader', {name: '参数组合', exact: true})}).getByText('已取消后续任务', {exact: true}).first()).toBeVisible();
    const preserved = page.url();
    await page.getByRole('button', {name: '退出登录', exact: true}).click();
    await login(page);
    await page.goto(preserved);
    await expect(page.getByRole('table').filter({has: page.getByRole('columnheader', {name: '参数组合', exact: true})}).getByText('已取消后续任务', {exact: true}).first()).toBeVisible();
    await page.screenshot({path: 'test-results/strategy-experiments-desktop.png', fullPage: true});
    await page.setViewportSize({width: 390, height: 844});
    await page.reload();
    await expect(page.getByRole('heading', {name: '策略参数实验'})).toBeVisible();
    await page.screenshot({path: 'test-results/strategy-experiments-mobile.png', fullPage: true});
});

test('390px 窄屏只读恢复已有实验', async ({page}) => {
    await page.setViewportSize({width: 390, height: 844});
    await login(page);
    const writes: string[] = [];
    page.on('request', request => {if (request.url().includes('/strategy-experiments') && request.method() !== 'GET') writes.push(request.url());});
    await page.goto('/experiments');
    await page.getByRole('button', {name: /^[a-f0-9-]{36}$/i}).first().click();
    await expect(page.getByRole('columnheader', {name: '参数组合', exact: true})).toBeVisible();
    await expect(page.getByRole('dialog')).not.toBeVisible();
    expect(writes).toHaveLength(0);
    await page.screenshot({path: 'test-results/strategy-experiments-mobile.png', fullPage: true});
});

test('明确拒绝后恢复若丢失响应，必须继续保留同一幂等身份', async ({page}) => {
    await login(page);
    await page.goto('/experiments');
    await page.getByLabel('回测配置模板（已绑定版本和数据集）').press('ArrowDown');
    await page.getByLabel('回测配置模板（已绑定版本和数据集）').fill(fixture.templates.SPOT_SMA_TARGET_V1);
    await page.getByTitle(`Experiment template SPOT_SMA_TARGET_V1 · ${fixture.templates.SPOT_SMA_TARGET_V1}`, {exact: true}).click();
    await page.getByRole('button', {name: '预览试验数量'}).click();
    await expect(page.getByText('共 4 条合法试验')).toBeVisible();
    let requestCount = 0;
    let committed: {experimentId: string} | undefined;
    await page.route('**/api/strategy-experiments/operations/*', async route => {
        requestCount++;
        if (requestCount === 1) {
            await route.fulfill({status: 403, contentType: 'application/json', body: JSON.stringify({status: 403, code: 'FORBIDDEN', message: 'SYNTHETIC_REJECTION'})});
        } else if (requestCount === 2) {
            const result = await route.fetch();
            expect(result.ok(), await result.text()).toBeTruthy();
            committed = await result.json();
            await route.abort('failed');
        } else await route.continue();
    });
    await page.getByRole('button', {name: '提交实验'}).click();
    await expect(page.getByRole('button', {name: '放弃已明确拒绝的请求'})).toBeVisible();
    await expect(page.getByRole('button', {name: '恢复原请求'})).not.toHaveClass(/ant-btn-loading/);
    const original = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('nq:experiment-creation:'))?.[1]);
    expect(original).toBeTruthy();
    await page.getByRole('button', {name: '恢复原请求'}).click();
    await expect(page.getByRole('button', {name: '放弃已明确拒绝的请求'})).toHaveCount(0);
    await expect(page.getByRole('button', {name: '恢复原请求'})).toBeEnabled();
    const unresolved = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('nq:experiment-creation:'))?.[1]);
    expect(JSON.parse(unresolved!).id).toBe(JSON.parse(original!).id);
    expect(JSON.parse(unresolved!).rejected).toBeUndefined();
    await page.getByRole('button', {name: '恢复原请求'}).click();
    await expect(page).toHaveURL(new RegExp(committed!.experimentId));
    expect(requestCount).toBe(3);
});
