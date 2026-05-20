import {expect, type APIResponse, type Page} from 'playwright/test';

interface PaperTradingFixture {
    authHeaders: {Authorization: string};
    publishId: string;
    backtestRunId: string;
    strategyVersionId: string;
}

async function authHeaders(page: Page): Promise<{Authorization: string}> {
    const session = await page.evaluate(() => JSON.parse(
        window.localStorage.getItem('nexus-quant.console.auth') ?? '{}',
    ));
    expect(session.accessToken, 'GateI-3 smoke 需要已登录 accessToken 执行本地 fixture 准备').toBeTruthy();
    return {
        Authorization: `${session.tokenType ?? 'Bearer'} ${session.accessToken}`,
    };
}

async function jsonOrThrow<T>(responsePromise: Promise<APIResponse>, label: string): Promise<T> {
    const response = await responsePromise;
    if (!response.ok()) {
        throw new Error(`${label} failed: ${response.status()} ${await response.text()}`);
    }
    return await response.json() as T;
}

/**
 * 准备 GateI-3 Paper Trading smoke 所需的最小本地数据：
 * 沿用 GateI-2 fixture 路径，再创建 backtest run、evaluate、发布，得到一个可创建 Paper run 的 publishId。
 */
export async function prepareGateI3PaperTradingFixture(page: Page): Promise<PaperTradingFixture> {
    const headers = await authHeaders(page);
    const suffix = Date.now();
    const strategyCode = `gatei3-e2e-${suffix}`;
    const legacyAccountId = Number(process.env.E2E_STRATEGY_ACCOUNT_ID ?? 3001);

    await jsonOrThrow(page.request.post('/api/marketdata/bars/ingestions/fixture', {
        headers,
        data: {
            fixtureId: 'BINANCE_BTCUSDT_1M_SAMPLE',
            exchangeCode: 'BINANCE',
            symbol: 'BTCUSDT',
            interval: '1m',
            startTime: '2025-01-01T00:00:00Z',
            endTime: '2025-01-01T00:05:59Z',
        },
        timeout: 30_000,
    }), 'fixture ingest');

    const strategy = await jsonOrThrow<{strategyId: string}>(page.request.post('/api/strategies', {
        headers,
        data: {
            strategyCode,
            strategyName: `GateI-3 E2E Strategy ${suffix}`,
            strategyType: 'BUY_AND_HOLD_FIXTURE',
            exchangeCode: 'BINANCE',
            accountId: legacyAccountId,
            tradeEnv: 'SIM',
            configSnapshot: '{"source":"gatei3-e2e"}',
        },
        timeout: 30_000,
    }), 'strategy create');

    const version = await jsonOrThrow<{strategyVersionId: string}>(page.request.post(
        `/api/strategies/${strategyCode}/versions`,
        {
            headers,
            data: {
                versionName: `GateI-3 E2E ${suffix}`,
                status: 'ACTIVE',
                paramSnapshotJson: '{"orderQuantity":"1"}',
                configSnapshotJson: '{"source":"gatei3-e2e","mode":"paper"}',
                sourceSnapshotJson: '{"source":"playwright"}',
            },
            timeout: 30_000,
        },
    ), 'strategy version create');

    const research = await jsonOrThrow<{researchConfigId: string}>(page.request.post('/api/research-configs', {
        headers,
        data: {
            sourceStrategyId: strategy.strategyId,
            name: `GateI-3 Research ${suffix}`,
            description: 'gatei3 paper trading smoke',
            parameterSchema: '{}',
            parameterDefaults: '{}',
            datasetSpec: JSON.stringify({
                provider: 'db',
                datasetId: 'BINANCE_BTCUSDT_1M_SAMPLE',
                exchangeCode: 'BINANCE',
                symbol: 'BTCUSDT',
                interval: '1m',
                resourcePath: 'marketdata_bars',
            }),
        },
        timeout: 30_000,
    }), 'research config create');

    const backtest = await jsonOrThrow<{backtestConfigId: string}>(page.request.post('/api/backtest-configs', {
        headers,
        data: {
            researchConfigId: research.researchConfigId,
            name: `GateI-3 Backtest ${suffix}`,
            description: 'gatei3 paper trading smoke',
            startTime: '2025-01-01T00:00:00Z',
            endTime: '2025-01-01T00:05:59Z',
            initialCapital: 100000,
            executionSpec: '{"mode":"bar","feeRate":"0.001","slippageBps":"10","orderQuantity":"1"}',
            evaluationSpec: '{}',
        },
        timeout: 30_000,
    }), 'backtest config create');

    await jsonOrThrow(page.request.patch(`/api/backtest-configs/${backtest.backtestConfigId}/strategy-version`, {
        headers,
        data: {strategyVersionId: version.strategyVersionId},
        timeout: 30_000,
    }), 'strategy version bind');

    const run = await jsonOrThrow<{backtestRunId: string}>(page.request.post('/api/backtest-runs', {
        headers,
        data: {backtestConfigId: backtest.backtestConfigId},
        timeout: 30_000,
    }), 'backtest run create');

    await jsonOrThrow(page.request.post(`/api/backtest-runs/${run.backtestRunId}/start`, {
        headers,
        timeout: 60_000,
    }), 'backtest run start');

    await jsonOrThrow(page.request.post(`/api/backtest-runs/${run.backtestRunId}/evaluate`, {
        headers,
        timeout: 60_000,
    }), 'backtest evaluation');

    const publish = await jsonOrThrow<{publishRecordId: string}>(page.request.post('/api/publishes', {
        headers,
        params: {backtestRunId: run.backtestRunId},
        data: {strategyVersionId: version.strategyVersionId, displayName: `GateI-3 Publish ${suffix}`},
        timeout: 30_000,
    }), 'publish create');

    return {
        authHeaders: headers,
        publishId: publish.publishRecordId,
        backtestRunId: run.backtestRunId,
        strategyVersionId: version.strategyVersionId,
    };
}
