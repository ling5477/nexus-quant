import {expect, test, type Page} from 'playwright/test';

/**
 * GateM-6F Operational Readiness final real-backend smoke.
 *
 * Why:
 * GateM-6D proved the real local backend API/UI loop. This final smoke keeps the same narrow
 * runbook path in place for GateM-6 close validation: authenticate locally, read the real
 * `GET /api/runtime/operational-readiness`, open `/runtime/readiness`, and prove the UI still
 * presents the safe fail-closed summary without promoting actuator health, skipped probes,
 * Paper-only, or no-real signals to LIVE authorization.
 *
 * Boundaries:
 * - Only the real read-only operational readiness GET is verified.
 * - No permission-probe POST, ingestion run-once, order, cancel, transfer, or withdraw endpoint.
 * - No external exchange host, LIVE enablement, AI runtime, DH runtime, RealClient, or real provider.
 * - Authentication uses the existing local login endpoint only; it does not create or mutate
 *   exchange accounts.
 */

interface LoginSession {
    accessToken: string;
    tokenType: string;
    expiresAt: string;
    username: string;
    roles: string[];
}

interface OperationalStatus {
    status: string;
    ready: boolean;
    reasonCode: string;
    reason: string;
}

interface OperationalReadinessResponse {
    generatedAt: string;
    liveStatus: OperationalStatus;
    aiStatus: OperationalStatus;
    dhRuntimeStatus: OperationalStatus;
    realProviderStatus: OperationalStatus;
    credentialExposureStatus: OperationalStatus;
    externalExchangeCallStatus: OperationalStatus;
    permissionProbeStatus: OperationalStatus;
    startupBoundaryStatus: OperationalStatus;
    profileBoundaryStatus: OperationalStatus;
    configDiagnosticsStatus: OperationalStatus;
    logDiagnosticsStatus: OperationalStatus;
}

interface RuntimeBoundaryRequests {
    apiRequests: string[];
    forbiddenWriteCalls: string[];
    externalExchangeRequests: string[];
}

const username = process.env.E2E_USERNAME ?? 'admin';
const password = process.env.E2E_PASSWORD ?? 'ChangeMe123!';
const SECRET_TOKENS = ['apiKey', 'api_key', 'secret', 'token', 'signature', 'passphrase', 'private key', 'mnemonic', 'cookie'];
const OPERATIONAL_FIELDS: Array<keyof Omit<OperationalReadinessResponse, 'generatedAt'>> = [
    'liveStatus',
    'aiStatus',
    'dhRuntimeStatus',
    'realProviderStatus',
    'credentialExposureStatus',
    'externalExchangeCallStatus',
    'permissionProbeStatus',
    'startupBoundaryStatus',
    'profileBoundaryStatus',
    'configDiagnosticsStatus',
    'logDiagnosticsStatus',
];

function trackRuntimeBoundaryRequests(page: Page): RuntimeBoundaryRequests {
    const requests: RuntimeBoundaryRequests = {
        apiRequests: [],
        forbiddenWriteCalls: [],
        externalExchangeRequests: [],
    };

    page.on('request', (request) => {
        const parsedUrl = new URL(request.url());
        const method = request.method().toUpperCase();
        const path = parsedUrl.pathname.toLowerCase();
        const entry = `${method} ${request.url()}`;

        if (/okx|binance|bybit|gate|coinbase|kraken/i.test(parsedUrl.hostname)) {
            requests.externalExchangeRequests.push(entry);
        }
        if (!path.startsWith('/api/')) {
            return;
        }

        requests.apiRequests.push(entry);
        if (method === 'GET') {
            return;
        }

        const isForbiddenWrite = path.includes('permission-probe')
            || path.includes('ingestions/run-once')
            || path.includes('ingestion-jobs/run-once')
            || /(^|\/)(orders?|cancel|transfer|withdraw)(\/|$)/i.test(path);

        if (isForbiddenWrite) {
            requests.forbiddenWriteCalls.push(entry);
        }
    });

    return requests;
}

