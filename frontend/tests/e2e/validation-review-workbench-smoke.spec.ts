import {expect, test, type Page, type Route} from 'playwright/test';

const CASE_ID = '11111111-1111-4111-8111-111111111111';
const SECOND_CASE_ID = '22222222-2222-4222-8222-222222222222';

interface ReviewStubOptions {
    roles?: string[];
    listMode?: 'data' | 'empty' | 'error' | 'forbidden' | 'delayed';
    detailNotFound?: boolean;
    actionStatus?: number;
    actionDelayMs?: number;
    stateOverride?: string;
    uuidFailure?: boolean;
}

interface ReviewRuntimeAudit {
    requests: Array<{method: string; url: string; headers: Record<string, string>; body: unknown}>;
    pageErrors: string[];
    consoleErrors: string[];
}

function reviewCase(id = CASE_ID, state = 'OPEN', version = 0) {
    return {
        id,
        ownerId: 7,
        evidenceType: 'RUNTIME_EVIDENCE',
        evidenceSource: `local-source-${id.slice(0, 8)}`,
        severity: id === CASE_ID ? 'CRITICAL' : 'WARNING',
        state,
        title: '本地证据需要人工复核',
        summary: '仅包含脱敏诊断摘要，不包含原始 payload。',
        version,
        createdAt: '2026-07-12T01:00:00Z',
        updatedAt: '2026-07-12T02:00:00Z',
        acknowledgedBy: state === 'OPEN' ? null : 7,
        acknowledgedAt: state === 'OPEN' ? null : '2026-07-12T02:00:00Z',
        escalatedBy: null,
        escalatedAt: null,
        resolvedBy: null,
        resolvedAt: null,
        closedBy: null,
        closedAt: null,
        retentionUntil: '2027-01-08T02:00:00Z',
        diagnosticOnly: true,
        noSideEffect: true,
        notTradingAuthorization: true,
        liveDisabled: true,
    };
}

/**
 * GateV-4 smoke 使用浏览器 route mock，不连接真实 backend。
 * 捕获全部请求以证明新增写侧仅命中 validation review endpoint，且不请求交易/凭证能力。
 */
