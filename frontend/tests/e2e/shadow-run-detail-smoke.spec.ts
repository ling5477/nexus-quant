import {expect, test, type Page, type Route} from 'playwright/test';
import zhPages from '@/i18n/locales/zh-CN/pages.json' with {type: 'json'};
import type {PaperShadowConsistencyDrilldownResponse} from '@/features/validation/types/shadow-runs';

const SHADOW_RUN_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const SHADOW_RUN_ID_2 = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
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
    warnings: [
        'read-only diagnostic',
        {code: 'NO_TRADING_AUTHORIZATION', token: 'token-should-not-render'},
    ],
    nextSteps: [
        'Review replay evidence only.',
        {
            code: 'KEEP_LOCAL_ONLY',
            credentialMaterial: 'credential-should-not-render',
        },
    ],
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
    limitations: [
        'diagnostic only',
        {code: 'NO_LIVE_AUTHORIZATION', passphrase: 'fake-passphrase'},
    ],
    generatedAt: '2026-07-06T11:05:00Z',
    traceId: 'trace-shadow-gater-7',
};

// 明确隔离的正式响应形状；计数对应本文件事件/快照，不填充产品运行数据。
const drilldownFixture: PaperShadowConsistencyDrilldownResponse = {
    generatedAt: '2026-07-06T11:06:00Z',
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    shadowRun: {
        shadowRunId: SHADOW_RUN_ID,
        strategyVersionId: detailFixture.strategyVersionId,
        datasetId: DATASET_ID,
        evaluationId: detailFixture.evaluationId,
        publishId: detailFixture.publishId,
        paperRunId: detailFixture.paperRunId,
        status: detailFixture.status,
        authorizationBoundary: detailFixture.authorizationBoundary,
        ...detailFixture.sideEffectFlags,
        createdAt: detailFixture.createdAt,
        updatedAt: detailFixture.updatedAt,
        startedAt: detailFixture.startedAt,
        completedAt: detailFixture.completedAt,
    },
    latestConsistency: {
        reportId: reportFixture.id,
        shadowRunId: SHADOW_RUN_ID,
        paperRunId: reportFixture.paperRunId,
        comparisonStatus: reportFixture.comparisonStatus,
        metricDelta: {returnDelta: 0.01},
        divergenceReasons: [],
        limitations: ['diagnostic only'],
        generatedAt: reportFixture.generatedAt,
        traceId: reportFixture.traceId,
    },
    comparisonStatus: 'CONSISTENT',
    divergenceSeverity: 'NONE',
    metricDelta: {returnDelta: 0.01},
    divergenceReasons: [],
    limitations: ['diagnostic only'],
    snapshotSummary: {
        totalSnapshots: 2,
        inputMarketdataSnapshots: 1,
        strategyDecisionSnapshots: 0,
        riskPreflightSnapshots: 0,
        orderIntentPreviewSnapshots: 1,
        latestSnapshotAt: snapshotsFixture[0].capturedAt,
        latestSnapshotTypes: ['INPUT_MARKETDATA', 'ORDER_INTENT_PREVIEW'],
    },
    eventSummary: {
        totalEvents: 2,
        latestEventAt: eventsFixture[1].createdAt,
        latestEventType: 'COMPLETED',
        latestReasonCode: 'COMPLETED',
    },
    blockers: [],
    warnings: [],
    nextSteps: [],
    evidenceAnchors: [],
    traceId: 'trace-shadow-drilldown',
};

const overviewFixture = {
    generatedAt: '2026-07-06T11:06:00Z',
    evidenceMetadata: {
        source: 'LOCAL_DB_SHADOW_FACTS',
        availability: 'PARTIAL',
        lastCalculatedAt: '2026-07-06T11:05:00Z',
        freshnessStatus: 'UNKNOWN',
        ageSeconds: 60,
        staleAfterSeconds: null,
        staleReason: 'SOURCE_PARTIAL',
        diagnosticOnly: true,
        noSideEffect: true,
        notTradingAuthorization: true,
        liveDisabled: true,
    },
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    totalRuns: 2,
    runningRuns: 0,
    blockedRuns: 1,
    failedRuns: 0,
    completedRuns: 1,
    staleRuns: 1,
    latestRun: null,
    latestConsistency: null,
    divergenceSeverity: 'UNKNOWN',
    blockers: [],
    warnings: [],
    nextSteps: [],
    evidenceAnchors: [],
    traceId: 'trace-shadow-overview-smoke',
};

