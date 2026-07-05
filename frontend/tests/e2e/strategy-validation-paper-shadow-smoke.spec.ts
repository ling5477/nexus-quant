import {expect, test, type Page, type Route} from 'playwright/test';

const SIDE_EFFECT_POLICY = [
    {code: 'NO_DB_WRITE', status: 'FORBIDDEN', message: 'Preview must not write database state.'},
    {code: 'NO_EXTERNAL_IO', status: 'FORBIDDEN', message: 'Preview must not call external systems.'},
    {code: 'NO_CREDENTIAL_ACCESS', status: 'FORBIDDEN', message: 'Preview must not read credential material.'},
    {code: 'NO_PRIVATE_ENDPOINT', status: 'FORBIDDEN', message: 'Preview must not call private exchange endpoint.'},
    {code: 'NO_ORDER_SUBMISSION', status: 'FORBIDDEN', message: 'Preview must not submit orders.'},
    {code: 'NO_LEDGER_MUTATION', status: 'FORBIDDEN', message: 'Preview must not mutate ledger.'},
    {code: 'NO_ACCOUNT_MUTATION', status: 'FORBIDDEN', message: 'Preview must not mutate account state.'},
];

const EVALUATION_GATE_FIXTURE = {
    scope: {
        strategyId: 'strategy-gateq',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
    },
    strategyId: 'strategy-gateq',
    strategyVersionId: 'sv-gateq-5',
    datasetId: '11111111-1111-4111-8111-111111111111',
    evaluationId: 'eval-gateq-5',
    publishId: 'pub-gateq-5',
    paperRunId: 'paper-gateq-5',
    gateStatus: 'READY_FOR_SHADOW_REVIEW',
    gateDecision: 'REVIEW_ONLY',
    evaluationStatus: 'SUCCEEDED',
    datasetQualityStatus: 'SATISFIED',
    paperEvidenceStatus: 'SATISFIED',
    publishTraceStatus: 'SATISFIED',
    requiredEvidence: [
        {code: 'STRATEGY_VERSION', status: 'SATISFIED', message: 'Strategy version fact is present.'},
        {code: 'DATASET', status: 'SATISFIED', message: 'Dataset quality facts are present.'},
        {code: 'EVALUATION', status: 'SATISFIED', message: 'Evaluation report is present.'},
    ],
    missingEvidence: [],
    blockers: [],
    warnings: [
        {code: 'READONLY_BOUNDARY', severity: 'WARNING', message: 'Evaluation gate is review evidence only.'},
    ],
    nextSteps: ['Review Paper vs Shadow comparison evidence before any later gate.'],
    generatedAt: '2026-07-05T10:00:00Z',
};

const PAPER_SHADOW_FIXTURE = {
    scope: {
        strategyId: 'strategy-gateq',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
        shadowRunId: 'shadow-preview-only',
    },
    strategyId: 'strategy-gateq',
    strategyVersionId: 'sv-gateq-5',
    datasetId: '11111111-1111-4111-8111-111111111111',
    evaluationId: 'eval-gateq-5',
    publishId: 'pub-gateq-5',
    paperRunId: 'paper-gateq-5',
    shadowRunId: 'shadow-preview-only',
    paperRunStatus: 'SUCCEEDED',
    shadowRunStatus: 'NOT_IMPLEMENTED',
    comparisonStatus: 'READY_FOR_COMPARISON',
    evaluationGateStatus: 'READY_FOR_SHADOW_REVIEW',
    paperEvidenceStatus: 'SATISFIED',
    shadowEvidenceStatus: 'NOT_IMPLEMENTED',
    dataQualityStatus: 'SATISFIED',
    comparable: true,
    requiredEvidence: [
        {code: 'PAPER_EVIDENCE', status: 'SATISFIED', message: 'Paper run evidence is available.'},
        {code: 'SHADOW_FACT_SOURCE', status: 'NOT_IMPLEMENTED', message: 'Shadow fact source is not implemented.'},
    ],
    missingEvidence: [
        {code: 'SHADOW_RUN_FACTS', status: 'NOT_IMPLEMENTED', message: 'Shadow run facts are not available.'},
    ],
    blockers: [],
    warnings: [
        {code: 'SHADOW_NOT_IMPLEMENTED', severity: 'WARNING', message: 'Shadow runner remains not implemented.'},
    ],
    nextSteps: ['Keep comparison read-only until a later approved Shadow fact source exists.'],
    generatedAt: '2026-07-05T10:01:00Z',
};