async function loginWithoutAccountFixture(page: Page): Promise<LoginSession> {
    const response = await page.request.post('/api/auth/login', {
        data: {username, password},
        timeout: 30_000,
    });
    expect(response.ok(), await response.text()).toBeTruthy();

    const session = await response.json() as LoginSession;
    expect(session.accessToken, 'final smoke requires an accessToken from /api/auth/login').toBeTruthy();
    expect(session.tokenType, 'final smoke requires tokenType from /api/auth/login').toBeTruthy();
    expect(session.expiresAt, 'final smoke requires expiresAt from /api/auth/login').toBeTruthy();
    expect(session.username, 'final smoke requires username from /api/auth/login').toBeTruthy();
    expect(Array.isArray(session.roles), 'final smoke requires roles from /api/auth/login').toBeTruthy();

    await page.addInitScript((payload: LoginSession) => {
        window.localStorage.setItem('nq.locale', 'en-US');
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify(payload));
    }, session);

    return session;
}

async function getOperationalReadiness(page: Page, session: LoginSession): Promise<OperationalReadinessResponse> {
    const response = await page.request.get('/api/runtime/operational-readiness', {
        headers: {
            Authorization: `${session.tokenType} ${session.accessToken}`,
        },
        timeout: 30_000,
    });
    expect(response.status(), await response.text()).toBe(200);

    const payload = await response.json() as OperationalReadinessResponse;
    expect(payload.generatedAt, 'operational readiness summary must include generatedAt').toBeTruthy();
    return payload;
}

function expectStatus(payload: OperationalReadinessResponse, field: keyof Omit<OperationalReadinessResponse, 'generatedAt'>, status: string) {
    const item = payload[field];
    expect(item.status, `${field} status`).toBe(status);
    expect(item.ready, `${field} must remain fail-closed`).toBe(false);
    expect(item.reasonCode, `${field} reasonCode must be present`).toBeTruthy();
    expect(item.reason, `${field} reason must be present`).toBeTruthy();
}

function expectEveryStatusBlocked(payload: OperationalReadinessResponse) {
    for (const field of OPERATIONAL_FIELDS) {
        expect(payload[field].ready, `${field} must not report ready=true`).toBe(false);
    }
}

function expectNoCredentialLikeText(text: string, context: string) {
    const lowerText = text.toLowerCase();
    for (const token of SECRET_TOKENS) {
        expect(lowerText.includes(token.toLowerCase()), `${context} must not expose ${token}`).toBeFalsy();
    }
}

function expectNoForbiddenRuntimeCalls(requests: RuntimeBoundaryRequests) {
    expect(requests.forbiddenWriteCalls, 'must not call forbidden write endpoints').toEqual([]);
    expect(requests.apiRequests.some((entry) => entry.includes('permission-probe')), 'must not call permission probe endpoints').toBeFalsy();
    expect(
        requests.apiRequests.some((entry) => entry.includes('ingestions/run-once') || entry.includes('ingestion-jobs/run-once')),
        'must not trigger ingestion run-once',
    ).toBeFalsy();
    expect(requests.externalExchangeRequests, 'must not call external exchange hosts from the browser').toEqual([]);
}