const listFixture = {
    items: [
        {
            id: SHADOW_RUN_ID,
            status: 'COMPLETED',
            strategyVersionId: 'sv-gater-8',
            datasetId: DATASET_ID,
            paperRunId: 'paper-gater-8',
            authorizationBoundary: 'DIAGNOSTIC_ONLY',
            traceId: 'trace-shadow-gater-8',
            createdAt: '2026-07-06T10:00:00Z',
            updatedAt: '2026-07-06T11:00:00Z',
            startedAt: '2026-07-06T10:01:00Z',
            completedAt: '2026-07-06T11:00:00Z',
            blockersCount: 0,
            warningsCount: 1,
            nextStepsCount: 1,
            noOrderSubmission: true,
            noCredentialAccess: true,
            noPrivateEndpoint: true,
            noLedgerMutation: true,
            noAccountMutation: true,
            credentialMaterial: 'fake-list-cred',
            authorizedForTrading: true,
        },
        {
            id: SHADOW_RUN_ID_2,
            status: 'BLOCKED',
            strategyVersionId: 'sv-gater-8-blocked',
            datasetId: DATASET_ID,
            paperRunId: 'paper-gater-8-blocked',
            authorizationBoundary: 'DIAGNOSTIC_ONLY',
            traceId: 'trace-shadow-gater-8-blocked',
            createdAt: '2026-07-06T09:00:00Z',
            updatedAt: '2026-07-06T09:20:00Z',
            startedAt: null,
            completedAt: '2026-07-06T09:20:00Z',
            blockersCount: 1,
            warningsCount: 0,
            nextStepsCount: 1,
            noOrderSubmission: true,
            noCredentialAccess: true,
            noPrivateEndpoint: true,
            noLedgerMutation: true,
            noAccountMutation: true,
            apiKey: 'fake-list-key',
            tradeApproved: true,
        },
    ],
    limit: 50,
    offset: 0,
    total: 2,
};

interface StubOptions {
    locale?: 'zh-CN' | 'en-US';
    detailStatus?: number;
    reportStatus?: number;
    listStatus?: number;
    delayDetailMs?: number;
    delayListMs?: number;
    evidenceAvailability?: 'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE' | 'UNKNOWN';
    evidenceFreshness?: 'FRESH' | 'STALE' | 'UNKNOWN';
    listItems?: typeof listFixture.items;
    drilldown?: typeof drilldownFixture | null;
    runStatus?: string;
}

function shadowRunUrl(): string {
    return `/strategies/shadow-runs/${SHADOW_RUN_ID}`;
}

async function selectDiagnosticStatus(page: Page, status: keyof typeof zhPages.shadowList.states) {
    await page.getByRole('combobox', {name: zhPages.shadowList.status, exact: true}).press('Enter');
    await page.getByText(zhPages.shadowList.states[status], {exact: true}).last().click();
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
        window.localStorage.setItem('nq.locale', 'zh-CN');
        window.localStorage.setItem(
            'nexus-quant.console.auth',
            JSON.stringify({
                accessToken: 'shadow-run-detail-smoke-session',
                tokenType: 'Bearer',
                expiresAt: '2999-01-01T00:00:00Z',
                username: 'e2e-operator',
                roles: ['ADMIN'],
            }),
        );
    });

    page.on('request', (request) => {
        requests.push(`${request.method()} ${request.url()}`);
    });

    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) =>
        route.fulfill({status: 404, json: {code: 'NOT_FOUND'}}),
    );

    // 产品壳使用正式结构的隔离fixture；未声明的业务接口保持失败，不能用空数组伪装成功。
    await page.route('**/api/user/preferences', (route: Route) =>
        route.fulfill({
            status: 200,
            json: {
                version: 0,
                locale: options.locale ?? 'zh-CN',
                hideLoginDisclosures: true,
                watchlist: [],
                defaultMarket: null,
                notificationsReadAt: null,
            },
        }),
    );
    await page.route('**/api/user/notifications?*', (route: Route) =>
        route.fulfill({
            status: 200,
            json: {
                items: [],
                offset: 0,
                hasMore: false,
                windowLimited: false,
                checkedAt: '2026-07-06T11:00:00Z',
                scope: 'OWNED_SIM_RUN_EVENTS',
            },
        }),
    );

    await page.route('**/api/auth/me', (route: Route) =>
        route.fulfill({
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
        }),
    );

    await page.route('**/api/exchange-accounts', (route: Route) =>
        route.fulfill({
            status: 200,
            json: [],
        }),
    );

    await page.route('**/api/paper-shadow/consistency/drilldown?*', (route: Route) =>
        options.drilldown === null
            ? route.fulfill({status: 404, json: apiError(404, 'no drilldown')})
            : route.fulfill({status: 200, json: options.drilldown ?? drilldownFixture}),
    );

    await page.route('**/api/shadow-runs/overview', (route: Route) =>
        route.fulfill({
            status: 200,
            json: {
                ...overviewFixture,
                ...(options.listItems
                    ? {
                          totalRuns: options.listItems.length,
                          completedRuns: options.listItems.length,
                          blockedRuns: 0,
                          staleRuns: 0,
                      }
                    : {}),
                evidenceMetadata: {
                    ...overviewFixture.evidenceMetadata,
                    availability: options.evidenceAvailability ?? 'PARTIAL',
                    freshnessStatus: options.evidenceFreshness ?? 'UNKNOWN',
                    staleReason:
                        options.evidenceFreshness === 'FRESH'
                            ? null
                            : options.evidenceFreshness === 'STALE'
                              ? 'STALE_THRESHOLD_EXCEEDED'
                              : options.evidenceAvailability === 'UNAVAILABLE'
                                ? 'SOURCE_UNAVAILABLE'
                                : 'SOURCE_PARTIAL',
                },
            },
        }),
    );

    await page.route(/\/api\/shadow-runs(?:\?.*)?$/, async (route: Route) => {
        if (options.delayListMs) {
            await new Promise((resolve) => setTimeout(resolve, options.delayListMs));
        }
        if (options.listStatus === 500) {
            return route.fulfill({
                status: 500,
                json: apiError(500, 'shadow run list query failed'),
            });
        }
        const url = new URL(route.request().url());
        const status = url.searchParams.get('status');
        const all = options.listItems ?? listFixture.items;
        const matches = status ? all.filter((item) => item.status === status) : all;
        const limit = Number(url.searchParams.get('limit') ?? 50);
        const offset = Number(url.searchParams.get('offset') ?? 0);
        return route.fulfill({
            status: 200,
            json: {
                items: matches.slice(offset, offset + limit),
                limit,
                offset,
                total: matches.length,
            },
        });
    });

    await page.route(`**/api/shadow-runs/${SHADOW_RUN_ID}`, async (route: Route) => {
        if (options.delayDetailMs) {
            await new Promise((resolve) => setTimeout(resolve, options.delayDetailMs));
        }
        if (options.detailStatus === 404) {
            return route.fulfill({
                status: 404,
                json: apiError(404, `shadow run not found: ${SHADOW_RUN_ID}`),
            });
        }
        return route.fulfill({
            status: 200,
            json: {...detailFixture, status: options.runStatus ?? detailFixture.status},
        });
    });

    await page.route(`**/api/shadow-runs/${SHADOW_RUN_ID}/events`, (route: Route) =>
        route.fulfill({
            status: 200,
            json: eventsFixture,
        }),
    );

    await page.route(`**/api/shadow-runs/${SHADOW_RUN_ID}/snapshots`, (route: Route) =>
        route.fulfill({
            status: 200,
            json: snapshotsFixture,
        }),
    );

    await page.route(
        `**/api/shadow-runs/${SHADOW_RUN_ID}/consistency-report/latest`,
        (route: Route) => {
            if (options.reportStatus === 500) {
                return route.fulfill({
                    status: 500,
                    json: apiError(500, 'latest consistency report query failed'),
                });
            }
            return route.fulfill({status: 200, json: reportFixture});
        },
    );

    return requests;
}

