import {expect, test} from 'playwright/test';

test('回测创建保留公共行情数据集所需的毫秒边界，不自动启动运行', async ({page}) => {
    const writes: {method: string; path: string; body: Record<string, unknown>}[] = [];
    await page.addInitScript(() => {
        localStorage.setItem('nq.locale', 'zh-CN');
        localStorage.setItem('nexus-quant.console.auth', JSON.stringify({accessToken: 'fixture',
            tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'fixture', roles: ['ADMIN']}));
    });
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (request.method() !== 'GET') {
            const body = request.postDataJSON();
            writes.push({method: request.method(), path, body});
            return route.fulfill({json: {...body, backtestConfigId: 'precise-config'}});
        }
        if (path === '/api/auth/me') return route.fulfill({json: {authenticated: true, username: 'fixture', roles: ['ADMIN']}});
        if (path === '/api/backtest-configs/precise-config') return route.fulfill({json: {
            ...writes[0]?.body, backtestConfigId: 'precise-config'}});
        return route.fulfill({json: []});
    });
    await page.goto('/backtests?researchConfigId=research-precise&create=true');
    const drawer = page.locator('.ant-drawer-open');
    await drawer.getByLabel('名称', {exact: true}).fill('精确数据边界');
    await drawer.getByLabel('开始时间', {exact: true}).fill('2026-10-05 16:00:00.000');
    await drawer.getByLabel('开始时间', {exact: true}).press('Enter');
    await drawer.getByLabel('结束时间', {exact: true}).fill('2026-10-08 15:59:59.999');
    await drawer.getByLabel('结束时间', {exact: true}).press('Enter');
    await drawer.getByLabel('初始资金', {exact: true}).fill('1000');
    await drawer.getByLabel('执行参数', {exact: true}).fill('{}');
    await drawer.getByLabel('评估参数', {exact: true}).fill('{}');
    await drawer.getByRole('button', {name: '提交创建', exact: true}).click();
    await expect(page).toHaveURL(/backtestConfigId=precise-config/);
    expect(writes).toHaveLength(1);
    expect(writes[0]).toMatchObject({method: 'POST', path: '/api/backtest-configs',
        body: {researchConfigId: 'research-precise', initialCapital: 1000}});
    expect(writes[0].body.startTime).toMatch(/\.000Z$/);
    expect(writes[0].body.endTime).toMatch(/:59\.999Z$/);
});

