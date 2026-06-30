import {expect, test, type Page, type Route} from 'playwright/test';

interface AdapterReadinessItem {
    venue: string;
    capability: string;
    status: string;
    allowed: boolean;
    liveAuthorized: boolean;
    reasons: string[];
    message: string;
}

interface OperationalStatus {
    status: string;
    ready: boolean;
    reasonCode: string;
    reason: string;
}

type OperationalReadinessResponse = Record<string, string | OperationalStatus>;

const FORBIDDEN_UI_TOKENS = [
    'apiKey',
    'api_key',
    'secret',
    'token',
    'signature',
    'passphrase',
    'private key',
    'mnemonic',
    'cookie',
];

const CAPABILITIES = ['PUBLIC_MARKETDATA', 'PLACE_ORDER', 'CANCEL_ORDER', 'PERMISSION_PROBE'];

function buildAdapterReadinessItems(): AdapterReadinessItem[] {
    const items: AdapterReadinessItem[] = [];

    for (const venue of ['NOOP', 'PAPER', 'SIM']) {
        for (const capability of CAPABILITIES) {
            items.push({
                venue,
                capability,
                status: 'NO_REAL',
                allowed: false,
                liveAuthorized: false,
                reasons: ['NO_REAL_DISABLED'],
                message: 'no-real / paper-only runtime; not real authorization',
            });
        }
    }

    for (const venue of ['OKX', 'BINANCE']) {
        for (const capability of CAPABILITIES) {
            items.push({
                venue,
                capability,
                status: 'NOT_READY',
                allowed: false,
                liveAuthorized: false,
                reasons: capability === 'PERMISSION_PROBE'
                    ? ['PERMISSION_PROBE_DISABLED', 'SKIPPED', 'LIVE_NOT_AUTHORIZED']
                    : ['ENDPOINT_DISABLED_SENTINEL', 'LIVE_NOT_AUTHORIZED'],
                message: 'exchange adapter not authorized: no real provider / LIVE disabled',
            });
        }
    }

    return items;
}

function status(status: string, reasonCode: string, reason: string): OperationalStatus {
    return {status, ready: false, reasonCode, reason};
}

function buildOperationalReadinessResponse(): OperationalReadinessResponse {
    return {
        generatedAt: '2026-06-30T14:00:00Z',
        liveStatus: status('DISABLED', 'LIVE_DISABLED', 'LIVE is disabled; no live authorization is available.'),
        aiStatus: status('NOT_STARTED', 'AI_NOT_STARTED', 'AI runtime has not started.'),
        dhRuntimeStatus: status('NOT_INTEGRATED', 'DH_RUNTIME_NOT_INTEGRATED', 'DH runtime is not integrated with NQ.'),
        realProviderStatus: status('NOT_IMPLEMENTED', 'REAL_PROVIDER_NOT_IMPLEMENTED', 'Real provider and RealClient are not implemented.'),
        credentialExposureStatus: status('NOT_EXPOSED', 'CREDENTIAL_MATERIAL_NOT_EXPOSED', 'Credential material is not exposed in this summary.'),
        externalExchangeCallStatus: status('DISABLED', 'EXTERNAL_EXCHANGE_CALL_DISABLED', 'No external exchange call is performed by this summary.'),
        permissionProbeStatus: status('SKIPPED', 'PERMISSION_PROBE_SKIPPED', 'Permission probe is skipped and does not prove real readiness.'),
        startupBoundaryStatus: status('SAFE_BY_DEFAULT', 'STARTUP_BOUNDARY_SAFE', 'Startup boundary summary is safe-by-default.'),
        profileBoundaryStatus: status('SAFE_SUMMARY_ONLY', 'PROFILE_BOUNDARY_SAFE_SUMMARY_ONLY', 'Only safe profile summary is exposed.'),
        configDiagnosticsStatus: status('SAFE_SUMMARY_ONLY', 'CONFIG_DIAGNOSTICS_SAFE_SUMMARY_ONLY', 'Only safe config diagnostics summary is exposed.'),
        logDiagnosticsStatus: status('SAFE_SUMMARY_ONLY', 'LOG_DIAGNOSTICS_SAFE_SUMMARY_ONLY', 'Only safe log diagnostics summary is exposed.'),
    };
}

async function seedAuthAndRuntimeStubs(
    page: Page,
    operationalHandler: (route: Route) => Promise<void> | void,
): Promise<void> {
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'runtime-operational-readiness-integration-session',
            tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z',
            username: 'e2e-operator',
            roles: ['ADMIN'],
        }));
    });

    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) => route.fulfill({status: 200, json: []}));

    await page.route('**/api/auth/me', (route: Route) => route.fulfill({
        status: 200,
        json: {
            userId: 1,
            username: 'e2e-operator',
            roles: ['ADMIN'],
            authenticated: true,
            defaultExchangeAccountId: null,
            defaultExchangeCode: null,
            defaultTradeEnv: null,
            defaultAccountAlias: null,
        },
    }));

    await page.route('**/api/exchange-accounts', (route: Route) => route.fulfill({
        status: 200,
        json: [],
    }));

    await page.route('**/api/adapters/readiness', (route: Route) => route.fulfill({
        status: 200,
        json: {generatedAt: '2026-06-30T14:00:00Z', items: buildAdapterReadinessItems()},
    }));

    await page.route('**/api/runtime/operational-readiness', operationalHandler);
}

