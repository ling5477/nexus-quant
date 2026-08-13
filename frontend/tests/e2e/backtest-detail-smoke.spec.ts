import {expect, test} from 'playwright/test';

import {prepareGateI2BacktestTraceFixture, prepareGateI2EvaluationFixture} from '@/../tests/e2e/gatei2-fixtures';
import {loginToConsole} from '@/../tests/e2e/support';

/**
 * BacktestDetailPage 页面级 E2E(B1.2)。
 *
 * 走真实后端 + 真实登录态 + 真实 fixture(strategy/version/research/backtest config + run + evaluate),
 * 验证 B1 / B1.1 回测详情页:有真实 pnl snapshots 的 run 渲染权益/回撤曲线,且指标/快照/摘要不回退;
 * 无 run(无可用序列)时显式 unavailable。不伪造数据、不接 socket。
 *
 * 依赖:本地后端(默认 127.0.0.1:18888)+ E2E_USERNAME/E2E_PASSWORD。后端不可用时本 spec 无法运行(见 TESTING.md)。
 */
test.describe('backtest detail page smoke (B1.2)', () => {
    test('有真实 pnl snapshots 的 run:权益/回撤曲线渲染,指标/快照/摘要不回退', async ({page}) => {
        test.setTimeout(180_000);

        await loginToConsole(page);
        // 真实链路:创建 config → 创建 run → start(执行,写入 sim_pnl_snapshots)→ evaluate(生成评估)。
        const fixture = await prepareGateI2EvaluationFixture(page);

        const pnlResponsePromise = page.waitForResponse((response) =>
            response.url().includes(`/api/backtest-runs/${fixture.backtestRunId}/pnl-snapshots`)
            && response.request().method() === 'GET',
        );
        await page.goto(`/backtests/${fixture.backtestConfigId}`);
        await expect(page.getByRole('heading', {name: '回测详情可视化'})).toBeVisible();

        const pnlResponse = await pnlResponsePromise;
        expect(pnlResponse.ok()).toBeTruthy();
        const snapshots = await pnlResponse.json();
        expect(Array.isArray(snapshots) && snapshots.length > 0,
            '执行后的回测 run 应有非空 sim_pnl_snapshots').toBeTruthy();

        // 权益/回撤曲线渲染为 ECharts canvas(有序列 → 不显示 unavailable)。
        await expect(page.getByText('权益 / 回撤曲线')).toBeVisible();
        await expect(page.locator('canvas').first()).toBeVisible({timeout: 30_000});
        await expect(page.getByText('所选评估缺少 backtestRunId')).toHaveCount(0);

        // 指标 / 摘要 / 快照区不回退。
        await expect(page.getByText('关键指标摘要')).toBeVisible();
        await expect(page.getByText('尚无评估结果。请先运行回测并执行评估后再查看指标。')).toHaveCount(0);
        await expect(page.getByRole('table', {name: '交易风险摘要'})).toBeVisible();
        await expect(page.getByText('数据集快照')).toBeVisible();
        await expect(page.getByText('参数 / 策略快照')).toBeVisible();
    });

    test('无 run(无可用序列):权益/回撤曲线显示明确 unavailable,指标空态不回退', async ({page}) => {
        test.setTimeout(180_000);

        await loginToConsole(page);
        // 只建 config(绑定 strategy version + dataset),不创建 run / 不评估 → 无可定位的回测运行序列。
        const fixture = await prepareGateI2BacktestTraceFixture(page);

        await page.goto(`/backtests/${fixture.backtestConfigId}`);
        await expect(page.getByRole('heading', {name: '回测详情可视化'})).toBeVisible();

        // 无 run → 曲线显式 unavailable(不编造)。
        const unavailableNotes = page.getByText('所选评估缺少 backtestRunId');
        await expect(unavailableNotes).toHaveCount(2);
        await expect(unavailableNotes.first()).toBeVisible({timeout: 30_000});
        // 指标空态:明确提示,不回退成报错或假数据。
        await expect(page.getByText('尚无评估结果。请先运行回测并执行评估后再查看指标。')).toBeVisible();
        // 数据集快照仍可见(config 已绑定 dataset),证明非曲线区不受影响。
        await expect(page.getByText('数据集快照')).toBeVisible();
    });
});