async function seedReviewWorkbench(page: Page, options: ReviewStubOptions = {}) {
    const roles = options.roles ?? ['ADMIN'];
    const requests: Array<{method: string; url: string; headers: Record<string, string>; body: unknown}> = [];
    const pageErrors: string[] = [];
    const consoleErrors: string[] = [];
    let currentCase = reviewCase(CASE_ID, options.stateOverride ?? 'OPEN');
    let events = [{
        id: 'event-1',
        caseId: CASE_ID,
        eventType: 'CREATED',
        fromState: 'OPEN',
        toState: 'OPEN',
        caseVersion: 0,
        actorId: 7,
        createdAt: '2026-07-12T01:00:00Z',
    }];

    await page.addInitScript(({authRoles, uuidFailure}) => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'gatev4-review-smoke-session',
            tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z',
            username: 'gatev4-reviewer',
            roles: authRoles,
        }));
        if (uuidFailure) {
            Object.defineProperty(window.crypto, 'randomUUID', {
                configurable: true,
                value: () => { throw new Error('fixture UUID failure'); },
            });
        }
    }, {authRoles: roles, uuidFailure: options.uuidFailure ?? false});

    page.on('request', (request) => requests.push({
        method: request.method(),
        url: request.url(),
        headers: request.headers(),
        body: request.postDataJSON() ?? null,
    }));
    page.on('pageerror', (error) => pageErrors.push(error.message));
    page.on('console', (entry) => {
        if (entry.type() !== 'error') {
            return;
        }

        const message = entry.text();
        const isKnownReactCompatibilityWarning = /antd v5 support React is 16 ~ 18/i.test(message);
        // Why: 这些状态码是本 spec 主动 mock 的失败契约；Chromium 会额外记录资源加载错误，
        // 但仍应让其他 console error 和全部 pageerror 触发失败，避免掩盖真实运行时异常。
        const isExpectedMockedHttpFailure = /Failed to load resource.*status of (403|404|409|422|500|503)\b/i.test(message);
        if (!isKnownReactCompatibilityWarning && !isExpectedMockedHttpFailure) {
            consoleErrors.push(message);
        }
    });

    // Why: 非 GateV-4 既有 read-only query 返回明确 unavailable，避免用结构错误的空数组伪造 DTO。
    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) => route.fulfill({status: 503, json: {code: 'FIXTURE_UNAVAILABLE'}}));
    await page.route('**/api/auth/me', (route: Route) => route.fulfill({
        status: 200,
        json: {
            userId: 7,
            username: 'gatev4-reviewer',
            roles,
            authenticated: true,
            defaultExchangeAccountId: null,
            defaultExchangeCode: null,
            defaultTradeEnv: null,
            defaultAccountAlias: null,
        },
    }));

    await page.route('**/api/validation-review-cases**', async (route: Route) => {
        const request = route.request();
        const url = new URL(request.url());
        const segments = url.pathname.split('/').filter(Boolean);
        const caseId = segments[2];
        const suffix = segments[3];

        if (request.method() === 'GET' && segments.length === 2) {
            if (options.listMode === 'delayed') await new Promise((resolve) => setTimeout(resolve, 350));
            if (options.listMode === 'forbidden') return route.fulfill({status: 403, json: {code: 'REVIEW_ACTION_FORBIDDEN'}});
            if (options.listMode === 'error') return route.fulfill({status: 500, json: {code: 'SERVER_ERROR'}});
            if (options.listMode === 'empty') return route.fulfill({status: 200, json: []});
            const offset = Number(url.searchParams.get('offset') ?? 0);
            const state = url.searchParams.get('state');
            const severity = url.searchParams.get('severity');
            const ownerId = url.searchParams.get('ownerId');
            let list = offset === 0
                ? Array.from({length: 20}, (_, index) => reviewCase(index === 0 ? CASE_ID : `${String(index + 10).padStart(8, '0')}-1111-4111-8111-111111111111`))
                : [reviewCase(SECOND_CASE_ID)];
            if (state) list = list.filter((item) => item.state === state);
            if (severity) list = list.filter((item) => item.severity === severity);
            if (ownerId) list = list.filter((item) => String(item.ownerId) === ownerId);
            return route.fulfill({status: 200, json: list});
        }

        if (request.method() === 'GET' && segments.length === 4 && suffix === 'events') {
            if (options.detailNotFound) return route.fulfill({status: 404, json: {code: 'REVIEW_CASE_NOT_FOUND'}});
            return route.fulfill({status: 200, json: events});
        }

        if (request.method() === 'GET' && segments.length === 3 && caseId) {
            if (options.detailNotFound) return route.fulfill({status: 404, json: {code: 'REVIEW_CASE_NOT_FOUND'}});
            return route.fulfill({status: 200, json: currentCase});
        }

        const allowedActions = new Set(['acknowledge', 'escalate', 'resolve', 'close']);
        if (request.method() === 'POST' && segments.length === 4 && caseId && suffix && allowedActions.has(suffix)) {
            if (options.actionDelayMs) await new Promise((resolve) => setTimeout(resolve, options.actionDelayMs));
            if (options.actionStatus && options.actionStatus !== 200) {
                const code = options.actionStatus === 403 ? 'REVIEW_ACTION_FORBIDDEN' : 'REVIEW_CASE_VERSION_CONFLICT';
                return route.fulfill({status: options.actionStatus, json: {code}});
            }
            const nextState = suffix === 'acknowledge' ? 'ACKNOWLEDGED'
                : suffix === 'escalate' ? 'ESCALATED'
                    : suffix === 'resolve' ? 'RESOLVED' : 'CLOSED';
            currentCase = {...currentCase, state: nextState, version: currentCase.version + 1};
            events = [...events, {
                id: `event-${events.length + 1}`,
                caseId,
                eventType: nextState,
                fromState: 'OPEN',
                toState: nextState,
                caseVersion: currentCase.version,
                actorId: 7,
                createdAt: '2026-07-12T02:05:00Z',
            }];
            return route.fulfill({status: 200, json: currentCase});
        }

        return route.fulfill({status: 404, json: {code: 'UNEXPECTED_REVIEW_ENDPOINT'}});
    });

    return {requests, pageErrors, consoleErrors} satisfies ReviewRuntimeAudit;
}

function reviewRequestCount(requests: ReviewRuntimeAudit['requests'], method: string, suffix: string): number {
    return requests.filter((item) => item.method === method && new URL(item.url).pathname.endsWith(suffix)).length;
}