const SHADOW_PREVIEW_FIXTURE = {
    scope: {
        strategyId: 'strategy-gateq',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
        shadowRunId: 'shadow-preview-only',
    },
    strategyId: 'strategy-gateq',
    strategyVersionId: 'sv-gateq-5',
    datasetId: '11111111-1111-4111-8111-111111111111',
    evaluationId: 'eval-gateq-5',
    publishId: 'pub-gateq-5',
    paperRunId: 'paper-gateq-5',
    shadowRunId: 'shadow-preview-only',
    runnerStatus: 'SKELETON_AVAILABLE',
    previewStatus: 'PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE',
    evaluationGateStatus: 'READY_FOR_SHADOW_REVIEW',
    paperShadowComparisonStatus: 'READY_FOR_COMPARISON',
    sideEffectPolicy: SIDE_EFFECT_POLICY,
    inputFactStatus: 'PARTIAL',
    traceStatus: 'PARTIAL',
    orderIntentPreviewStatus: 'NOT_EXECUTED',
    riskPreflightPreviewStatus: 'NOT_EXECUTED',
    requiredEvidence: [
        {code: 'TRACE_CHAIN', status: 'SATISFIED', message: 'Trace chain is visible.'},
        {code: 'SHADOW_FACTS', status: 'NOT_AVAILABLE', message: 'Shadow facts are not available.'},
    ],
    missingEvidence: [
        {
            code: 'SHADOW_FACTS',
            status: 'NOT_AVAILABLE',
            message: 'Shadow run facts are required before preview review.'
        },
    ],
    blockers: [
        {
            code: 'PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE',
            severity: 'BLOCKER',
            message: 'Shadow facts are not available.'
        },
    ],
    warnings: [
        {code: 'NO_SIDE_EFFECT_ONLY', severity: 'WARNING', message: 'Preview cannot execute strategy logic.'},
    ],
    nextSteps: ['Provide approved Shadow read-only facts in a later gate before preview review.'],
    generatedAt: '2026-07-05T10:02:00Z',
};

const UNKNOWN_GATE_FIXTURE = {
    ...EVALUATION_GATE_FIXTURE,
    gateStatus: 'UNKNOWN',
    gateDecision: 'NOT_AVAILABLE',
    evaluationStatus: 'UNKNOWN',
    datasetQualityStatus: 'UNKNOWN',
    requiredEvidence: [
        {code: 'STRATEGY_VERSION', status: 'UNKNOWN', message: 'Strategy version fact is unknown.'},
    ],
    missingEvidence: [
        {code: 'DATASET', status: 'NOT_AVAILABLE', message: 'Dataset is not available.'},
    ],
    blockers: [
        {code: 'DATASET_MISSING', severity: 'BLOCKER', message: 'Dataset fact is missing.'},
    ],
    warnings: [],
    nextSteps: ['Resolve missing dataset facts before review.'],
};

async function seedAuthAndGateQStubs(
    page: Page,
    overrides: {
        evaluationGate?: Record<string, unknown>;
        paperShadow?: Record<string, unknown>;
        preview?: Record<string, unknown>;
    } = {},
): Promise<string[]> {
    const requests: string[] = [];

    // Why: GateQ-5 smoke 只验证前端只读展示，不启动后端、不外联、不读取真实 credential。
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'strategy-validation-smoke-session',
            tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z',
            username: 'e2e-operator',
            roles: ['ADMIN'],
        }));
    });

    page.on('request', (request) => requests.push(request.url()));

    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) => route.fulfill({status: 200, json: []}));

    await page.route('**/api/auth/me', (route: Route) => route.fulfill({
        status: 200,
        json: {
            userId: 1,
            username: 'e2e-operator',
            roles: ['ADMIN'],
            authenticated: true,
            defaultExchangeAccountId: 101,
            defaultExchangeCode: 'BINANCE',
            defaultTradeEnv: 'SIM',
            defaultAccountAlias: 'strategy-validation-smoke',
        },
    }));

    await page.route('**/api/exchange-accounts', (route: Route) => route.fulfill({
        status: 200,
        json: [{
            exchangeAccountId: 101,
            legacyAccountId: null,
            exchangeCode: 'BINANCE',
            tradeEnv: 'SIM',
            accountAlias: 'strategy-validation-smoke',
            externalAccountRef: null,
            isDefault: true,
            status: 'ACTIVE',
        }],
    }));

    await page.route('**/api/strategies/evaluation-gate**', (route: Route) => route.fulfill({
        status: 200,
        json: overrides.evaluationGate ?? EVALUATION_GATE_FIXTURE,
    }));

    await page.route('**/api/strategies/paper-shadow/comparison**', (route: Route) => route.fulfill({
        status: 200,
        json: overrides.paperShadow ?? PAPER_SHADOW_FIXTURE,
    }));

    await page.route('**/api/strategies/shadow-live/preview**', (route: Route) => route.fulfill({
        status: 200,
        json: overrides.preview ?? SHADOW_PREVIEW_FIXTURE,
    }));

    return requests;
}