for (const publishStatus of ['SUCCEEDED', 'FAILED']) {
    test(`首次回测显式动作、URL恢复、配置隔离和发布 ${publishStatus}`, async ({page}) => {
        const posts: {path: string; body: unknown}[] = [];
        const reads: string[] = [];
        const config = (id: string) => ({backtestConfigId: id, researchConfigId: 'research-a', name: id,
            initialCapital: 1000, executionSpec: '{}', evaluationSpec: '{}', configSnapshot: '{}',
            strategyVersionId: 'version-a', datasetId: 'dataset-a'});
        const run = {backtestRunId: 'run-a', backtestConfigId: 'config-a', researchConfigId: 'research-a',
            strategyVersionId: 'version-a', status: 'CREATED', evaluationStatus: null as string | null,
            publishStatus: null as string | null};
        const report = {evalReportId: 'report-a', backtestRunId: 'run-a', evaluationStatus: 'SUCCEEDED'};
        await page.addInitScript(() => {
            localStorage.setItem('nq.locale', 'zh-CN');
            localStorage.setItem('nexus-quant.console.auth', JSON.stringify({accessToken: 'fixture',
                tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'fixture', roles: ['ADMIN']}));
        });
        await page.route('**/api/**', async route => {
            const request = route.request();
            const path = new URL(request.url()).pathname;
            if (request.method() === 'POST') {
                posts.push({path, body: request.postDataJSON()});
                await new Promise(resolve => setTimeout(resolve, 200));
            } else reads.push(path);
            const reply = (json: unknown, status = 200) => route.fulfill({status, json});
            if (path === '/api/auth/me') return reply({userId: 1, username: 'fixture', roles: ['ADMIN'], authenticated: true});
            if (path === '/api/backtest-configs') return reply([config('config-a'), config('config-b')]);
            if (path.startsWith('/api/backtest-configs/')) return reply(config(path.split('/').at(-1)!));
            if (path === '/api/backtest-runs/run-a/start') {run.status = 'SUCCEEDED'; return reply(run);}
            if (path === '/api/backtest-runs/run-a/evaluate') {run.evaluationStatus = 'SUCCEEDED'; return reply(report);}
            if (path === '/api/backtest-runs/run-a/evaluation') return reply(report, run.evaluationStatus ? 200 : 404);
            if (path === '/api/backtest-runs/run-a') return reply(run);
            if (path === '/api/publishes' && request.method() === 'POST') {
                expect(new URL(request.url()).searchParams.get('backtestRunId')).toBe('run-a');
                run.publishStatus = publishStatus;
                return reply({publishRecordId: 'publish-a', backtestRunId: 'run-a', publishStatus, failureCode: publishStatus === 'FAILED' ? 'ADMISSION_DENIED' : null});
            }
            return reply([]);
        });
        await page.goto('/backtests?backtestConfigId=config-a&backtestRunId=run-a');
        const actions = page.getByTestId('backtest-run-actions');
        await expect(actions).toBeVisible();
        await page.goto('/backtests?backtestConfigId=config-b&backtestRunId=run-a');
        await expect(page.getByText('此运行不属于当前回测配置，请选择匹配的运行。')).toBeVisible();
        await expect(actions).toHaveCount(0);
        expect(posts).toHaveLength(0);
        expect(reads).not.toContain('/api/backtest-runs/run-a/evaluation');
        await page.goto('/backtests?backtestConfigId=config-a&backtestRunId=run-a');
        await expect(actions).toBeVisible();
        expect(posts).toHaveLength(0);
        expect(reads).not.toContain('/api/backtest-runs/run-a/evaluation');
        await expect(actions.getByRole('button', {name: '评估此运行'})).toBeDisabled();
        await actions.getByRole('button', {name: '启动回测'}).click();
        await expect(actions.getByRole('button', {name: '启动回测'})).toBeDisabled();
        await expect(actions.getByRole('button', {name: '评估此运行'})).toBeEnabled();
        expect(reads).not.toContain('/api/backtest-runs/run-a/evaluation');
        await actions.getByRole('button', {name: '评估此运行'}).click();
        await expect(actions.getByRole('button', {name: '评估此运行'})).toBeDisabled();
        await expect(actions.getByRole('button', {name: '发布此运行'})).toBeEnabled();
        await actions.getByRole('button', {name: '发布此运行'}).click();
        await expect(actions.getByRole('button', {name: '发布此运行'})).toBeDisabled();
        await expect(actions.getByText('publish-a', {exact: true})).toBeVisible();
        if (publishStatus === 'SUCCEEDED') {
            await expect(actions.getByRole('link', {name: '前往模拟运行'})).toHaveAttribute('href', '/paper-trading/runs?publishId=publish-a');
        } else {
            await expect(actions.getByText('发布未成功，请查看状态与原因。')).toBeVisible();
            await expect(actions).toContainText('ADMISSION_DENIED');
            await expect(actions.getByRole('link', {name: '前往模拟运行'})).toHaveCount(0);
            await expect(page.locator('.ant-message-success')).toHaveCount(0);
        }
        expect(posts.map(p => p.path)).toEqual(['/api/backtest-runs/run-a/start', '/api/backtest-runs/run-a/evaluate', '/api/publishes']);
        expect(posts.at(-1)?.body).toEqual({strategyVersionId: 'version-a'});
        await page.getByRole('link', {name: '可视化', exact: true}).click();
        await expect(page).toHaveURL(/backtests\/config-a\?backtestRunId=run-a/);
        await expect.poll(() => reads.filter(p => p === '/api/backtest-runs/run-a/evaluation').length).toBeGreaterThan(0);
        const reportReadCount = reads.filter(p => p === '/api/backtest-runs/run-a/evaluation').length;
        await page.evaluate(() => {
            history.pushState({}, '', '/backtests/config-b?backtestRunId=run-a');
            dispatchEvent(new PopStateEvent('popstate'));
        });
        await expect(page.getByText('此运行不属于当前回测配置，请选择匹配的运行。')).toBeVisible();
        await expect(page.getByText('report-a', {exact: true})).toHaveCount(0);
        await page.getByRole('button', {name: /刷\s*新/, exact: true}).click();
        await expect(page.getByText('此运行不属于当前回测配置，请选择匹配的运行。')).toBeVisible();
        expect(reads.filter(p => p === '/api/backtest-runs/run-a/evaluation')).toHaveLength(reportReadCount);
        await page.goto('/backtests?backtestConfigId=config-a&backtestRunId=run-a');
        await page.reload();
        await expect(actions).toContainText('run-a');
        expect(posts).toHaveLength(3);
        await page.locator('.ant-drawer').getByRole('button', {name: /Close|关闭/}).click();
        await page.getByRole('button', {name: /查\s*询/}).click();
        await page.locator('tr').filter({hasText: 'config-a'}).getByRole('button', {name: '查看详情'}).click();
        await expect(actions).toContainText('run-a');
        await page.locator('.ant-drawer').getByRole('button', {name: /Close|关闭/}).click();
        await page.locator('tr').filter({hasText: 'config-b'}).getByRole('button', {name: '查看详情'}).click();
        await expect(page.locator('.ant-drawer')).toContainText('config-b');
        await expect(actions).toHaveCount(0);
        await expect(page).toHaveURL(/backtestConfigId=config-b$/);
        await page.reload();
        await expect(page.locator('.ant-drawer')).toContainText('config-b');
        await expect(actions).toHaveCount(0);
    });
}

