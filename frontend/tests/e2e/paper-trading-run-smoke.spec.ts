import {expect, test, type Locator} from 'playwright/test';

import {loginToConsole} from '@/../tests/e2e/support';
import {prepareGateI3PaperTradingFixture} from '@/../tests/e2e/paper-trading-fixtures';

async function selectRunFactTab(drawer: Locator, name: string) {
    const tab = drawer.getByRole('tab', {name, exact: true});
    await tab.scrollIntoViewIfNeeded();
    await tab.click();
    if ((await tab.getAttribute('aria-selected')) !== 'true') {
        // Why: backend smoke 视口下 Ant Design overflow tabs 可能只收到 pointer active 状态，
        // 但未切换 panel；native click 仍作用于同一个用户可见 tab button，不绕过业务状态。
        await tab.evaluate((element) => (element as HTMLElement).click());
    }
    await expect(tab).toHaveAttribute('aria-selected', 'true', {timeout: 10_000});
}

test.describe('GateI-3 paper trading run smoke', () => {
    test('Paper Trading 页面可创建、启动、停止 run，并展示订单/成交/持仓区域', async ({page}) => {
        await loginToConsole(page);

        const fixture = await prepareGateI3PaperTradingFixture(page);

        await page.goto('/paper-trading/runs');
        await expect(page).toHaveURL(/\/paper-trading\/runs$/);
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        await page.getByRole('button', {name: /查\s*询/}).click();
        await expect(page.getByText(/共 \d+ 条记录/)).toBeVisible();

        await page.getByRole('button', {name: /创建\s*Paper\s*Run/i}).click();
        const dialog = page.getByRole('dialog', {name: /创建\s*Paper\s*Trading/});
        await expect(dialog).toBeVisible();
        await dialog.getByPlaceholder('发布记录 ID（publishId）').fill(fixture.publishId);

        const createResponse = page.waitForResponse((response) => (
            response.url().includes('/api/paper-trading/runs')
            && response.request().method() === 'POST'
            && !response.url().endsWith('/start')
            && !response.url().endsWith('/stop')
        ));
        await page.getByRole('button', {name: 'OK', exact: true}).click();
        const created = await createResponse;
        expect(created.ok(), `create paper run failed: ${created.status()} ${await created.text()}`).toBeTruthy();
        const createdPayload = await created.json();
        const paperRunId: string = createdPayload.paperRunId;
        expect(paperRunId).toBeTruthy();
        expect(createdPayload.publishId).toBe(fixture.publishId);
        expect(createdPayload.status).toBe('CREATED');
        expect(createdPayload.strategyVersionId).toBe(fixture.strategyVersionId);

        const drawer = page.getByRole('region', {name: 'Paper Trading 详情'});
        await expect(drawer.getByText('Paper Run ID')).toBeVisible({timeout: 10_000});
        await expect(drawer.getByText(paperRunId).first()).toBeVisible();
        await expect(drawer.getByText('CREATED').first()).toBeVisible();

        const startResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/start`)
            && response.request().method() === 'POST'
        ));
        await expect(drawer.getByRole('button', {name: '启动 Paper Run'})).toBeEnabled();
        await drawer.getByRole('button', {name: '启动 Paper Run'}).click();
        const started = await startResponse;
        expect(started.ok()).toBeTruthy();
        const startedPayload = await started.json();
        expect(startedPayload.status).toBe('RUNNING');
        await expect(drawer.getByText('RUNNING').first()).toBeVisible({timeout: 15_000});

        const stopResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/stop`)
            && response.request().method() === 'POST'
        ));
        await expect(drawer.getByRole('button', {name: '停止 Paper Run'})).toBeEnabled();
        await drawer.getByRole('button', {name: '停止 Paper Run'}).click();
        const stopped = await stopResponse;
        expect(stopped.ok()).toBeTruthy();
        const stoppedPayload = await stopped.json();
        expect(stoppedPayload.status).toBe('STOPPED');
        await expect(drawer.getByText('STOPPED').first()).toBeVisible({timeout: 15_000});
        await expect(drawer.getByText('运行控制台')).toBeVisible();
        await expect(drawer.getByText('运行事实')).toBeVisible();
        await expect(drawer.getByText('生命周期操作仅作用于当前 SIM/Paper run')).toBeVisible();
        await expect(drawer.getByText('订单事实').first()).toBeVisible();
        await expect(drawer.getByText('成交事实').first()).toBeVisible();
        await expect(drawer.getByText('持仓事实').first()).toBeVisible();
        await expect(drawer.getByText('净 PnL').first()).toBeVisible();
        await expect(drawer.getByText('风控闭环').first()).toBeVisible();

        await selectRunFactTab(drawer, '订单');
        await expect(drawer.getByText('当前 Paper run 暂无订单事实。')).toBeVisible();

        await selectRunFactTab(drawer, '成交');
        await expect(drawer.getByText('当前 Paper run 暂无成交事实。')).toBeVisible();

        await selectRunFactTab(drawer, '持仓');
        await expect(drawer.getByText('当前 Paper run 暂无持仓事实。')).toBeVisible();

        await selectRunFactTab(drawer, '快照');
        await expect(drawer.getByText('Publish Snapshot')).toBeVisible();
        await expect(drawer.getByText('Strategy Version Snapshot')).toBeVisible();
    });
});