function validationUrl(): string {
    const params = new URLSearchParams({
        strategyId: 'strategy-gateq',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
        shadowRunId: 'shadow-preview-only',
    });
    return `/strategies/validation?${params.toString()}`;
}

function expectNoForbiddenCopy(page: Page) {
    return expect(page.locator('body')).not.toContainText(/LIVE READY|TRADE_APPROVED|TRADE APPROVED|authorizedForTrading|tradingReady|liveReady|SHADOW LIVE TRADING ENABLED|REAL PROVIDER ENABLED|PRIVATE TRADING ENABLED|REAL PERMISSION PROBE ENABLED|AI STARTED|DH INTEGRATED|Integration-1 RUNTIME STARTED|placeOrder|cancelOrder|withdraw|transfer|apiKey|secret|passphrase|token|private key|ML_READY|PYTHON ML READY|PYTHON LIVE READY/i);
}

function expectNoForbiddenRequests(requests: string[]): void {
    const forbiddenApiPattern = /credential|permission-probe|withdraw|transfer|\/order|\/cancel|\/amend|wallet|subaccount|\/private|listenKey/i;
    const forbiddenHostPattern = /okx|binance|bybit|coinbase|kraken|gate\.io/i;

    for (const requestUrl of requests) {
        const hostname = new URL(requestUrl).hostname;
        expect(requestUrl, `forbidden private/credential API request: ${requestUrl}`).not.toMatch(forbiddenApiPattern);
        expect(hostname, `forbidden real exchange host request: ${requestUrl}`).not.toMatch(forbiddenHostPattern);
    }
}

async function expectNoSuccessTagForStatuses(page: Page, statuses: string[]): Promise<void> {
    for (const status of statuses) {
        await expect(page.locator('.ant-tag-success').filter({hasText: status})).toHaveCount(0);
    }
}