function expectOnlyAllowedReviewRequests(requests: ReviewRuntimeAudit['requests']): void {
    const base = '/api/validation-review-cases';
    const detailPattern = new RegExp(`^${base}/[0-9a-f-]{36}$`, 'i');
    const eventsPattern = new RegExp(`^${base}/[0-9a-f-]{36}/events$`, 'i');
    const actionPattern = new RegExp(`^${base}/[0-9a-f-]{36}/(acknowledge|escalate|resolve|close)$`, 'i');
    for (const request of requests.filter((item) => new URL(item.url).pathname.startsWith(base))) {
        const path = new URL(request.url).pathname;
        const allowed = (request.method === 'GET' && (path === base || detailPattern.test(path) || eventsPattern.test(path)))
            || (request.method === 'POST' && actionPattern.test(path));
        expect(allowed, `unexpected validation review request: ${request.method} ${path}`).toBeTruthy();
    }
}

function expectNoUnexpectedRuntimeErrors(audit: ReviewRuntimeAudit): void {
    expect(audit.pageErrors).toEqual([]);
    expect(audit.consoleErrors).toEqual([]);
}

test.describe('GateV-4 validation review workbench', () => {
    test('ADMIN queue、筛选、翻页、URL detail、events 与安全边界完整', async ({page}) => {
        const audit = await seedReviewWorkbench(page);
        const {requests} = audit;
        await page.goto('/strategies/validation');

        const workbench = page.getByTestId('validation-review-workbench');
        await expect(workbench).toContainText('Validation Review Workbench');
        await expect(workbench).toContainText('诊断审查流程，不构成交易授权，也不会启动 LIVE 或 Shadow trading。');
        await expect(page.getByLabel('Owner ID')).toBeVisible();
        await expect(page.getByTestId('validation-review-queue').locator('tbody tr.ant-table-row')).toHaveCount(20);
        await page.getByRole('button', {name: '下一页'}).click();
        await expect.poll(() => requests.some((item) => item.url.includes('offset=20'))).toBeTruthy();
        await page.getByRole('button', {name: '上一页'}).click();

        await page.getByRole('combobox', {name: 'Review state'}).click();
        await page.getByTitle('OPEN').click();
        await expect.poll(() => requests.some((item) => item.url.includes('state=OPEN'))).toBeTruthy();
        await page.getByRole('combobox', {name: 'Review severity'}).click();
        await page.getByTitle('CRITICAL').click();
        await expect.poll(() => requests.some((item) => item.url.includes('severity=CRITICAL'))).toBeTruthy();
        await page.getByLabel('Owner ID').fill('7');
        await expect.poll(() => requests.some((item) => item.url.includes('ownerId=7'))).toBeTruthy();

        await page.getByTestId('validation-review-queue').getByText(CASE_ID).click();
        await expect(page).toHaveURL(new RegExp(`reviewCaseId=${CASE_ID}`));
        const drawer = page.getByTestId('validation-review-case-drawer');
        await expect(drawer).toContainText('后端安全 DTO 未公开 trace/schema/checksum/evidence anchor');
        await expect(drawer).toContainText('CREATED');
        const actionArea = page.getByTestId('validation-review-actions');
        await expect(actionArea.getByText(/create|delete|reopen|approve|authorize|trade/i)).toHaveCount(0);
        await page.goBack();
        await expect(page).not.toHaveURL(/reviewCaseId=/);
        await expect(page.getByTestId('validation-review-case-drawer')).not.toBeVisible();
        await page.goForward();
        await expect(page.getByTestId('validation-review-case-drawer')).toContainText(CASE_ID);
        await page.reload();
        await expect(page.getByTestId('validation-review-case-drawer')).toContainText(CASE_ID);

        await page.getByTestId('validation-review-case-drawer').getByRole('button', {name: 'Close'}).click();
        const forbiddenEndpoint = /\/api\/(orders?|accounts?|ledger|exchange|credentials?|live|shadow)(\/|\?|$)/i;
        expect(requests.filter((item) => item.method === 'POST').every((item) => item.url.includes('/api/validation-review-cases/'))).toBeTruthy();
        expect(requests.some((item) => forbiddenEndpoint.test(new URL(item.url).pathname))).toBeFalsy();
        expectOnlyAllowedReviewRequests(requests);
        expectNoUnexpectedRuntimeErrors(audit);
    });

    test('合法 mutation 使用真实 body/header，pending 防重复并刷新三类 query', async ({page}) => {
        const audit = await seedReviewWorkbench(page, {actionDelayMs: 300});
        const {requests} = audit;
        await page.goto(`/strategies/validation?reviewCaseId=${CASE_ID}`);
        await expect(page.getByTestId('validation-review-case-drawer')).toContainText(CASE_ID);
        const beforeList = reviewRequestCount(requests, 'GET', '/api/validation-review-cases');
        const beforeDetail = reviewRequestCount(requests, 'GET', `/api/validation-review-cases/${CASE_ID}`);
        const beforeEvents = reviewRequestCount(requests, 'GET', `/api/validation-review-cases/${CASE_ID}/events`);
        await page.getByRole('button', {name: '确认已阅'}).click();
        await page.getByLabel('复核原因').fill('已核对本地脱敏诊断证据');
        const confirm = page.getByRole('button', {name: '确认提交'});
        await confirm.click();
        await expect(confirm).toBeDisabled();
        await expect(page.getByTestId('validation-review-case-drawer')).toContainText('ACKNOWLEDGED');

        const post = requests.find((item) => item.method === 'POST');
        expect(post?.url).toContain(`/api/validation-review-cases/${CASE_ID}/acknowledge`);
        expect(post?.headers['idempotency-key']).toBeTruthy();
        expect(post?.headers['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/i);
        expect(post?.body).toEqual({expectedVersion: 0, reason: '已核对本地脱敏诊断证据'});
        expect(requests.filter((item) => item.method === 'POST')).toHaveLength(1);
        await expect.poll(() => reviewRequestCount(requests, 'GET', '/api/validation-review-cases')).toBeGreaterThan(beforeList);
        await expect.poll(() => reviewRequestCount(requests, 'GET', `/api/validation-review-cases/${CASE_ID}`)).toBeGreaterThan(beforeDetail);
        await expect.poll(() => reviewRequestCount(requests, 'GET', `/api/validation-review-cases/${CASE_ID}/events`)).toBeGreaterThan(beforeEvents);
        expectOnlyAllowedReviewRequests(requests);
        expectNoUnexpectedRuntimeErrors(audit);
    });

    test('409 冲突保守提示并刷新 detail/events，403 后禁用动作', async ({page}) => {
        const conflictAudit = await seedReviewWorkbench(page, {actionStatus: 409});
        const conflictRequests = conflictAudit.requests;
        await page.goto(`/strategies/validation?reviewCaseId=${CASE_ID}`);
        const beforeList = reviewRequestCount(conflictRequests, 'GET', '/api/validation-review-cases');
        const beforeDetail = reviewRequestCount(conflictRequests, 'GET', `/api/validation-review-cases/${CASE_ID}`);
        const beforeEvents = reviewRequestCount(conflictRequests, 'GET', `/api/validation-review-cases/${CASE_ID}/events`);
        await page.getByRole('button', {name: '确认已阅'}).click();
        await page.getByLabel('复核原因').fill('冲突回归');
        await page.getByRole('button', {name: '确认提交'}).click();
        await expect(page.getByText('Case 状态已变化或流转不再合法，已重新获取最新详情。')).toBeVisible();
        await expect.poll(() => reviewRequestCount(conflictRequests, 'GET', '/api/validation-review-cases')).toBeGreaterThan(beforeList);
        await expect.poll(() => reviewRequestCount(conflictRequests, 'GET', `/api/validation-review-cases/${CASE_ID}`)).toBeGreaterThan(beforeDetail);
        await expect.poll(() => reviewRequestCount(conflictRequests, 'GET', `/api/validation-review-cases/${CASE_ID}/events`)).toBeGreaterThan(beforeEvents);

        await page.unrouteAll({behavior: 'wait'});
        const invalidTransitionAudit = await seedReviewWorkbench(page, {actionStatus: 422});
        await page.reload();
        await page.getByRole('button', {name: '确认已阅'}).click();
        await page.getByLabel('复核原因').fill('非法流转回归');
        await page.getByRole('button', {name: '确认提交'}).click();
        await expect(page.getByText('Case 状态已变化或流转不再合法，已重新获取最新详情。')).toBeVisible();

        await page.unrouteAll({behavior: 'wait'});
        const forbiddenAudit = await seedReviewWorkbench(page, {actionStatus: 403});
        await page.reload();
        await page.getByRole('button', {name: '确认已阅'}).click();
        await page.getByLabel('复核原因').fill('权限回归');
        await page.getByRole('button', {name: '确认提交'}).click();
        await expect(page.getByText('当前身份无权执行该复核动作。')).toBeVisible();
        await expect(page.getByRole('button', {name: '确认已阅'})).toBeDisabled();
        expectOnlyAllowedReviewRequests(conflictRequests);
        expectOnlyAllowedReviewRequests(invalidTransitionAudit.requests);
        expectOnlyAllowedReviewRequests(forbiddenAudit.requests);
        expectNoUnexpectedRuntimeErrors(conflictAudit);
        expectNoUnexpectedRuntimeErrors(invalidTransitionAudit);
        expectNoUnexpectedRuntimeErrors(forbiddenAudit);
    });

    test('loading、empty、API error、permission denied、404 与 OPERATOR owner 边界', async ({page}) => {
        const loadingAudit = await seedReviewWorkbench(page, {roles: ['OPERATOR'], listMode: 'delayed'});
        await page.goto('/strategies/validation');
        await expect(page.getByTestId('validation-review-queue').locator('.ant-spin-spinning')).toBeVisible();
        await expect(page.getByLabel('Owner ID')).toHaveCount(0);

        await page.unrouteAll({behavior: 'wait'});
        const emptyAudit = await seedReviewWorkbench(page, {roles: ['OPERATOR'], listMode: 'empty'});
        await page.reload();
        await expect(page.getByText('当前筛选条件下没有 review case。')).toBeVisible();

        await page.unrouteAll({behavior: 'wait'});
        const errorAudit = await seedReviewWorkbench(page, {listMode: 'error'});
        await page.reload();
        await expect(page.getByText('Review queue 加载失败')).toBeVisible();

        await page.unrouteAll({behavior: 'wait'});
        const permissionAudit = await seedReviewWorkbench(page, {listMode: 'forbidden'});
        await page.reload();
        await expect(page.getByText('无权访问 review queue')).toBeVisible();

        await page.unrouteAll({behavior: 'wait'});
        const notFoundAudit = await seedReviewWorkbench(page, {detailNotFound: true});
        await page.goto(`/strategies/validation?reviewCaseId=${SECOND_CASE_ID}`);
        await expect(page.getByText('Case 已不存在')).toBeVisible();

        await page.unrouteAll({behavior: 'wait'});
        const invalidUrlAudit = await seedReviewWorkbench(page);
        await page.goto('/strategies/validation?reviewCaseId=not-a-uuid');
        await expect(page.getByText('reviewCaseId 无效')).toBeVisible();
        expect(invalidUrlAudit.requests.some((item) => {
            const path = new URL(item.url).pathname;
            return path.startsWith('/api/validation-review-cases') && item.url.includes('not-a-uuid');
        })).toBeFalsy();

        await page.unrouteAll({behavior: 'wait'});
        const unknownStateAudit = await seedReviewWorkbench(page, {stateOverride: 'FUTURE_STATE'});
        await page.goto(`/strategies/validation?reviewCaseId=${CASE_ID}`);
        await expect(page.getByText('当前状态没有可执行动作。')).toBeVisible();

        await page.unrouteAll({behavior: 'wait'});
        const uuidFailureAudit = await seedReviewWorkbench(page, {uuidFailure: true});
        await page.reload();
        await page.getByRole('button', {name: '确认已阅'}).click();
        await page.getByLabel('复核原因').fill('UUID fail-closed 回归');
        await page.getByRole('button', {name: '确认提交'}).click();
        await expect(page.getByText('无法生成安全的 Idempotency-Key；本次请求未发送。')).toBeVisible();
        expect(uuidFailureAudit.requests.filter((item) => item.method === 'POST')).toHaveLength(0);

        for (const audit of [loadingAudit, emptyAudit, errorAudit, permissionAudit, notFoundAudit, invalidUrlAudit, unknownStateAudit, uuidFailureAudit]) {
            expectOnlyAllowedReviewRequests(audit.requests);
            expectNoUnexpectedRuntimeErrors(audit);
        }
    });
});