test.describe('GateI-4 paper trading monitor smoke', () => {
    test('风控检查、5 个监控 Tab、紧急停机完整链路', async ({page}) => {
        await loginToConsole(page);

        const fixture = await prepareGateI3PaperTradingFixture(page);

        // Create paper run via UI
        await page.goto('/paper-trading/runs');
        await expect(page.getByRole('heading', {name: '模拟交易'})).toBeVisible();

        await page.getByRole('button', {name: /创建\s*Paper\s*Run/i}).click();
        const dialog = page.getByRole('dialog', {name: /创建\s*Paper\s*Trading/});
        await expect(dialog).toBeVisible();
        await dialog.getByPlaceholder('发布记录 ID（publishId）').fill(fixture.publishId);

        const createResponse = page.waitForResponse((response) => (
            response.url().includes('/api/paper-trading/runs')
            && response.request().method() === 'POST'
            && !response.url().endsWith('/start')
            && !response.url().endsWith('/stop')
        ));
        await page.getByRole('button', {name: 'OK', exact: true}).click();
        const created = await createResponse;
        expect(created.ok(), `create paper run failed: ${created.status()}`).toBeTruthy();
        const createdPayload = await created.json();
        const paperRunId: string = createdPayload.paperRunId;

        // Start via UI
        const row = page.locator(`tr[data-row-key="${paperRunId}"]`);
        await expect(row).toBeVisible({timeout: 15_000});

        const startResponse = page.waitForResponse((response) => (
            response.url().endsWith(`/api/paper-trading/runs/${paperRunId}/start`)
            && response.request().method() === 'POST'
        ));
        await row.getByRole('link', {name: '启动'}).or(row.getByRole('button', {name: '启动'})).click();
        const started = await startResponse;
        expect(started.ok()).toBeTruthy();
        await expect(row.getByText('RUNNING')).toBeVisible({timeout: 15_000});

        // Open detail drawer
        await row.getByRole('link', {name: '查看详情'}).or(row.getByRole('button', {name: '查看详情'})).click();
        const drawer = page.getByRole('region', {name: 'Paper Trading 详情'});
        await expect(drawer.getByText('Paper Run ID')).toBeVisible({timeout: 10_000});

        // Tab: 风控结果 — click "执行风控检查" button
        await page.getByRole('tab', {name: '风控结果'}).click();
        const riskResponse = page.waitForResponse((response) => (
            response.url().includes(`/api/paper-trading/runs/${paperRunId}/risk-results/run-once`)
            && response.request().method() === 'POST'
        ));
        await page.getByRole('button', {name: /执行风控检查/}).click();
        const riskRes = await riskResponse;
        expect(riskRes.ok(), `risk run-once failed: ${riskRes.status()} ${await riskRes.text()}`).toBeTruthy();
        // checkType 同时出现在底部「风控结果」表与顶部状态条「风控状态」卡片，用 .first() 规避 strict 多匹配。
        await expect(page.getByText('BASIC_HEALTH_CHECK').first()).toBeVisible({timeout: 10_000});
        await expect(page.getByText('PASSED').first()).toBeVisible({timeout: 5_000});
        await expect(drawer.getByText('风控闭环')).toBeVisible();
        // 风控闭环指标卡 footer 精确文案；用 exact 规避与 summary 时间线风控描述
        // “BASIC_HEALTH_CHECK · LOW：...” 的 strict mode 多匹配。
        await expect(drawer.getByText('BASIC_HEALTH_CHECK · LOW', {exact: true})).toBeVisible({timeout: 10_000});

        // Tab: 资金曲线
        await selectRunFactTab(drawer, '资金曲线');
        await expect(drawer.getByText('当前 Paper run 暂无资金曲线数据。')).toBeVisible();

        // Tab: 持仓曲线
        await selectRunFactTab(drawer, '持仓曲线');
        await expect(drawer.getByText('当前 Paper run 暂无持仓曲线数据。')).toBeVisible();

        // Tab: 交易复盘
        await selectRunFactTab(drawer, '交易复盘');
        await expect(drawer.getByText('当前 Paper run 暂无交易复盘记录。')).toBeVisible();

        // 紧急停机现在位于右侧「操作区」（内联控制台），始终可见，无需切换 Tab。
        const esButton = page.getByRole('button', {name: /紧急停机/});
        await expect(esButton).toBeVisible({timeout: 5_000});
        await expect(esButton).toBeEnabled({timeout: 5_000});

        const esResponse = page.waitForResponse((response) => (
            response.url().includes(`/api/paper-trading/runs/${paperRunId}/emergency-stop`)
            && response.request().method() === 'POST'
        ));
        await esButton.click();
        await expect(page.getByText('此操作将立即停止当前 Paper run')).toBeVisible({timeout: 10_000});
        await page.getByRole('button', {name: '确认停机'}).click();
        const esRes = await esResponse;
        expect(esRes.ok()).toBeTruthy();
        const esPayload = await esRes.json();
        expect(esPayload.status).toBe('APPLIED');
        expect(esPayload.triggerType).toBe('MANUAL');

        // 内联控制台无需关闭抽屉；紧急停机后焦点 run 应在左侧列表显示 STOPPED。
        const stoppedRow = page.locator(`tr[data-row-key="${paperRunId}"]`);
        await expect(stoppedRow.getByText('STOPPED').first()).toBeVisible({timeout: 15_000});
    });
});
