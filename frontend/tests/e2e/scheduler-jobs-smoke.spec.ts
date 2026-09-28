import {expect, test, type Page, type Route} from 'playwright/test';

interface JobRow {
    jobKey: string;
    enabled: boolean;
    fixedDelayMs: number;
    nextRunAt: string | null;
    lastStartedAt: string | null;
    lastFinishedAt: string | null;
    lastStatus: string;
    lastErrorCode: string | null;
    consecutiveFailures: number;
    version: number;
    activeRunId: string | null;
    updatedBy: string | null;
    updatedAt: string;
}

const now = '2026-09-29T00:00:00Z';

async function setup(page: Page, roles: string[]) {
    const jobs: JobRow[] = [
        {jobKey: 'PAPER_MATCHING', enabled: false, fixedDelayMs: 2000,
            nextRunAt: null, lastStartedAt: null, lastFinishedAt: null,
            lastStatus: 'NEVER_RUN', lastErrorCode: null, consecutiveFailures: 0,
            version: 0, activeRunId: null, updatedBy: null, updatedAt: now},
        {jobKey: 'STRATEGY_RECOVERY', enabled: false, fixedDelayMs: 5000,
            nextRunAt: null, lastStartedAt: null, lastFinishedAt: null,
            lastStatus: 'NEVER_RUN', lastErrorCode: null, consecutiveFailures: 0,
            version: 0, activeRunId: null, updatedBy: null, updatedAt: now},
    ];
    const writes: Array<{method: string; url: string; body?: Record<string, unknown>}> = [];
    await page.addInitScript((sessionRoles) => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'synthetic-session', tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z', username: 'scheduler-test', roles: sessionRoles,
        }));
    }, roles);
    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) =>
        route.fulfill({status: 404, json: {code: 'NOT_FOUND'}}));
    await page.route('**/api/auth/me', (route: Route) => route.fulfill({status: 200, json: {
        userId: 1, username: 'scheduler-test', roles, authenticated: true,
        defaultExchangeAccountId: null, defaultExchangeCode: null,
        defaultTradeEnv: null, defaultAccountAlias: null,
    }}));
    await page.route('**/api/runtime/operational-readiness', (route: Route) =>
        route.fulfill({status: 200, json: {
            generatedAt: now, liveStatus: {status: 'DISABLED'},
            realProviderStatus: {status: 'UNAVAILABLE'},
        }}));
    await page.route('**/api/scheduler/jobs', (route: Route) =>
        route.fulfill({status: 200, json: jobs}));
    await page.route(/\/api\/scheduler\/jobs\/[^/]+(?:\/run-once)?$/, async (route: Route) => {
        const request = route.request();
        const jobKey = decodeURIComponent(new URL(request.url()).pathname.split('/')[4]);
        const row = jobs.find((item) => item.jobKey === jobKey);
        if (!row) return route.fulfill({status: 404, json: {code: 'UNKNOWN_SCHEDULER_JOB'}});
        const body = request.method() === 'PATCH' ? request.postDataJSON() as Record<string, unknown> : undefined;
        writes.push({method: request.method(), url: request.url(), body});
        if (request.method() === 'PATCH') {
            if (body?.expectedVersion !== row.version) return route.fulfill({status: 409, json: {code: 'SCHEDULER_VERSION_CONFLICT'}});
            if (body.enabled !== undefined) row.enabled = Boolean(body.enabled);
            if (body.fixedDelayMs !== undefined) row.fixedDelayMs = Number(body.fixedDelayMs);
            row.version++;
            row.nextRunAt = row.enabled ? now : null;
            return route.fulfill({status: 200, json: row});
        }
        if (request.method() === 'POST') {
            row.lastStatus = 'SUCCESS';
            row.lastStartedAt = now;
            row.lastFinishedAt = now;
            return route.fulfill({status: 200, json: {jobKey, result: 'SUCCESS'}});
        }
        return route.fulfill({status: 405});
    });
    return {jobs, writes};
}

test('ADMIN 可以确认启停、修改间隔和手动运行，恢复任务保持静态禁用', async ({page}) => {
    const fixture = await setup(page, ['ADMIN']);
    await page.goto('/system/scheduler');
    await expect(page.getByRole('heading', {name: '调度任务'})).toBeVisible();
    await expect(page.getByText('启用后台任务不等于 LIVE 或交易授权')).toBeVisible();
    await expect(page.getByText('此接口未提供当前事实')).toBeVisible();
    const paper = page.getByRole('row').filter({hasText: 'PAPER_MATCHING'});
    const recovery = page.getByRole('row').filter({hasText: 'STRATEGY_RECOVERY'});
    await expect(recovery.getByRole('button', {name: /开\s*启/})).toBeDisabled();
    await expect(recovery.getByRole('button', {name: '运行一次'})).toBeDisabled();

    await paper.getByRole('button', {name: /开\s*启/}).click();
    await page.getByRole('button', {name: /确\s*认/}).click();
    await expect(paper.getByText('ON', {exact: true})).toBeVisible();
    expect(fixture.writes[0].body).toEqual({enabled: true, expectedVersion: 0});

    await paper.getByRole('button', {name: '修改间隔'}).click();
    await page.getByRole('spinbutton', {name: '固定间隔'}).fill('3000');
    await page.getByRole('button', {name: /保\s*存/}).click();
    await expect(paper.getByText('3,000 ms')).toBeVisible();
    expect(fixture.writes[1].body).toEqual({fixedDelayMs: 3000, expectedVersion: 1});

    await paper.getByRole('button', {name: '运行一次'}).click();
    await page.getByRole('button', {name: /确\s*认/}).click();
    await expect(paper.getByText('SUCCESS')).toBeVisible();
    expect(fixture.writes[2].method).toBe('POST');

    await page.setViewportSize({width: 390, height: 844});
    await expect(page.getByRole('heading', {name: '调度任务'})).toBeVisible();
    await expect(page.getByText('启用后台任务不等于 LIVE 或交易授权')).toBeVisible();
    await expect(paper).toBeVisible();
});

test('只读角色能看状态但不能写；列表错误不伪装为空任务', async ({page}) => {
    const fixture = await setup(page, ['OPERATOR']);
    await page.goto('/system/scheduler');
    await expect(page.getByRole('row').filter({hasText: 'PAPER_MATCHING'})).toBeVisible();
    await expect(page.getByText('只读权限')).toHaveCount(2);
    await expect(page.getByRole('button', {name: /开\s*启/})).toHaveCount(0);
    expect(fixture.writes).toHaveLength(0);

    await page.route('**/api/scheduler/jobs', (route: Route) =>
        route.fulfill({status: 503, json: {code: 'SCHEDULER_UNAVAILABLE'}}));
    await page.getByRole('button', {name: /刷\s*新/}).click();
    await expect(page.getByText('调度任务加载失败')).toBeVisible();
    await expect(page.getByRole('row').filter({hasText: 'PAPER_MATCHING'})).toBeVisible();
});