function expectNoForbiddenRequests(requests: string[]): void {
    const forbiddenApiPattern =
        /start|stop|execute|rerun|approve|trade|placeOrder|cancelOrder|withdraw|transfer|credential|permission-probe|private/i;
    const forbiddenHostPattern = /okx|binance|bybit|coinbase|kraken|gate\.io/i;

    for (const requestEntry of requests) {
        const [, requestUrl] = requestEntry.split(' ');
        const hostname = new URL(requestUrl).hostname;
        expect(hostname, `forbidden real exchange host request: ${requestEntry}`).not.toMatch(
            forbiddenHostPattern,
        );
        if (requestUrl.includes('/api/shadow-runs')) {
            expect(
                requestEntry,
                `Shadow Run detail page must only call read-only API: ${requestEntry}`,
            ).toMatch(/^GET /);
            expect(
                requestUrl,
                `forbidden Shadow Run write/private API request: ${requestEntry}`,
            ).not.toMatch(forbiddenApiPattern);
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
        'fake-list-cred',
        'fake-list-key',
        'realOrderId',
        'real-order-should-not-render',
        'authorizedForTrading',
        'passphrase',
        'fake-passphrase',
        'tradingReady',
        'liveReady',
        'tradeApproved',
    ]) {
        expect(bodyText, `sensitive field/value must not render: ${forbidden}`).not.toContain(
            forbidden,
        );
    }
}