function trackRuntimeBoundaryRequests(page: Page) {
    const apiWrites: string[] = [];
    const apiRequests: string[] = [];
    const externalExchangeRequests: string[] = [];

    page.on('request', (request) => {
        const url = request.url();
        const entry = `${request.method()} ${url}`;
        const host = new URL(url).hostname;

        if (/okx|binance|bybit|gate|coinbase|kraken/i.test(host)) {
            externalExchangeRequests.push(entry);
        }
        if (!url.includes('/api/')) {
            return;
        }
        apiRequests.push(entry);
        if (request.method() !== 'GET') {
            apiWrites.push(entry);
        }
    });

    return {apiWrites, apiRequests, externalExchangeRequests};
}

function expectNoForbiddenRuntimeCalls(requests: {
    apiWrites: string[];
    apiRequests: string[];
    externalExchangeRequests: string[];
}) {
    expect(requests.apiWrites, 'operational readiness must not call write endpoints').toEqual([]);
    expect(requests.apiRequests.some((entry) => entry.includes('permission-probe')), 'must not call permission probe endpoints').toBeFalsy();
    expect(requests.apiRequests.some((entry) => entry.includes('ingestions/run-once') || entry.includes('ingestion-jobs/run-once')), 'must not trigger ingestion run-once').toBeFalsy();
    expect(requests.apiRequests.some((entry) => !entry.startsWith('GET ') && /order|cancel/i.test(entry)), 'must not submit order/cancel writes').toBeFalsy();
    expect(requests.apiRequests.some((entry) => !entry.startsWith('GET ') && /transfer|withdraw/i.test(entry)), 'must not submit transfer/withdraw writes').toBeFalsy();
    expect(requests.externalExchangeRequests, 'must not call external exchange hosts').toEqual([]);
}

test.describe('runtime operational readiness backend summary integration', () => {
    test('shows backend safe summary without exposing credential values or write actions', async ({page}) => {
        const requests = trackRuntimeBoundaryRequests(page);

        await seedAuthAndRuntimeStubs(page, (route) => route.fulfill({
            status: 200,
            json: buildOperationalReadinessResponse(),
        }));

        const summaryResponse = page.waitForResponse((response) => (
            response.url().includes('/api/runtime/operational-readiness')
            && response.request().method() === 'GET'
            && response.status() === 200
        ));

        await page.goto('/runtime/readiness');
        await summaryResponse;

        const overview = page.getByTestId('operational-readiness-overview');
        await expect(overview).toBeVisible();
        await expect(overview).toContainText('Operational Readiness');
        await expect(overview).toContainText('generated 2026');

        for (const text of [
            'LIVE status',
            'DISABLED',
            'LIVE_DISABLED',
            'AI status',
            'NOT_STARTED',
            'AI_NOT_STARTED',
            'DH runtime status',
            'NOT_INTEGRATED',
            'DH_RUNTIME_NOT_INTEGRATED',
            'Real provider status',
            'NOT_IMPLEMENTED',
            'REAL_PROVIDER_NOT_IMPLEMENTED',
            'Credential exposure status',
            'NOT_EXPOSED',
            'CREDENTIAL_MATERIAL_NOT_EXPOSED',
            'External exchange call status',
            'EXTERNAL_EXCHANGE_CALL_DISABLED',
            'Permission probe status',
            'SKIPPED',
            'PERMISSION_PROBE_SKIPPED',
            'Startup boundary status',
            'SAFE_BY_DEFAULT',
            'Profile boundary status',
            'SAFE_SUMMARY_ONLY',
            'Config diagnostics status',
            'CONFIG_DIAGNOSTICS_SAFE_SUMMARY_ONLY',
            'Log diagnostics status',
            'LOG_DIAGNOSTICS_SAFE_SUMMARY_ONLY',
        ]) {
            await expect(overview).toContainText(text);
        }

        await expect(overview.getByText('BLOCKED')).toHaveCount(11);
        await expect(overview).toContainText('Operational readiness summary is fail-closed');
        await expect(overview).toContainText('Actuator health is process health only, not LIVE authorization.');
        await expect(overview).toContainText('Runtime UI does not prove real provider readiness');
        await expect(overview).toContainText('Paper-only / SKIPPED / NoReal signals are not real-ready.');

        const bodyText = (await page.locator('body').innerText()).toLowerCase();
        for (const token of FORBIDDEN_UI_TOKENS) {
            expect(bodyText.includes(token.toLowerCase()), `runtime page must not leak ${token}`).toBeFalsy();
        }

        expectNoForbiddenRuntimeCalls(requests);
    });

    test('fails closed when operational readiness summary is unavailable', async ({page}) => {
        const requests = trackRuntimeBoundaryRequests(page);

        await seedAuthAndRuntimeStubs(page, (route) => route.fulfill({
            status: 503,
            json: {
                code: 'OPERATIONAL_READINESS_UNAVAILABLE',
                message: 'runtime summary unavailable',
            },
        }));

        await page.goto('/runtime/readiness');

        const overview = page.getByTestId('operational-readiness-overview');
        await expect(overview).toBeVisible();
        await expect(overview).toContainText('operational readiness summary unavailable');
        await expect(overview).toContainText('UNAVAILABLE');
        await expect(overview).toContainText('PENDING_BACKEND_SUPPORT');
        await expect(overview.getByText('BLOCKED')).toHaveCount(11);

        const overviewText = await overview.innerText();
        expect(overviewText).not.toMatch(/\bREADY\b|READY_FOR_PAPER_ONLY|LIVE ready|LIVE 已授权/i);

        expectNoForbiddenRuntimeCalls(requests);
    });
});
