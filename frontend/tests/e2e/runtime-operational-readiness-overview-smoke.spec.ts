import {expect, test, type Page, type Route} from 'playwright/test';

/**
 * GateM-6A/6C backend-free smoke for the Operational Readiness section.
 *
 * Why:
 * The section is a read-only UI boundary. After GateM-6B, the page should prefer backend safe
 * summary fields while still avoiding write endpoints, permission probe POST, ingestion run-once,
 * trading actions, real exchanges, or credential-bearing diagnostics.
 */

interface ReadinessItem {
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

const SECRET_TOKENS = ['apiKey', 'api_key', 'secret', 'token', 'signature', 'passphrase', 'private key', 'mnemonic'];
const CAPABILITIES = ['PUBLIC_MARKETDATA', 'PLACE_ORDER', 'CANCEL_ORDER', 'PERMISSION_PROBE'];

function buildReadinessItems(): ReadinessItem[] {
    const items: ReadinessItem[] = [];

    for (const venue of ['NOOP', 'PAPER', 'SIM']) {
        for (const capability of CAPABILITIES) {
            items.push({
                venue,
                capability,
                status: 'NO_REAL',
                allowed: false,
                liveAuthorized: false,
                reasons: ['NO_REAL_DISABLED', 'READY_FOR_PAPER_ONLY'],
                message: 'no-real / paper-only runtime; not real authorization',
            });
        }
    }

    for (const venue of ['FAKE', 'STUB', 'FUTURE_REAL']) {
        items.push({
            venue,
            capability: 'PUBLIC_MARKETDATA',
            status: 'NOT_READY',
            allowed: false,
            liveAuthorized: false,
            reasons: [venue === 'FUTURE_REAL' ? 'FUTURE_REAL_DISABLED' : venue, 'LIVE_NOT_AUTHORIZED'],
            message: `${venue} is blocked for current GateM runtime`,
        });
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
                    ? ['PERMISSION_PROBE_DISABLED', 'SKIPPED', 'CREDENTIAL_UNCONFIGURED', 'LIVE_NOT_AUTHORIZED']
                    : ['ENDPOINT_DISABLED_SENTINEL', 'CREDENTIAL_UNCONFIGURED', 'LIVE_NOT_AUTHORIZED'],
                message: 'exchange adapter not authorized: no real provider / LIVE disabled',
            });
        }
    }

    return items;
}

function operationalStatus(status: string, reasonCode: string, reason: string): OperationalStatus {
    return {status, ready: false, reasonCode, reason};
}

function buildOperationalReadinessSummary() {
    return {
        generatedAt: '2026-06-30T14:00:00Z',
        liveStatus: operationalStatus('DISABLED', 'LIVE_DISABLED', 'LIVE is disabled.'),
        aiStatus: operationalStatus('NOT_STARTED', 'AI_NOT_STARTED', 'AI runtime has not started.'),
        dhRuntimeStatus: operationalStatus('NOT_INTEGRATED', 'DH_RUNTIME_NOT_INTEGRATED', 'DH runtime is not integrated.'),
        realProviderStatus: operationalStatus('NOT_IMPLEMENTED', 'REAL_PROVIDER_NOT_IMPLEMENTED', 'Real provider is not implemented.'),
        credentialExposureStatus: operationalStatus('NOT_EXPOSED', 'CREDENTIAL_MATERIAL_NOT_EXPOSED', 'Credential material is not exposed.'),
        externalExchangeCallStatus: operationalStatus('DISABLED', 'EXTERNAL_EXCHANGE_CALL_DISABLED', 'No external exchange call is performed.'),
        permissionProbeStatus: operationalStatus('SKIPPED', 'PERMISSION_PROBE_SKIPPED', 'Permission probe is skipped.'),
        startupBoundaryStatus: operationalStatus('SAFE_BY_DEFAULT', 'STARTUP_BOUNDARY_SAFE', 'Startup boundary is safe-by-default.'),
        profileBoundaryStatus: operationalStatus('SAFE_SUMMARY_ONLY', 'PROFILE_BOUNDARY_SAFE_SUMMARY_ONLY', 'Safe profile summary only.'),
        configDiagnosticsStatus: operationalStatus('SAFE_SUMMARY_ONLY', 'CONFIG_DIAGNOSTICS_SAFE_SUMMARY_ONLY', 'Safe config diagnostics summary only.'),
        logDiagnosticsStatus: operationalStatus('SAFE_SUMMARY_ONLY', 'LOG_DIAGNOSTICS_SAFE_SUMMARY_ONLY', 'Safe log diagnostics summary only.'),
    };
}