test.describe('Shadow Run detail / replay read-only view', () => {
    test.setTimeout(120_000);

    test('展示列表、status 筛选、loading/error/empty、敏感字段过滤并进入 detail', async ({
        page,
    }) => {
        const stubOptions: StubOptions = {};
        const requests = await seedAuthAndShadowRunStubs(page, stubOptions);

        await page.goto('/strategies/shadow-runs');

        const view = page.getByTestId('shadow-run-list-page');
        await expect(view).toBeVisible();
        await expect(view.getByRole('heading', {name: 'Shadow Run 列表'})).toBeVisible();
        await view.getByText(zhPages.shadowList.overviewDetails, {exact: true}).click();
        await view
            .getByRole('row')
            .filter({hasText: SHADOW_RUN_ID})
            .getByText(zhPages.shadowList.sourceDetails, {exact: true})
            .click();
        const evidenceMetadata = page.getByTestId('shadow-run-evidence-metadata');
        await expect(evidenceMetadata).toContainText('LOCAL_DB_SHADOW_FACTS');
        await expect(evidenceMetadata).toContainText(/可用性：\s*PARTIAL/);
        await expect(evidenceMetadata).toContainText(/新鲜度：\s*UNKNOWN/);
        await expect(view).toContainText('仅诊断 / 无交易授权');
        await expect(view).toContainText('禁止订单提交: true');
        await expect(view).toContainText('禁止凭证访问: true');
        await expect(view).toContainText('禁止私有接口: true');
        await expect(view).toContainText('禁止账本写入: true');
        await expect(view).toContainText('禁止账户写入: true');
        await expect(view).toContainText('sv-gater-8');
        await expect(view).toContainText(DATASET_ID);
        await expect(view).toContainText('trace-shadow-gater-8');
        // 只拦截测试页的复制提交，既不读取也不覆盖用户剪贴板；同时验证复制不会冒泡跳转。
        await page.evaluate(() => {
            const captured = window as Window & {shadowCopyValues: string[]};
            captured.shadowCopyValues = [];
            const original = document.execCommand.bind(document);
            document.execCommand = (command, showUi, value) => {
                if (command === 'copy') {
                    captured.shadowCopyValues.push(window.getSelection()?.toString() ?? '');
                    return true;
                }
                return original(command, showUi, value);
            };
        });
        await view
            .getByRole('row')
            .filter({hasText: SHADOW_RUN_ID})
            .locator('.ant-typography-copy')
            .first()
            .click();
        await expect(page).toHaveURL(/\/strategies\/shadow-runs$/);
        expect(
            await page.evaluate(
                () => (window as Window & {shadowCopyValues: string[]}).shadowCopyValues,
            ),
        ).toEqual([SHADOW_RUN_ID]);
        stubOptions.evidenceAvailability = 'UNAVAILABLE';
        await view.getByRole('button', {name: '刷新 overview'}).click();
        await expect(evidenceMetadata).toContainText(/可用性：\s*UNAVAILABLE/);
        await expect(evidenceMetadata).toContainText('无法判断新鲜度');

        stubOptions.evidenceAvailability = 'AVAILABLE';
        stubOptions.evidenceFreshness = 'STALE';
        await view.getByRole('button', {name: '刷新 overview'}).click();
        await expect(evidenceMetadata).toContainText(/新鲜度：\s*STALE/);

        stubOptions.evidenceFreshness = 'FRESH';
        await view.getByRole('button', {name: '刷新 overview'}).click();
        await expect(evidenceMetadata).toContainText(/新鲜度：\s*FRESH/);
        await expect(
            page.getByRole('button', {
                name: /start|stop|execute|rerun|approve|trade|下单|撤单|转账|提现/i,
            }),
        ).toHaveCount(0);
        await expectNoSensitiveCopy(page);
        expectNoForbiddenRequests(requests);

        requests.length = 0;
        await selectDiagnosticStatus(page, 'COMPLETED');
        await expect(view).toContainText('sv-gater-8');
        await expect(view).not.toContainText('sv-gater-8-blocked');
        expect(
            requests.some(
                (entry) => entry.includes('/api/shadow-runs') && entry.includes('status=COMPLETED'),
            ),
        ).toBeTruthy();
        expectNoForbiddenRequests(requests);

        requests.length = 0;
        await selectDiagnosticStatus(page, 'FAILED');
        await expect(view).toContainText(zhPages.shadowList.empty);
        expect(
            requests.some(
                (entry) => entry.includes('/api/shadow-runs') && entry.includes('status=FAILED'),
            ),
        ).toBeTruthy();
        expectNoForbiddenRequests(requests);

        requests.length = 0;
        stubOptions.listStatus = 500;
        await selectDiagnosticStatus(page, 'CANCELLED');
        await expect(view).toContainText('Shadow Run list 加载失败');
        await expect(view).toContainText('INTERNAL_ERROR · traceId: trace-shadow-error');
        await expect(view).not.toContainText('shadow run list query failed');
        expectNoForbiddenRequests(requests);

        requests.length = 0;
        stubOptions.listStatus = undefined;
        stubOptions.delayListMs = 2_000;
        await selectDiagnosticStatus(page, 'RUNNING');
        await expect(page.getByText('正在加载影子运行列表')).toBeVisible();
        await expect(view).toContainText(zhPages.shadowList.empty);
        expectNoForbiddenRequests(requests);

        requests.length = 0;
        stubOptions.delayListMs = undefined;
        await selectDiagnosticStatus(page, 'ALL');
        await expect(view).toContainText('sv-gater-8');
        await view.getByText('sv-gater-8').first().click();
        await expect(page).toHaveURL(new RegExp(`/strategies/shadow-runs/${SHADOW_RUN_ID}$`));
        await expect(page.getByTestId('shadow-run-detail-page')).toBeVisible();
        expectNoForbiddenRequests(requests);
    });

    test('分页与详情返回保持同一筛选、滚动和焦点，窄屏操作与对象同屏', async ({page}) => {
        await page.setViewportSize({width: 1536, height: 1024});
        const base = listFixture.items[0];
        const target = {
            ...base,
            strategyVersionId: detailFixture.strategyVersionId,
            paperRunId: detailFixture.paperRunId,
        };
        const items = Array.from({length: 60}, (_, index) => ({
            ...base,
            id: `${(index + 1).toString(16).padStart(8, '0')}-aaaa-4aaa-8aaa-aaaaaaaaaaaa`,
        }));
        const requests = await seedAuthAndShadowRunStubs(page, {listItems: [...items, target]});
        await page.goto('/strategies/shadow-runs?status=COMPLETED&page=2');
        const targetButton = page.locator(`#shadow-run-view-${SHADOW_RUN_ID}`);
        await targetButton.scrollIntoViewIfNeeded();
        const scrollBefore = await page.evaluate(() => window.scrollY);
        expect(scrollBefore).toBeGreaterThan(0);
        await targetButton.click();
        await expect(page).toHaveURL(new RegExp(`/strategies/shadow-runs/${SHADOW_RUN_ID}$`));
        await page.getByRole('button', {name: zhPages.backToShadowRuns}).click();
        await expect(page).toHaveURL(/status=COMPLETED&page=2$/);
        await expect(targetButton).toBeFocused();
        await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(scrollBefore);
        expect(
            requests.some(
                (entry) => entry.includes('status=COMPLETED') && entry.includes('offset=50'),
            ),
        ).toBe(true);

        await targetButton.click();
        await expect(page.getByTestId('shadow-run-detail-page')).toBeVisible();
        await page.goBack();
        await expect(page).toHaveURL(/status=COMPLETED&page=2$/);
        await expect(targetButton).toBeFocused();
        await page.setViewportSize({width: 390, height: 950});
        await expect(targetButton).toBeVisible();
        await expect
            .poll(() => page.evaluate(() => document.documentElement.scrollWidth))
            .toBe(390);

        requests.length = 0;
        await page.goto('/strategies/shadow-runs?status=TRADE_APPROVED&page=0');
        await expect(page.getByText(zhPages.shadowList.invalidQuery)).toBeVisible();
        expect(requests.some((entry) => /\/api\/shadow-runs(?:\?|$)/.test(entry))).toBe(false);
        expectNoForbiddenRequests(requests);
    });

    const diagnosticCases = [
        {
            key: 'no-report',
            comparison: 'NO_REPORT',
            run: 'COMPLETED',
            report: false,
            severity: 'NONE',
        },
        {key: 'failed', comparison: 'FAILED', run: 'FAILED', report: true, severity: 'NONE'},
        {key: 'blocked', comparison: 'CONSISTENT', run: 'BLOCKED', report: true, severity: 'NONE'},
        {key: 'diverged', comparison: 'DIVERGED', run: 'COMPLETED', report: true, severity: 'HIGH'},
        {
            key: 'stale',
            comparison: 'STALE_EVIDENCE',
            run: 'COMPLETED',
            report: true,
            severity: 'UNKNOWN',
        },
        {key: 'normal', comparison: 'CONSISTENT', run: 'COMPLETED', report: true, severity: 'NONE'},
    ] as const;

    for (const item of diagnosticCases)
        test(`诊断概览与一致性分区保留 ${item.key} 的真实含义`, async ({page}) => {
            const errors: string[] = [];
            page.on('pageerror', (error) => errors.push(error.message));
            const requests = await seedAuthAndShadowRunStubs(page, {
                runStatus: item.run,
                drilldown: {
                    ...drilldownFixture,
                    comparisonStatus: item.comparison,
                    divergenceSeverity: item.severity,
                    shadowRun: {...drilldownFixture.shadowRun, status: item.run},
                    latestConsistency: item.report
                        ? {
                              ...drilldownFixture.latestConsistency!,
                              comparisonStatus: item.comparison,
                          }
                        : null,
                },
            });
            await page.setViewportSize({width: 1536, height: 1024});
            await page.goto(shadowRunUrl());
            const overview = page.getByTestId('shadow-detail-overview');
            await expect(overview).toContainText(zhPages.shadowDetail.states[item.key]);
            await expect(overview).toContainText(zhPages.shadowDetail.stateHelp[item.key]);
            await expect(overview).not.toContainText('comparisonStatus');
            if (process.env.NQ_SHADOW_DETAIL_UI_EVIDENCE && item.key === 'normal')
                await page.screenshot({
                    path: `${process.env.NQ_SHADOW_DETAIL_UI_EVIDENCE}/overview-desktop.png`,
                });
            await page
                .getByRole('tab', {name: zhPages.shadowDetail.sections.consistency, exact: true})
                .click();
            const comparison = page.getByRole('region', {
                name: zhPages.paperShadowConsistencyDrilldownPanel,
            });
            await expect(comparison).toBeVisible();
            await expect(comparison).toContainText(item.comparison);
            await expectNoSensitiveCopy(page);
            expectNoForbiddenRequests(requests);
            expect(errors).toEqual([]);
        });

    test('分区深链接刷新保持选择和原参数，窄屏支持键盘切换且不溢出', async ({page}) => {
        const requests = await seedAuthAndShadowRunStubs(page);
        await page.setViewportSize({width: 390, height: 950});
        await page.goto(shadowRunUrl() + '?view=snapshots&note=keep#inspect');
        await expect(
            page.getByRole('tab', {name: zhPages.shadowDetail.sections.snapshots, exact: true}),
        ).toHaveAttribute('aria-selected', 'true');
        await expect(page.getByRole('region', {name: '影子运行快照面板'})).toBeVisible();
        await page.reload();
        const snapshotsTab = page.getByRole('tab', {
            name: zhPages.shadowDetail.sections.snapshots,
            exact: true,
        });
        await expect(snapshotsTab).toHaveAttribute('aria-selected', 'true');
        await expect(page.locator('.app-shell__drawer')).toBeHidden();
        await snapshotsTab.press('ArrowLeft');
        // 方向键聚焦后，组件会附加可访问序号；继续用实际焦点确认，不删除键盘断言。
        await expect(page.getByRole('tab', {name: /事件$/})).toBeFocused();
        await page.keyboard.press('Enter');
        await expect(page.getByRole('region', {name: '影子运行事件时间线'})).toBeVisible();
        await expect(page).toHaveURL(
            (url) =>
                url.searchParams.get('view') === 'events' &&
                url.searchParams.get('note') === 'keep' &&
                url.hash === '#inspect',
        );
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
            390,
        );
        await page
            .getByRole('tab', {name: zhPages.shadowDetail.sections.overview, exact: true})
            .click();
        await expect(page.getByTestId('shadow-detail-overview')).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
            390,
        );
        // 等待标签指示线到达已选概览，避免把过渡帧当成最终移动布局。
        await expect
            .poll(async () => {
                const tab = await page
                    .getByRole('tab', {name: zhPages.shadowDetail.sections.overview, exact: true})
                    .boundingBox();
                const ink = await page
                    .locator('.shadow-detail-workspace > .ant-tabs-nav .ant-tabs-ink-bar')
                    .boundingBox();
                return tab && ink ? Math.abs(tab.x - ink.x) : 100;
            })
            .toBeLessThan(2);
        if (process.env.NQ_SHADOW_DETAIL_UI_EVIDENCE)
            await page.screenshot({
                path: `${process.env.NQ_SHADOW_DETAIL_UI_EVIDENCE}/overview-mobile.png`,
            });
        expectNoForbiddenRequests(requests);
    });

    test('非法或重复分区不读取事实，明确重置后才读取原对象', async ({page}) => {
        const requests = await seedAuthAndShadowRunStubs(page);
        for (const search of ['?view=trade-now', '?view=events&view=report']) {
            requests.length = 0;
            await page.goto(shadowRunUrl() + search);
            await expect(page.getByText(zhPages.shadowDetail.invalidSection)).toBeVisible();
            expect(
                requests.some((request) => /\/api\/(shadow-runs|paper-shadow)\//.test(request)),
            ).toBe(false);
        }
        await page
            .getByRole('button', {name: zhPages.shadowDetail.resetSection, exact: true})
            .click();
        await expect(page.getByTestId('shadow-detail-overview')).toBeVisible();
        await expect(page).toHaveURL((url) => !url.searchParams.has('view'));
        expectNoForbiddenRequests(requests);
    });

    test('缺失或串对象的摘要不补零，不把异常能力标记说成已禁用或未实现', async ({page}) => {
        const options: StubOptions = {
            drilldown: {
                ...drilldownFixture,
                shadowRun: {...drilldownFixture.shadowRun, shadowRunId: SHADOW_RUN_ID_2},
            },
        };
        const requests = await seedAuthAndShadowRunStubs(page, options);
        await page.goto(shadowRunUrl());
        await expect(page.getByTestId('shadow-detail-overview')).toContainText(
            zhPages.shadowDetail.summaryUnavailable,
        );
        await expect(page.locator('.shadow-detail-evidence dd')).toHaveText(['—', '—']);
        await page
            .getByRole('tab', {name: zhPages.shadowDetail.sections.source, exact: true})
            .click();
        await expect(
            page.getByText(zhPages.shadowDetail.capabilities.live.unknown, {exact: true}),
        ).toBeVisible();
        options.drilldown = {
            ...drilldownFixture,
            liveDisabled: false,
            privateTradingImplemented: true,
            notTradingAuthorization: false,
        };
        await page.reload();
        await expect(
            page.getByText(zhPages.shadowDetail.capabilities.live.false, {exact: true}),
        ).toBeVisible();
        await expect(
            page.getByText(zhPages.shadowDetail.capabilities.private.true, {exact: true}),
        ).toBeVisible();
        await expect(
            page.getByText(zhPages.shadowDetail.capabilities.authority.false, {exact: true}),
        ).toBeVisible();
        await expect(
            page.getByText(zhPages.shadowDetail.capabilities.live.true, {exact: true}),
        ).toHaveCount(0);
        await expect(
            page.getByRole('button', {name: /start|stop|execute|approve|trade|下单|撤单/i}),
        ).toHaveCount(0);
        expectNoForbiddenRequests(requests);
    });

    test('直接刷新保持同一运行身份，五项读取重新发起且不发送写命令', async ({page}) => {
        const requests = await seedAuthAndShadowRunStubs(page);
        await page.goto(shadowRunUrl());
        await expect(page.getByTestId('shadow-detail-overview')).toBeVisible();
        const refresh = page.getByRole('button', {name: /刷新只读数据/});
        await expect(refresh).not.toHaveClass(/ant-btn-loading/);
        requests.length = 0;
        await refresh.click();
        await expect
            .poll(
                () =>
                    requests.filter(
                        (request) =>
                            request.startsWith('GET ') &&
                            (request.includes(`/api/shadow-runs/${SHADOW_RUN_ID}`) ||
                                request.includes('/api/paper-shadow/consistency/drilldown')),
                    ).length,
            )
            .toBe(5);
        await expect(page.getByTestId('shadow-detail-overview')).toContainText(SHADOW_RUN_ID);
        expect(requests.every((request) => request.startsWith('GET '))).toBe(true);
        expectNoForbiddenRequests(requests);
    });

    test('英文窄屏更多菜单可直接进入来源分区并恢复概览', async ({page}) => {
        const requests = await seedAuthAndShadowRunStubs(page, {locale: 'en-US'});
        await page.setViewportSize({width: 390, height: 950});
        await page.goto(shadowRunUrl());
        await expect(page.getByRole('tab', {name: 'Overview', exact: true})).toHaveAttribute(
            'aria-selected',
            'true',
        );
        await expect(page.locator('.app-shell__drawer')).toBeHidden();
        await page.locator('.shadow-detail-workspace .ant-tabs-nav-more').click();
        await page.getByRole('option', {name: 'Sources', exact: true}).click();
        const source = page.getByRole('tabpanel', {name: 'Sources', exact: true});
        await expect(source).toBeVisible();
        await expect(source).toContainText(SHADOW_RUN_ID);
        await expect(page).toHaveURL((url) => url.searchParams.get('view') === 'source');
        // 窄屏激活末尾分区后，前面的标签也会进入溢出菜单，按用户可见入口返回。
        await page.locator('.shadow-detail-workspace .ant-tabs-nav-more').click();
        await page.getByRole('option', {name: 'Overview', exact: true}).click();
        await expect(page.getByTestId('shadow-detail-overview')).toBeVisible();
        await expect(page).toHaveURL((url) => !url.searchParams.has('view'));
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
            390,
        );
        expectNoForbiddenRequests(requests);
    });

    test('大写UUID深链接仍读取同一正式身份', async ({page}) => {
        const requests = await seedAuthAndShadowRunStubs(page);
        await page.goto(shadowRunUrl().replace(SHADOW_RUN_ID, SHADOW_RUN_ID.toUpperCase()));
        await expect(page.getByTestId('shadow-detail-overview')).toContainText(SHADOW_RUN_ID);
        expect(
            requests.some((request) => request.endsWith(`/api/shadow-runs/${SHADOW_RUN_ID}`)),
        ).toBe(true);
        expectNoForbiddenRequests(requests);
    });

    test('详情响应串对象时关闭后续事实读取，不显示另一运行', async ({page}) => {
        const requests = await seedAuthAndShadowRunStubs(page);
        await page.route(`**/api/shadow-runs/${SHADOW_RUN_ID}`, (route) =>
            route.fulfill({status: 200, json: {...detailFixture, id: SHADOW_RUN_ID_2}}),
        );
        await page.goto(shadowRunUrl());
        await expect(page.getByText(zhPages.shadowDetail.identityMismatch)).toBeVisible();
        await expect(page.getByTestId('shadow-detail-overview')).toHaveCount(0);
        expect(
            requests.some((request) =>
                /\/events|\/snapshots|\/consistency-report\/latest/.test(request),
            ),
        ).toBe(false);
        expectNoForbiddenRequests(requests);
    });

    test('展示 detail、events、snapshots、latest report、404、loading/error 与只读边界', async ({
        page,
    }) => {
        const stubOptions: StubOptions = {};
        const requests = await seedAuthAndShadowRunStubs(page, stubOptions);

        await page.goto(shadowRunUrl());

        const view = page.getByTestId('shadow-run-detail-page');
        await expect(view).toBeVisible();
        await expect(view.getByRole('heading', {name: '影子运行详情 / 回放'})).toBeVisible();

        const overview = view.getByTestId('shadow-detail-overview');
        await expect(overview).toBeVisible();
        await expect(overview).toContainText(zhPages.shadowDetail.states.normal);
        await expect(overview).toContainText('sv-gater-7');
        await expect(overview).toContainText(DATASET_ID);
        await expect(page.getByRole('region', {name: '影子运行事件时间线'})).toHaveCount(0);
        await view
            .getByRole('tab', {name: zhPages.shadowDetail.sections.source, exact: true})
            .click();

        await expect(view).toContainText('LIVE 已禁用');
        await expect(view).toContainText('仅诊断');
        await expect(view).toContainText('禁止订单提交: true');
        await expect(view).toContainText('禁止凭证访问: true');
        await expect(view).toContainText('禁止私有接口: true');
        await expect(view).toContainText('禁止账本写入: true');
        await expect(view).toContainText('禁止账户写入: true');
        await expect(view).toContainText('无交易授权');

        await expect(view).toContainText('sv-gater-7');
        await expect(view).toContainText(DATASET_ID);
        await expect(view).toContainText('trace-shadow-gater-7');
        await expectNoSensitiveCopy(page);

        await view
            .getByRole('tab', {name: zhPages.shadowDetail.sections.events, exact: true})
            .click();
        const timeline = page.getByRole('region', {name: '影子运行事件时间线'});
        await expect(timeline).toBeVisible();
        await expect(timeline).toContainText('CREATED');
        await expect(timeline).toContainText('COMPLETED');
        const timelineText = await timeline.innerText();
        expect(timelineText.indexOf('CREATED')).toBeLessThan(timelineText.indexOf('COMPLETED'));
        await expectNoSensitiveCopy(page);

        await view
            .getByRole('tab', {name: zhPages.shadowDetail.sections.snapshots, exact: true})
            .click();
        const snapshots = page.getByRole('region', {name: '影子运行快照面板'});
        await expect(snapshots).toBeVisible();
        await expect(snapshots).toContainText('INPUT_MARKETDATA');
        await expect(snapshots).toContainText('ORDER_INTENT_PREVIEW');
        await expect(snapshots).toContainText('shadow-input-marketdata.v1');
        await expect(snapshots).toContainText('BTC-USDT');

        await snapshots.getByText('ORDER_INTENT_PREVIEW').first().click();
        await expect(snapshots).toContainText('shadow-order-intent-preview.v1');
        await expect(snapshots).toContainText('previewOnly');
        await expect(snapshots).toContainText('visible-safe-value');
        await expectNoSensitiveCopy(page);

        await view
            .getByRole('tab', {name: zhPages.shadowDetail.sections.report, exact: true})
            .click();
        const report = page.getByRole('region', {name: '影子一致性报告面板'});
        await expect(report).toBeVisible();
        await expect(report).toContainText('CONSISTENT');
        await expect(report).toContainText('metricDelta');
        await expect(report).toContainText('returnDelta');
        await expect(report).toContainText('divergenceReasons');
        await expect(report).toContainText('limitations');
        await expect(report).toContainText('diagnostic only');
        await expect(report).toContainText('不是 approval，不代表 trading authorization');

        await expect(
            page.getByRole('button', {
                name: /start|stop|execute|rerun|approve|trade|下单|撤单|转账|提现/i,
            }),
        ).toHaveCount(0);
        await expectNoSensitiveCopy(page);
        expectNoForbiddenRequests(requests);
        expect(
            requests.some((entry) => entry.includes(`/api/shadow-runs/${SHADOW_RUN_ID}`)),
        ).toBeTruthy();
        expect(requests.some((entry) => entry.includes('/events'))).toBeTruthy();
        expect(requests.some((entry) => entry.includes('/snapshots'))).toBeTruthy();
        expect(requests.some((entry) => entry.includes('/consistency-report/latest'))).toBeTruthy();

        requests.length = 0;
        stubOptions.detailStatus = 404;
        stubOptions.reportStatus = undefined;
        stubOptions.delayDetailMs = undefined;

        await page.goto(shadowRunUrl());

        await expect(view).toContainText('Shadow Run not found / Shadow Run 不存在');
        await expect(view).toContainText(SHADOW_RUN_ID);
        await expect(
            page.getByRole('button', {name: /start|stop|execute|rerun|approve|trade/i}),
        ).toHaveCount(0);
        expect(requests.some((entry) => entry.includes('/events'))).toBeFalsy();
        expect(requests.some((entry) => entry.includes('/snapshots'))).toBeFalsy();
        expect(requests.some((entry) => entry.includes('/consistency-report/latest'))).toBeFalsy();
        expectNoForbiddenRequests(requests);

        requests.length = 0;
        stubOptions.detailStatus = undefined;
        stubOptions.reportStatus = 500;
        stubOptions.delayDetailMs = 2_000;

        await page.goto(shadowRunUrl());

        await expect(page.getByText('正在加载影子运行详情')).toBeVisible();
        await view
            .getByRole('tab', {name: zhPages.shadowDetail.sections.source, exact: true})
            .click();
        await expect(view).toContainText('Shadow Run 基本信息');
        await view
            .getByRole('tab', {name: zhPages.shadowDetail.sections.report, exact: true})
            .click();
        await expect(view).toContainText('Consistency report 加载失败');
        await expect(view).toContainText('INTERNAL_ERROR · traceId: trace-shadow-error');
        await expect(view).not.toContainText('latest consistency report query failed');
        await expect(
            page.getByRole('button', {name: /start|stop|execute|rerun|approve|trade/i}),
        ).toHaveCount(0);
        expectNoForbiddenRequests(requests);
    });
});
