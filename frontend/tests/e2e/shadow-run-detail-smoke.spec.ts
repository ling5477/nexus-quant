import {expect, test, type Page, type Route} from 'playwright/test';

const SHADOW_RUN_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const DATASET_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

const detailFixture = {
    id: SHADOW_RUN_ID,
    strategyVersionId: 'sv-gater-7',
    datasetId: DATASET_ID,
    evaluationId: 'eval-gater-7',
    publishId: 'pub-gater-7',
    paperRunId: 'paper-gater-7',
    status: 'COMPLETED',
    windowStart: '2026-07-06T10:00:00Z',
    windowEnd: '2026-07-06T11:00:00Z',
    authorizationBoundary: 'DIAGNOSTIC_ONLY',
    sideEffectFlags: {
        noOrderSubmission: true,
        noCredentialAccess: true,
        noPrivateEndpoint: true,
        noLedgerMutation: true,
        noAccountMutation: true,
        noExternalPrivateIo: true,
    },
    blockers: [],
    warnings: ['read-only diagnostic', {code: 'NO_TRADING_AUTHORIZATION', token: 'token-should-not-render'}],
    nextSteps: ['Review replay evidence only.', {
        code: 'KEEP_LOCAL_ONLY',
        credentialMaterial: 'credential-should-not-render'
    }],
    requestId: 'req-shadow-gater-7',
    traceId: 'trace-shadow-gater-7',
    createdAt: '2026-07-06T10:00:00Z',
    updatedAt: '2026-07-06T11:00:00Z',
    startedAt: '2026-07-06T10:01:00Z',
    stoppedAt: null,
    completedAt: '2026-07-06T11:00:00Z',
};

const eventsFixture = [
    {
        eventType: 'CREATED',
        fromStatus: null,
        toStatus: 'CREATED',
        reasonCode: 'CREATED',
        message: 'local shadow run created',
        metadata: {diagnosticOnly: true},
        requestId: 'req-shadow-gater-7',
        traceId: 'trace-shadow-gater-7',
        createdAt: '2026-07-06T10:00:00Z',
    },
    {
        eventType: 'COMPLETED',
        fromStatus: 'RUNNING',
        toStatus: 'COMPLETED',
        reasonCode: 'COMPLETED',
        message: 'local shadow run completed',
        metadata: {diagnosticOnly: true, secret: 'secret-should-not-render'},
        requestId: 'req-shadow-gater-7',
        traceId: 'trace-shadow-gater-7',
        createdAt: '2026-07-06T11:00:00Z',
    },
];

const snapshotsFixture = [
    {
        snapshotType: 'ORDER_INTENT_PREVIEW',
        sequenceNo: 2,
        source: 'LOCAL_CALLER_SUPPLIED_READONLY_INPUT',
        schemaVersion: 'shadow-order-intent-preview.v1',
        checksum: 'sha256-order-preview',
        payload: {
            previewOnly: true,
            safeSymbol: 'BTC-USDT',
            apiKey: 'api-key-should-not-render',
            nested: {
                safeValue: 'visible-safe-value',
                realOrderId: 'real-order-should-not-render',
            },
        },
        capturedAt: '2026-07-06T10:03:00Z',
        traceId: 'trace-shadow-gater-7',
    },
    {
        snapshotType: 'INPUT_MARKETDATA',
        sequenceNo: 1,
        source: 'PUBLIC_MARKETDATA_SNAPSHOT',
        schemaVersion: 'shadow-input-marketdata.v1',
        checksum: 'sha256-input-marketdata',
        payload: {barCount: 120, symbol: 'BTC-USDT'},
        capturedAt: '2026-07-06T10:02:00Z',
        traceId: 'trace-shadow-gater-7',
    },
];

const reportFixture = {
    id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    shadowRunId: SHADOW_RUN_ID,
    paperRunId: 'paper-gater-7',
    comparisonStatus: 'CONSISTENT',
    metricDelta: {
        schemaVersion: 'shadow-consistency-report.v1',
        returnDelta: 0.01,
        authorizedForTrading: true,
    },
    divergenceReasons: [{code: 'NONE', message: 'No material divergence.'}],
    limitations: ['diagnostic only', {code: 'NO_LIVE_AUTHORIZATION', passphrase: 'passphrase-should-not-render'}],
    generatedAt: '2026-07-06T11:05:00Z',
    traceId: 'trace-shadow-gater-7',
};