test.describe('runtime operational readiness final smoke', () => {
    test('keeps the local runbook API and UI path fail-closed', async ({page}) => {
        const session = await loginWithoutAccountFixture(page);
        const directPayload = await getOperationalReadiness(page, session);
        const requests = trackRuntimeBoundaryRequests(page);

        expectStatus(directPayload, 'liveStatus', 'DISABLED');
        expectStatus(directPayload, 'aiStatus', 'NOT_STARTED');
        expectStatus(directPayload, 'dhRuntimeStatus', 'NOT_INTEGRATED');
        expectStatus(directPayload, 'realProviderStatus', 'NOT_IMPLEMENTED');
        expectStatus(directPayload, 'credentialExposureStatus', 'NOT_EXPOSED');
        expectStatus(directPayload, 'permissionProbeStatus', 'SKIPPED');
        expectEveryStatusBlocked(directPayload);
        expectNoCredentialLikeText(JSON.stringify(directPayload), 'safe summary');

        const operationalResponsePromise = page.waitForResponse((response) => (
            response.url().includes('/api/runtime/operational-readiness')
            && response.request().method() === 'GET'
        ), {timeout: 30_000});

        await page.goto('/runtime/readiness');
        await expect(page).toHaveURL(/\/runtime\/readiness$/);

        const operationalResponse = await operationalResponsePromise;
        expect(operationalResponse.status()).toBe(200);
        const pagePayload = await operationalResponse.json() as OperationalReadinessResponse;
        expectEveryStatusBlocked(pagePayload);

        const overview = page.getByTestId('operational-readiness-overview');
        await expect(overview).toBeVisible();
        await expect(overview.getByText('Operational readiness', {exact: true})).toBeVisible();

        // 默认摘要使用产品本地化文案；先按业务行验证，避免原始代码在隐藏区域造成空通过。
        for (const [area, status] of [
            ['LIVE status', 'Disabled'],
            ['AI status', 'Not started'],
            ['DH runtime status', 'Not integrated'],
            ['Real provider status', 'Not implemented'],
            ['Credential exposure status', 'Sensitive information omitted'],
            ['Permission probe status', 'Disabled or skipped'],
        ]) {
            const row = overview.getByRole('row')
                .filter({has: page.getByRole('cell', {name: area, exact: true})});
            await expect(row).toHaveCount(1);
            await expect(row.getByRole('cell', {name: status, exact: true})).toBeVisible();
        }
        await expect(overview).toContainText('Runtime status is diagnostic and does not authorize trading');
        await expect(overview).toContainText('Process health and real trading authorization are separate. Runtime status does not establish provider, permission or LIVE readiness.');

        for (const boundary of [
            'Paper-only boundary, not real authorization',
            'Skipped / disabled is not a pass state',
            'No real trading capability',
        ]) {
            await expect(page.getByText(boundary, {exact: true})).toBeVisible();
        }

        // 原始状态和安全状态位于懒挂载的技术详情，必须通过可访问入口展开后验证。
        const technicalDetails = overview.getByRole('button', {name: /Technical details and original reasons/});
        await expect(technicalDetails).toHaveCount(1);
        await expect(technicalDetails).toHaveAttribute('aria-expanded', 'false');
        await technicalDetails.click();
        await expect(technicalDetails).toHaveAttribute('aria-expanded', 'true');

        const operationalAreas: Record<keyof Omit<OperationalReadinessResponse, 'generatedAt'>, string> = {
            liveStatus: 'LIVE status',
            aiStatus: 'AI status',
            dhRuntimeStatus: 'DH runtime status',
            realProviderStatus: 'Real provider status',
            credentialExposureStatus: 'Credential exposure status',
            externalExchangeCallStatus: 'External exchange call status',
            permissionProbeStatus: 'Permission probe status',
            startupBoundaryStatus: 'Startup boundary status',
            profileBoundaryStatus: 'Profile boundary status',
            configDiagnosticsStatus: 'Configuration diagnostics status',
            logDiagnosticsStatus: 'Log diagnostics status',
        };
        // 同一业务行的原始代码、原因及 BLOCKED 必须对应真实响应，不能由另一行或摘要替代。
        for (const field of OPERATIONAL_FIELDS) {
            expect(pagePayload[field].status, field + ' UI response must preserve the direct API status').toBe(directPayload[field].status);
            const row = overview.getByRole('row')
                .filter({has: page.getByText(operationalAreas[field], {exact: true})})
                .filter({has: page.getByRole('cell', {name: 'BLOCKED', exact: true})});
            await expect(row).toHaveCount(1);
            await expect(row.getByRole('cell', {name: pagePayload[field].status, exact: true})).toBeVisible();
            await expect(row.getByRole('cell', {name: pagePayload[field].reasonCode, exact: true})).toBeVisible();
            await expect(row.getByRole('cell', {name: pagePayload[field].reason, exact: true})).toBeVisible();
        }

        await expect(overview.getByText('BLOCKED')).toHaveCount(11);
        await expect(page.getByText(/live[-\s]?ready/i)).toHaveCount(0);
        await expect(page.getByText(/verified/i)).toHaveCount(0);
        await expect(page.getByText(/LIVE authorized/i)).toHaveCount(0);
        await expect(page.getByText('LIVE 已授权')).toHaveCount(0);

        expectNoCredentialLikeText(await page.locator('body').innerText(), 'runtime page');
        expectNoForbiddenRuntimeCalls(requests);
    });
});
