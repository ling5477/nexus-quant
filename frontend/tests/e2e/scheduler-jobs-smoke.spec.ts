import {expect, test, type Page, type Route} from 'playwright/test';
import zhPages from '@/i18n/locales/zh-CN/pages.json' with {type: 'json'};

interface JobRow {
    jobKey: string;
    enabled: boolean;
    fixedDelayMs: number;
    minFixedDelayMs: number;
    maxFixedDelayMs: number;
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
        {
            jobKey: 'PAPER_MATCHING',
            enabled: false,
            fixedDelayMs: 2000,
            minFixedDelayMs: 1000,
            maxFixedDelayMs: 60000,
            nextRunAt: null,
            lastStartedAt: null,
            lastFinishedAt: null,
            lastStatus: 'NEVER_RUN',
            lastErrorCode: null,
            consecutiveFailures: 0,
            version: 0,
            activeRunId: null,
            updatedBy: null,
            updatedAt: now,
        },
        {
            jobKey: 'STRATEGY_RECOVERY',
            enabled: false,
            fixedDelayMs: 5000,
            minFixedDelayMs: 1000,
            maxFixedDelayMs: 86400000,
            nextRunAt: null,
            lastStartedAt: null,
            lastFinishedAt: null,
            lastStatus: 'NEVER_RUN',
            lastErrorCode: null,
            consecutiveFailures: 0,
            version: 0,
            activeRunId: null,
            updatedBy: null,
            updatedAt: now,
        },
    ];
    const writes: Array<{method: string; url: string; body?: Record<string, unknown>}> = [];
    await page.addInitScript((sessionRoles) => {
        window.localStorage.setItem('nq.locale', 'zh-CN');
        window.localStorage.setItem(
            'nexus-quant.console.auth',
            JSON.stringify({
                accessToken: 'synthetic-session',
                tokenType: 'Bearer',
                expiresAt: '2999-01-01T00:00:00Z',
                username: 'scheduler-test',
                roles: sessionRoles,
            }),
        );
    }, roles);
    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) =>
        route.fulfill({status: 404, json: {code: 'NOT_FOUND'}}),
    );
    await page.route('**/api/auth/me', (route: Route) =>
        route.fulfill({
            status: 200,
            json: {
                userId: 1,
                username: 'scheduler-test',
                roles,
                authenticated: true,
                defaultExchangeAccountId: null,
                defaultExchangeCode: null,
                defaultTradeEnv: null,
                defaultAccountAlias: null,
            },
        }),
    );
    // 共享产品壳使用正式分页结构；这些只是边界fixture，不连接真实用户偏好或通知源。
    await page.route('**/api/user/preferences', (route) =>
        route.fulfill({
            status: 200,
            json: {
                version: 0,
                locale: 'zh-CN',
                hideLoginDisclosures: true,
                watchlist: [],
                defaultMarket: null,
                notificationsReadAt: null,
            },
        }),
    );
    await page.route('**/api/user/notifications?*', (route) =>
        route.fulfill({
            status: 200,
            json: {
                items: [],
                offset: 0,
                hasMore: false,
                windowLimited: false,
                checkedAt: now,
                scope: 'OWNED_SIM_RUN_EVENTS',
            },
        }),
    );
    await page.route('**/api/runtime/operational-readiness', (route: Route) =>
        route.fulfill({
            status: 200,
            json: {
                generatedAt: now,
                liveStatus: {status: 'DISABLED'},
                realProviderStatus: {status: 'UNAVAILABLE'},
            },
        }),
    );
    await page.route('**/api/scheduler/jobs', (route: Route) =>
        route.fulfill({status: 200, json: jobs}),
    );
    await page.route(/\/api\/scheduler\/jobs\/[^/]+(?:\/run-once)?$/, async (route: Route) => {
        const request = route.request();
        const jobKey = decodeURIComponent(new URL(request.url()).pathname.split('/')[4]);
        const row = jobs.find((item) => item.jobKey === jobKey);
        if (!row) return route.fulfill({status: 404, json: {code: 'UNKNOWN_SCHEDULER_JOB'}});
        const body =
            request.method() === 'PATCH'
                ? (request.postDataJSON() as Record<string, unknown>)
                : undefined;
        writes.push({method: request.method(), url: request.url(), body});
        if (request.method() === 'PATCH') {
            if (body?.expectedVersion !== row.version)
                return route.fulfill({status: 409, json: {code: 'SCHEDULER_VERSION_CONFLICT'}});
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
    const operations = new Map<string, Record<string, unknown>>();
    await page.route('**/api/scheduler/operations', async (route) => {
        const request = route.request();
        const body = request.postDataJSON() as Record<string, unknown>;
        writes.push({method: request.method(), url: request.url(), body});
        const id = String(body.operationId);
        const original = operations.get(id);
        if (original) return route.fulfill({status: 200, json: original});
        const row = jobs.find((item) => item.jobKey === body.jobKey);
        if (!row) return route.fulfill({status: 404, json: {code: 'UNKNOWN_SCHEDULER_JOB'}});
        if (body.type === 'UPDATE_CONTROL') {
            if (body.expectedVersion !== row.version)
                return route.fulfill({status: 409, json: {code: 'SCHEDULER_VERSION_CONFLICT'}});
            if (body.enabled !== undefined) row.enabled = Boolean(body.enabled);
            if (body.fixedDelayMs !== undefined) row.fixedDelayMs = Number(body.fixedDelayMs);
            row.version++;
            row.nextRunAt = row.enabled ? now : null;
        } else {
            row.lastStatus = 'SUCCESS';
            row.lastStartedAt = now;
            row.lastFinishedAt = now;
        }
        const receipt = {
            operationId: id,
            type: body.type,
            jobKey: row.jobKey,
            status: 'COMPLETED',
            createdAt: now,
            startedAt: body.type === 'RUN_ONCE' ? now : null,
            completedAt: now,
            result: body.type === 'RUN_ONCE' ? 'SUCCESS' : null,
            reasonCode: null,
            control: body.type === 'UPDATE_CONTROL' ? {...row} : null,
        };
        operations.set(id, receipt);
        return route.fulfill({status: 200, json: receipt});
    });
    await page.route('**/api/scheduler/operations/*', (route) => {
        const receipt = operations.get(new URL(route.request().url()).pathname.split('/').at(-1)!);
        return route.fulfill(
            receipt ? {status: 200, json: receipt} : {status: 404, json: {code: 'NOT_FOUND'}},
        );
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
    await expect(paper.getByText(zhPages.schedulerStateEnabled, {exact: true})).toBeVisible();
    expect(fixture.writes[0].body).toMatchObject({
        type: 'UPDATE_CONTROL',
        jobKey: 'PAPER_MATCHING',
        enabled: true,
        expectedVersion: 0,
    });
    expect(fixture.writes[0].body?.operationId).toMatch(/^[a-f0-9-]{36}$/i);

    await paper.getByRole('button', {name: '修改间隔'}).click();
    await page.getByRole('spinbutton', {name: '固定间隔'}).fill('3000');
    await page.getByRole('button', {name: /保\s*存/}).click();
    await expect(paper.getByText('3,000 ms')).toBeVisible();
    expect(fixture.writes[1].body).toMatchObject({
        type: 'UPDATE_CONTROL',
        jobKey: 'PAPER_MATCHING',
        fixedDelayMs: 3000,
        expectedVersion: 1,
    });

    await paper.getByRole('button', {name: '运行一次'}).click();
    await page.getByRole('button', {name: /确\s*认/}).click();
    // 用户可见结果遵循本地化合同；原始状态仍需通过键盘展开验证，不能只验证隐藏文本存在。
    await expect(paper.getByText(zhPages.schedulerStatus_SUCCESS, {exact: true})).toBeVisible();
    const rawStatus = paper.getByText('SUCCESS', {exact: true});
    await expect(rawStatus).not.toBeVisible();
    await paper.getByText(zhPages.schedulerTechnicalStatus, {exact: true}).press('Enter');
    await expect(rawStatus).toBeVisible();
    expect(fixture.writes[2].method).toBe('POST');

    await page.setViewportSize({width: 390, height: 844});
    await expect(page.getByRole('heading', {name: '调度任务'})).toBeVisible();
    await expect(page.getByText('启用后台任务不等于 LIVE 或交易授权')).toBeVisible();
    await expect(paper).toBeVisible();
});

test('持续SIM间隔服从正式30000ms下限，范围外草稿不能保存', async ({page}) => {
    const fixture = await setup(page, ['ADMIN']);
    fixture.jobs.push({
        ...fixture.jobs[0],
        jobKey: 'CONTINUOUS_SIM_POLL',
        fixedDelayMs: 300000,
        minFixedDelayMs: 30000,
        maxFixedDelayMs: 86400000,
    });
    await page.goto('/system/scheduler');
    const row = page.getByRole('row').filter({hasText: 'CONTINUOUS_SIM_POLL'});
    await row.getByRole('button', {name: '修改间隔', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: '修改间隔', exact: true});
    const input = dialog.getByRole('spinbutton', {name: '固定间隔', exact: true});
    await expect(input).toHaveAttribute('aria-valuemin', '30000');
    await expect(input).toHaveAttribute('aria-valuemax', '86400000');
    await input.fill('29999');
    await expect(dialog.getByRole('button', {name: /保\s*存/})).toBeDisabled();
    await expect(dialog).toContainText('30,000');
    expect(fixture.writes).toHaveLength(0);
    await input.fill('30000');
    await dialog.getByRole('button', {name: /保\s*存/}).click();
    await expect(dialog).not.toBeVisible();
    expect(fixture.writes[0].body).toMatchObject({
        type: 'UPDATE_CONTROL',
        jobKey: 'CONTINUOUS_SIM_POLL',
        fixedDelayMs: 30000,
        expectedVersion: 0,
    });
});

test('只读角色能看状态但不能写；列表错误不伪装为空任务', async ({page}) => {
    const fixture = await setup(page, ['OPERATOR']);
    await page.goto('/system/scheduler');
    await expect(page.getByRole('row').filter({hasText: 'PAPER_MATCHING'})).toBeVisible();
    await expect(page.getByText('只读权限')).toHaveCount(2);
    await expect(page.getByRole('button', {name: /开\s*启/})).toHaveCount(0);
    expect(fixture.writes).toHaveLength(0);

    await page.route('**/api/scheduler/jobs', (route: Route) =>
        route.fulfill({status: 503, json: {code: 'SCHEDULER_UNAVAILABLE'}}),
    );
    await page.getByRole('button', {name: /刷\s*新/}).click();
    await expect(page.getByText('调度任务加载失败')).toBeVisible();
    await expect(page.getByRole('row').filter({hasText: 'PAPER_MATCHING'})).toBeVisible();

    // 读取失败保留旧业务行；显式重试只重新读取列表，不能生成新的调度操作。
    let recoveryReads = 0;
    await page.route('**/api/scheduler/jobs', (route: Route) => {
        recoveryReads++;
        return route.fulfill({status: 200, json: fixture.jobs});
    });
    await page.getByRole('button', {name: /^重\s*试$/}).click();
    await expect(page.getByText('调度任务加载失败')).not.toBeVisible();
    await expect(page.getByRole('row').filter({hasText: 'PAPER_MATCHING'})).toBeVisible();
    expect(recoveryReads).toBeGreaterThan(0);
    expect(fixture.writes).toHaveLength(0);
});
