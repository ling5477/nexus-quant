import {expect, test, type Page} from 'playwright/test';

/**
 * GateM-6D Operational Readiness real-backend smoke.
 *
 * Why:
 * GateM-6C proved the page integration with backend-free stubs. This smoke keeps the browser on the
 * real local backend, does not mock `/api/runtime/operational-readiness`, and proves the UI consumes
 * the GateM-6B safe summary without turning process health, skipped probes, Paper-only, or no-real
 * signals into real-ready / LIVE-ready capability.
 *
 * Boundaries:
 * - Only real `GET /api/runtime/operational-readiness` is verified.
 * - No permission-probe POST, ingestion run-once, order, cancel, transfer, or withdraw endpoint.
 * - No real exchange host, LIVE enablement, AI runtime, DH runtime, RealClient, or real provider.
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
    expect(session.accessToken, 'real-backend smoke requires an accessToken from /api/auth/login').toBeTruthy();
    expect(session.tokenType, 'real-backend smoke requires tokenType from /api/auth/login').toBeTruthy();
    expect(session.expiresAt, 'real-backend smoke requires expiresAt from /api/auth/login').toBeTruthy();
    expect(session.username, 'real-backend smoke requires username from /api/auth/login').toBeTruthy();
    expect(Array.isArray(session.roles), 'real-backend smoke requires roles from /api/auth/login').toBeTruthy();

    await page.addInitScript((payload: LoginSession) => {
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

function expectNoForbiddenRuntimeCalls(requests: RuntimeBoundaryRequests) {
    expect(requests.forbiddenWriteCalls, 'must not call forbidden write endpoints').toEqual([]);
    expect(requests.apiRequests.some((entry) => entry.includes('permission-probe')), 'must not call permission probe endpoints').toBeFalsy();
    expect(
        requests.apiRequests.some((entry) => entry.includes('ingestions/run-once') || entry.includes('ingestion-jobs/run-once')),
        'must not trigger ingestion run-once',
    ).toBeFalsy();
    expect(requests.externalExchangeRequests, 'must not call external exchange hosts from the browser').toEqual([]);
}

test.describe('runtime operational readiness real backend smoke', () => {
    test('consumes real safe summary and remains fail-closed', async ({page}) => {
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

        const directPayloadText = JSON.stringify(directPayload).toLowerCase();
        for (const token of SECRET_TOKENS) {
            expect(directPayloadText.includes(token.toLowerCase()), `safe summary must not expose ${token}`).toBeFalsy();
        }

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
        await expect(overview.getByText('Operational Readiness', {exact: true})).toBeVisible();

        for (const text of [
            'LIVE status',
            'DISABLED',
            'AI status',
            'NOT_STARTED',
            'DH runtime status',
            'NOT_INTEGRATED',
            'Real provider status',
            'NOT_IMPLEMENTED',
            'Credential exposure status',
            'NOT_EXPOSED',
            'Permission probe status',
            'SKIPPED',
            'Operational readiness summary is fail-closed',
            'Actuator health is process health only, not LIVE authorization.',
            'Runtime UI does not prove real provider readiness',
            'Paper-only / SKIPPED / NoReal signals are not real-ready.',
        ]) {
            await expect(overview).toContainText(text);
        }

        await expect(overview.getByText('BLOCKED')).toHaveCount(11);
        await expect(page.getByText(/live[-\s]?ready/i)).toHaveCount(0);
        await expect(page.getByText(/verified/i)).toHaveCount(0);
        await expect(page.getByText('LIVE 已授权')).toHaveCount(0);

        const bodyText = (await page.locator('body').innerText()).toLowerCase();
        for (const token of SECRET_TOKENS) {
            expect(bodyText.includes(token.toLowerCase()), `runtime page must not leak ${token}`).toBeFalsy();
        }

        expectNoForbiddenRuntimeCalls(requests);
    });
});