async function seedOperationalReadinessStubs(page: Page): Promise<void> {
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'runtime-operational-readiness-smoke-session',
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
        json: {generatedAt: '2026-06-30T13:00:00Z', items: buildReadinessItems()},
    }));

    await page.route('**/api/runtime/operational-readiness', (route: Route) => route.fulfill({
        status: 200,
        json: buildOperationalReadinessSummary(),
    }));
}

test.describe('runtime operational readiness overview', () => {
    test('shows backend summary boundaries without write endpoint calls', async ({page}) => {
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

        await seedOperationalReadinessStubs(page);

        await page.goto('/runtime/readiness');

        const overview = page.getByTestId('operational-readiness-overview');
        await expect(overview).toBeVisible();
        await expect(overview.getByText('Operational Readiness', {exact: true})).toBeVisible();

        await expect(overview).toContainText('LIVE status');
        await expect(overview).toContainText('DISABLED');
        await expect(overview).toContainText('LIVE_DISABLED');
        await expect(overview).toContainText('AI status');
        await expect(overview).toContainText('NOT_STARTED');
        await expect(overview).toContainText('DH runtime status');
        await expect(overview).toContainText('NOT_INTEGRATED');
        await expect(overview).toContainText('Real provider status');
        await expect(overview).toContainText('NOT_IMPLEMENTED');
        await expect(overview).toContainText('Credential exposure status');
        await expect(overview).toContainText('NOT_EXPOSED');
        await expect(overview).toContainText('Permission probe status');
        await expect(overview).toContainText('SKIPPED');
        await expect(overview).toContainText('Profile boundary status');
        await expect(overview).toContainText('SAFE_SUMMARY_ONLY');
        await expect(overview).toContainText('Operational readiness summary is fail-closed');
        await expect(overview).toContainText('Actuator health is process health only, not LIVE authorization.');
        await expect(overview).toContainText('Runtime UI does not prove real provider readiness');
        await expect(overview).toContainText('Paper-only / SKIPPED / NoReal signals are not real-ready.');

        await expect(overview.getByRole('link', {name: 'View MarketData readiness'}))
            .toHaveAttribute('href', '/marketdata?exchangeCode=BINANCE&marketType=SPOT&symbol=BTC-USDT&interval=1m');
        await expect(overview.getByRole('link', {name: 'View Dashboard runtime summary'}))
            .toHaveAttribute('href', '/dashboard');

        await expect(page.getByText('LIVE disabled').first()).toBeVisible();
        await expect(page.getByText('RealClient / real provider / real exchange adapter not implemented')).toBeVisible();
        await expect(page.getByText('NO_REAL').first()).toBeVisible();
        await expect(page.getByText(/LIVE ready/i)).toHaveCount(0);
        await expect(page.getByText('LIVE 已授权')).toHaveCount(0);

        const bodyText = (await page.locator('body').innerText()).toLowerCase();
        for (const token of SECRET_TOKENS) {
            expect(bodyText.includes(token.toLowerCase()), `operational readiness page must not leak ${token}`).toBeFalsy();
        }

        expect(apiWrites, 'operational readiness overview must not call write endpoints').toEqual([]);
        expect(apiRequests.some((entry) => entry.includes('permission-probe')), 'must not call permission probe endpoints').toBeFalsy();
        expect(apiRequests.some((entry) => entry.includes('ingestions/run-once') || entry.includes('ingestion-jobs/run-once')), 'must not trigger ingestion run-once').toBeFalsy();
        expect(apiRequests.some((entry) => !entry.startsWith('GET ') && /order|cancel/i.test(entry)), 'must not submit order/cancel writes').toBeFalsy();
        expect(apiRequests.some((entry) => !entry.startsWith('GET ') && /transfer|withdraw/i.test(entry)), 'must not submit transfer/withdraw writes').toBeFalsy();
        expect(externalExchangeRequests, 'must not call external exchange hosts').toEqual([]);
    });
});
