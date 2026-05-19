import {expect, test} from 'playwright/test';

import {prepareGateI2EvaluationFixture} from '@/../tests/e2e/gatei2-fixtures';
import {loginToConsole} from '@/../tests/e2e/support';

test.describe('GateI-2 evaluation report enhanced smoke', () => {
    test('evaluations 页面展示核心增强指标并支持 empty 状态', async ({page}) => {
        test.setTimeout(180_000);

        await loginToConsole(page);
        const fixture = await prepareGateI2EvaluationFixture(page);

        await page.goto('/evaluations');
        await expect(page.getByRole('heading', {name: '评估结果'})).toBeVisible();
        await expect(page.getByText('点击查询后加载评估结果列表。')).toBeVisible();

        const listResponsePromise = page.waitForResponse((response) =>
            response.url().includes('/api/evaluations') && response.request().method() === 'GET',
        );
        await page.getByRole('button', {name: /查\s*询/}).click();
        const listResponse = await listResponsePromise;
        expect(listResponse.ok()).toBeTruthy();

        await expect(page.getByRole('columnheader', {name: '总收益'})).toBeVisible();
        await expect(page.getByRole('columnheader', {name: '最大回撤'})).toBeVisible();
        await expect(page.getByRole('columnheader', {name: '胜率'})).toBeVisible();
        await expect(page.getByRole('columnheader', {name: '盈亏比'})).toBeVisible();
        await expect(page.getByRole('columnheader', {name: '成交数'})).toBeVisible();
        await expect(page.getByRole('columnheader', {name: 'Sharpe'})).toBeVisible();
        await expect(page.getByText(fixture.evalReportId)).toBeVisible({timeout: 60_000});

        await page.locator('tr').filter({hasText: fixture.evalReportId})
            .getByRole('button', {name: '查看详情'})
            .click();
        await expect(page.getByText('评估详情')).toBeVisible();
        await expect(page.getByText(fixture.backtestRunId).first()).toBeVisible();
        await expect(page.getByText('Metrics JSON').first()).toBeVisible();
        await expect(page.getByText('年化收益').first()).toBeVisible();
        await expect(page.getByText('总收益率').first()).toBeVisible();
    });
});