test('评估详情统一百分比与币种；重新评估切换新报告而不刷新已被替换的报告', async ({page}) => {
    let reportId = 'report-old';
    const detailReads: string[] = [];
    const report = () => ({evalReportId: reportId, backtestRunId: 'run-eval', evaluationStatus: 'SUCCEEDED',
        totalReturnRate: -0.0694781798791, totalReturn: -0.0694781798791, annualizedReturn: -0.99986,
        maxDrawdownRate: 0.069973495, winRate: 0.25, initialCapital: 1000, finalEquity: 930.5218, netPnl: -69.4782});
    await page.addInitScript(() => {
        localStorage.setItem('nq.locale', 'zh-CN');
        localStorage.setItem('nexus-quant.console.auth', JSON.stringify({accessToken: 'fixture',
            tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'fixture', roles: ['ADMIN']}));
    });
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        const reply = (json: unknown, status = 200) => route.fulfill({json, status});
        if (path === '/api/auth/me') return reply({userId: 1, authenticated: true, username: 'fixture', roles: ['ADMIN']});
        if (path === '/api/evaluations') return reply([report()]);
        if (path === '/api/backtest-runs/run-eval/evaluate') {reportId = 'report-new'; return reply(report());}
        if (path.startsWith('/api/evaluations/')) {
            detailReads.push(path);
            return reply(path.endsWith(reportId) ? report() : {code: 'RESOURCE_NOT_FOUND'}, path.endsWith(reportId) ? 200 : 404);
        }
        return reply([]);
    });
    await page.goto('/evaluations');
    await page.getByRole('button', {name: /查\s*询/}).click();
    await page.locator('tr').filter({hasText: 'report-old'}).getByRole('button', {name: '查看详情'}).click();
    const drawer = page.locator('.ant-drawer');
    await expect(drawer.getByText('-6.9478%', {exact: true}).first()).toBeVisible();
    await expect(drawer.getByText('6.9973%', {exact: true}).first()).toBeVisible();
    await expect(drawer.getByText('25.0000%', {exact: true}).first()).toBeVisible();
    await expect(drawer).toContainText('USDT');
    await expect.poll(() => detailReads.length).toBe(1);
    await drawer.getByRole('button', {name: '执行评估'}).click();
    await expect(drawer).toContainText('report-new');
    expect(detailReads.filter(path => path.endsWith('report-old'))).toHaveLength(1);
    await expect(drawer.getByText('完整评估详情暂不可用')).toHaveCount(0);
});