test.describe('strategy validation Paper / Shadow comparison view', () => {
    test('展示 evaluation gate、comparison、preview、blockers、nextSteps 与 sideEffectPolicy', async ({page}) => {
        const requests = await seedAuthAndGateQStubs(page);

        await page.goto(validationUrl());

        const view = page.getByTestId('strategy-validation-page');
        await expect(view).toBeVisible();
        await expect(view).toContainText('策略生命周期追溯与 Paper / Shadow 对照');
        await expect(view).toContainText('只读验证');
        await expect(view).toContainText('不代表交易授权');
        await expect(view).toContainText('不代表 LIVE 已启用');
        await expect(view).toContainText('不提交真实订单');
        await expect(view).toContainText('不读取真实凭证');
        await expect(view).toContainText('不调用 private endpoint');
        await expect(view).toContainText('不写真实账户 / 资金 / ledger');
        await expect(view).toContainText('不接 AI / DH runtime 执行链路');

        await expect(view).toContainText('状态解释');
        await expect(view).toContainText('VALID_FOR_BINDING_PREVIEW');
        await expect(view).toContainText('UNKNOWN / NOT_AVAILABLE / NOT_IMPLEMENTED / BLOCKED_*');
        await expect(view).toContainText('生命周期追溯链');
        await expect(view).toContainText('strategyVersion -> dataset -> evaluation -> publish -> paper -> shadow');
        await expect(view).toContainText('Strategy Version');
        await expect(view).toContainText('Dataset');
        await expect(view).toContainText('Evaluation Gate');
        await expect(view).toContainText('Publish Trace');
        await expect(view).toContainText('Paper Run');
        await expect(view).toContainText('Paper / Shadow Comparison');
        await expect(view).toContainText('Shadow Live Preview');
        await expect(view).toContainText('Python Artifact Binding Preview');
        await expect(view).toContainText('PENDING_FRONTEND_SUPPORT（等待前端接入支持）');
        await expect(view).toContainText('NOT_CONNECTED');

        await expect(view).toContainText('Evidence Matrix / 证据矩阵');
        await expect(view).toContainText('requiredEvidence');
        await expect(view).toContainText('missingEvidence');
        await expect(view).toContainText('blockers');
        await expect(view).toContainText('warnings');
        await expect(view).toContainText('nextSteps');

        await expect(view).toContainText('READY_FOR_SHADOW_REVIEW（可进入 Shadow 评审）');
        await expect(view).toContainText('READY_FOR_COMPARISON（可查看只读对照）');
        await expect(view).toContainText('PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE（Shadow facts 不可用）');
        await expect(view).toContainText('NOT_IMPLEMENTED（能力未实现）');
        await expect(view).toContainText('PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE');
        await expect(view).toContainText('Provide approved Shadow read-only facts');

        for (const policy of SIDE_EFFECT_POLICY) {
            await expect(view).toContainText(policy.code);
            await expect(view).toContainText(policy.status);
        }

        await expect(view).toContainText('strategyVersion');
        await expect(view).toContainText('dataset');
        await expect(view).toContainText('evaluation');
        await expect(view).toContainText('publish');
        await expect(view).toContainText('paper');
        await expect(view).toContainText('shadow');

        await expectNoForbiddenCopy(page);
        await expectNoSuccessTagForStatuses(page, ['NOT_IMPLEMENTED', 'NOT_AVAILABLE', 'UNKNOWN', 'PENDING_FRONTEND_SUPPORT']);
        expectNoForbiddenRequests(requests);
        expect(requests.some((url) => url.includes('/api/strategies/evaluation-gate'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/strategies/paper-shadow/comparison'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/strategies/shadow-live/preview'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/research/evaluation-artifacts'))).toBeFalsy();
    });

    test('UNKNOWN / NOT_AVAILABLE 不显示为成功态', async ({page}) => {
        const requests = await seedAuthAndGateQStubs(page, {
            evaluationGate: UNKNOWN_GATE_FIXTURE,
            paperShadow: {
                ...PAPER_SHADOW_FIXTURE,
                comparisonStatus: 'NOT_AVAILABLE',
                evaluationGateStatus: 'UNKNOWN',
                comparable: false,
                requiredEvidence: [],
                missingEvidence: [
                    {code: 'PAPER_EVIDENCE', status: 'NOT_AVAILABLE', message: 'Paper evidence is not available.'},
                ],
                blockers: [
                    {code: 'PAPER_EVIDENCE_MISSING', severity: 'BLOCKER', message: 'Paper evidence is missing.'},
                ],
            },
            preview: {
                ...SHADOW_PREVIEW_FIXTURE,
                previewStatus: 'UNKNOWN',
                evaluationGateStatus: 'UNKNOWN',
                paperShadowComparisonStatus: 'NOT_AVAILABLE',
                requiredEvidence: [],
                missingEvidence: [
                    {code: 'TRACE_CHAIN', status: 'UNKNOWN', message: 'Trace chain is unknown.'},
                ],
                blockers: [
                    {code: 'UNKNOWN_INPUT_FACTS', severity: 'BLOCKER', message: 'Input facts are unknown.'},
                ],
            },
        });

        await page.goto(validationUrl());

        const view = page.getByTestId('strategy-validation-page');
        await expect(view).toContainText('UNKNOWN（未知）');
        await expect(view).toContainText('NOT_AVAILABLE（不可用）');
        await expect(view).toContainText('查询结果不是通过态');
        await expect(view).toContainText('Resolve missing dataset facts before review.');
        await expect(view).not.toContainText('READY_FOR_SHADOW_REVIEW（可进入 Shadow 评审）');
        await expect(view).not.toContainText('READY_FOR_NO_SIDE_EFFECT_PREVIEW（可生成无副作用预览）');

        await expectNoForbiddenCopy(page);
        await expectNoSuccessTagForStatuses(page, ['UNKNOWN', 'NOT_AVAILABLE', 'NOT_IMPLEMENTED']);
        expectNoForbiddenRequests(requests);
    });
});