interface StubOptions {
    detailStatus?: number;
    reportStatus?: number;
    delayDetailMs?: number;
}

function shadowRunUrl(): string {
    return `/strategies/shadow-runs/${SHADOW_RUN_ID}`;
}

function apiError(status: number, message: string) {
    return {
        timestamp: '2026-07-06T11:10:00Z',
        status,
        code: status === 404 ? 'RESOURCE_NOT_FOUND' : 'INTERNAL_ERROR',
        message,
        path: `/api/shadow-runs/${SHADOW_RUN_ID}`,
        traceId: 'trace-shadow-error',
        fieldErrors: [],
    };
}

async function seedAuthAndShadowRunStubs(page: Page, options: StubOptions = {}): Promise<string[]> {
    const requests: string[] = [];

    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'shadow-run-detail-smoke-session',
            tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z',
            username: 'e2e-operator',
            roles: ['ADMIN'],
        }));
    });

    page.on('request', (request) => {
        requests.push(`${request.method()} ${request.url()}`);
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

    await page.route(`**/api/shadow-runs/${SHADOW_RUN_ID}`, async (route: Route) => {
        if (options.delayDetailMs) {
            await new Promise((resolve) => setTimeout(resolve, options.delayDetailMs));
        }
        if (options.detailStatus === 404) {
            return route.fulfill({status: 404, json: apiError(404, `shadow run not found: ${SHADOW_RUN_ID}`)});
        }
        return route.fulfill({status: 200, json: detailFixture});
    });

    await page.route(`**/api/shadow-runs/${SHADOW_RUN_ID}/events`, (route: Route) => route.fulfill({
        status: 200,
        json: eventsFixture,
    }));

    await page.route(`**/api/shadow-runs/${SHADOW_RUN_ID}/snapshots`, (route: Route) => route.fulfill({
        status: 200,
        json: snapshotsFixture,
    }));

    await page.route(`**/api/shadow-runs/${SHADOW_RUN_ID}/consistency-report/latest`, (route: Route) => {
        if (options.reportStatus === 500) {
            return route.fulfill({status: 500, json: apiError(500, 'latest consistency report query failed')});
        }
        return route.fulfill({status: 200, json: reportFixture});
    });

    return requests;
}

function expectNoForbiddenRequests(requests: string[]): void {
    const forbiddenApiPattern = /start|stop|execute|rerun|approve|trade|placeOrder|cancelOrder|withdraw|transfer|credential|permission-probe|private/i;
    const forbiddenHostPattern = /okx|binance|bybit|coinbase|kraken|gate\.io/i;

    for (const requestEntry of requests) {
        const [, requestUrl] = requestEntry.split(' ');
        const hostname = new URL(requestUrl).hostname;
        expect(hostname, `forbidden real exchange host request: ${requestEntry}`).not.toMatch(forbiddenHostPattern);
        if (requestUrl.includes('/api/shadow-runs/')) {
            expect(requestEntry, `Shadow Run detail page must only call read-only API: ${requestEntry}`).toMatch(/^GET /);
            expect(requestUrl, `forbidden Shadow Run write/private API request: ${requestEntry}`).not.toMatch(forbiddenApiPattern);
        }
    }
}

async function expectNoSensitiveCopy(page: Page): Promise<void> {
    const bodyText = await page.locator('body').innerText();
    for (const forbidden of [
        'apiKey',
        'api-key-should-not-render',
        'secret-should-not-render',
        'token-should-not-render',
        'credentialMaterial',
        'credential-should-not-render',
        'realOrderId',
        'real-order-should-not-render',
        'authorizedForTrading',
        'passphrase',
        'passphrase-should-not-render',
        'tradingReady',
        'liveReady',
        'tradeApproved',
    ]) {
        expect(bodyText, `sensitive field/value must not render: ${forbidden}`).not.toContain(forbidden);
    }
}

test.describe('Shadow Run detail / replay read-only view', () => {
    test('展示 detail、events、snapshots、latest consistency report 与 no-side-effect flags', async ({page}) => {
        const requests = await seedAuthAndShadowRunStubs(page);

        await page.goto(shadowRunUrl());

        const view = page.getByTestId('shadow-run-detail-page');
        await expect(view).toBeVisible();
        await expect(view.getByRole('heading', {name: 'Shadow Run detail / replay'})).toBeVisible();

        await expect(view).toContainText('LIVE disabled');
        await expect(view).toContainText('Diagnostic only');
        await expect(view).toContainText('No order submission: true');
        await expect(view).toContainText('No credential access: true');
        await expect(view).toContainText('No private endpoint: true');
        await expect(view).toContainText('No ledger mutation: true');
        await expect(view).toContainText('No account mutation: true');
        await expect(view).toContainText('no trading authorization');

        await expect(view).toContainText('sv-gater-7');
        await expect(view).toContainText(DATASET_ID);
        await expect(view).toContainText('trace-shadow-gater-7');

        const timeline = page.getByRole('region', {name: 'Shadow Run events timeline'});
        await expect(timeline).toBeVisible();
        await expect(timeline).toContainText('CREATED');
        await expect(timeline).toContainText('COMPLETED');
        const timelineText = await timeline.innerText();
        expect(timelineText.indexOf('CREATED')).toBeLessThan(timelineText.indexOf('COMPLETED'));

        const snapshots = page.getByRole('region', {name: 'Shadow Run snapshots panel'});
        await expect(snapshots).toBeVisible();
        await expect(snapshots).toContainText('INPUT_MARKETDATA');
        await expect(snapshots).toContainText('ORDER_INTENT_PREVIEW');
        await expect(snapshots).toContainText('shadow-input-marketdata.v1');
        await expect(snapshots).toContainText('BTC-USDT');

        await snapshots.getByText('ORDER_INTENT_PREVIEW').first().click();
        await expect(snapshots).toContainText('shadow-order-intent-preview.v1');
        await expect(snapshots).toContainText('previewOnly');
        await expect(snapshots).toContainText('visible-safe-value');

        const report = page.getByRole('region', {name: 'Shadow consistency report panel'});
        await expect(report).toBeVisible();
        await expect(report).toContainText('CONSISTENT');
        await expect(report).toContainText('metricDelta');
        await expect(report).toContainText('returnDelta');
        await expect(report).toContainText('divergenceReasons');
        await expect(report).toContainText('limitations');
        await expect(report).toContainText('diagnostic only');
        await expect(report).toContainText('不是 approval，不代表 trading authorization');

        await expect(page.getByRole('button', {name: /start|stop|execute|rerun|approve|trade|下单|撤单|转账|提现/i})).toHaveCount(0);
        await expectNoSensitiveCopy(page);
        expectNoForbiddenRequests(requests);
        expect(requests.some((entry) => entry.includes(`/api/shadow-runs/${SHADOW_RUN_ID}`))).toBeTruthy();
        expect(requests.some((entry) => entry.includes('/events'))).toBeTruthy();
        expect(requests.some((entry) => entry.includes('/snapshots'))).toBeTruthy();
        expect(requests.some((entry) => entry.includes('/consistency-report/latest'))).toBeTruthy();
    });

    test('API 404 时展示 not found / empty state 且不继续请求 facts', async ({page}) => {
        const requests = await seedAuthAndShadowRunStubs(page, {detailStatus: 404});

        await page.goto(shadowRunUrl());

        const view = page.getByTestId('shadow-run-detail-page');
        await expect(view).toContainText('Shadow Run not found / Shadow Run 不存在');
        await expect(view).toContainText(SHADOW_RUN_ID);
        await expect(page.getByRole('button', {name: /start|stop|execute|rerun|approve|trade/i})).toHaveCount(0);
        expect(requests.some((entry) => entry.includes('/events'))).toBeFalsy();
        expect(requests.some((entry) => entry.includes('/snapshots'))).toBeFalsy();
        expect(requests.some((entry) => entry.includes('/consistency-report/latest'))).toBeFalsy();
        expectNoForbiddenRequests(requests);
    });

    test('loading 与 latest consistency report error 状态可见', async ({page}) => {
        await seedAuthAndShadowRunStubs(page, {delayDetailMs: 400, reportStatus: 500});

        await page.goto(shadowRunUrl());

        await expect(page.getByText('Shadow Run detail loading')).toBeVisible();
        const view = page.getByTestId('shadow-run-detail-page');
        await expect(view).toContainText('Shadow Run 基本信息');
        await expect(view).toContainText('Consistency report 加载失败');
        await expect(view).toContainText('latest consistency report query failed');
        await expect(page.getByRole('button', {name: /start|stop|execute|rerun|approve|trade/i})).toHaveCount(0);
    });
});
