import {expect, type APIResponse, type Page} from 'playwright/test';

interface GateI2Fixture {
    authHeaders: {Authorization: string};
    datasetId: string;
    strategyCode: string;
    strategyVersionId: string;
    researchConfigId: string;
    backtestConfigId: string;
}

interface GateI2RunFixture extends GateI2Fixture {
    backtestRunId: string;
    evalReportId: string;
}

interface GateI2FixtureOptions {
    bindDataset?: boolean;
}

async function authHeaders(page: Page): Promise<{Authorization: string}> {
    const session = await page.evaluate(() => JSON.parse(
        window.localStorage.getItem('nexus-quant.console.auth') ?? '{}',
    ));
    expect(session.accessToken, 'GateI-2 smoke 需要已登录 accessToken 执行本地 fixture 准备').toBeTruthy();
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
 * 准备 GateI-2 前端 smoke 所需的最小本地数据。
 *
 * Why:
 * E2E 不能依赖外网交易所，也不能假设开发库已经存在可追溯回测配置；
 * 因此测试显式导入本地 fixture bars、创建 strategy version、research config、backtest config，
 * 再通过正式 API 绑定 strategy version 与 dataset，保证页面断言覆盖真实 HTTP 与 DB 链路。
 */
export async function prepareGateI2BacktestTraceFixture(page: Page): Promise<GateI2Fixture> {
    return prepareGateI2BacktestTraceFixtureWithOptions(page, {bindDataset: true});
}

/**
 * 准备 GateI-2 前端 smoke 所需的最小本地数据。
 *
 * Why:
 * E2E 不能依赖外网交易所，也不能假设开发库已经存在可追溯回测配置；
 * 因此测试显式导入本地 fixture bars、创建 strategy version、research config、backtest config，
 * 再通过正式 API 绑定 strategy version 与 dataset，保证页面断言覆盖真实 HTTP 与 DB 链路。
 *
 * Edge:
 * dataset API 使用 GateH-3 的内部 symbol 口径 BTC-USDT；回测执行 fixture 仍使用历史
 * marketdata_bars 中的 BTCUSDT。评估 smoke 为了验证 eval 指标链路，会跳过 dataset 绑定，
 * 避免把展示追溯用 dataset snapshot 误作为执行数据源。
 */
async function prepareGateI2BacktestTraceFixtureWithOptions(
    page: Page,
    options: GateI2FixtureOptions,
): Promise<GateI2Fixture> {
    const headers = await authHeaders(page);
    const suffix = Date.now();
    const strategyCode = `gatei2-e2e-${suffix}`;
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

    let datasetId = '';
    if (options.bindDataset ?? true) {
        const dataset = await jsonOrThrow<{datasetId: string}>(page.request.post('/api/marketdata/datasets', {
            headers,
            data: {
                datasetName: `gatei2-dataset-${suffix}`,
                exchangeCode: 'BINANCE',
                marketType: 'SPOT',
                symbol: 'BTC-USDT',
                interval: '1m',
                startTime: '2025-01-01T00:00:00Z',
                endTime: '2025-01-01T00:05:59Z',
            },
            timeout: 30_000,
        }), 'dataset create');
        await jsonOrThrow(page.request.post(`/api/marketdata/datasets/${dataset.datasetId}/refresh-quality`, {
            headers,
            timeout: 30_000,
        }), 'dataset quality refresh');
        datasetId = dataset.datasetId;
    }

    const strategy = await jsonOrThrow<{strategyId: string}>(page.request.post('/api/strategies', {
        headers,
        data: {
            strategyCode,
            strategyName: `GateI-2 E2E Strategy ${suffix}`,
            strategyType: 'BUY_AND_HOLD_FIXTURE',
            exchangeCode: 'BINANCE',
            accountId: legacyAccountId,
            tradeEnv: 'SIM',
            configSnapshot: '{"source":"gatei2-e2e"}',
        },
        timeout: 30_000,
    }), 'strategy create');

    const version = await jsonOrThrow<{strategyVersionId: string}>(page.request.post(
        `/api/strategies/${strategyCode}/versions`,
        {
            headers,
            data: {
                versionName: `GateI-2 E2E ${suffix}`,
                status: 'ACTIVE',
                paramSnapshotJson: '{"orderQuantity":"1"}',
                configSnapshotJson: '{"source":"gatei2-e2e","mode":"traceability"}',
                sourceSnapshotJson: '{"source":"playwright"}',
            },
            timeout: 30_000,
        },
    ), 'strategy version create');

    const research = await jsonOrThrow<{researchConfigId: string}>(page.request.post('/api/research-configs', {
        headers,
        data: {
            sourceStrategyId: strategy.strategyId,
            name: `GateI-2 Research ${suffix}`,
            description: 'gatei2 enhanced traceability smoke',
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
            name: `GateI-2 Backtest ${suffix}`,
            description: 'gatei2 enhanced traceability smoke',
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
    if (datasetId) {
        await jsonOrThrow(page.request.patch(`/api/backtest-configs/${backtest.backtestConfigId}/dataset`, {
            headers,
            data: {datasetId},
            timeout: 30_000,
        }), 'dataset bind');
    }

    return {
        authHeaders: headers,
        datasetId,
        strategyCode,
        strategyVersionId: version.strategyVersionId,
        researchConfigId: research.researchConfigId,
        backtestConfigId: backtest.backtestConfigId,
    };
}

export async function prepareGateI2EvaluationFixture(page: Page): Promise<GateI2RunFixture> {
    const fixture = await prepareGateI2BacktestTraceFixtureWithOptions(page, {bindDataset: false});
    const run = await jsonOrThrow<{backtestRunId: string}>(page.request.post('/api/backtest-runs', {
        headers: fixture.authHeaders,
        data: {backtestConfigId: fixture.backtestConfigId},
        timeout: 30_000,
    }), 'backtest run create');

    await jsonOrThrow(page.request.post(`/api/backtest-runs/${run.backtestRunId}/start`, {
        headers: fixture.authHeaders,
        timeout: 60_000,
    }), 'backtest run start');

    const evaluation = await jsonOrThrow<{evalReportId: string}>(page.request.post(
        `/api/backtest-runs/${run.backtestRunId}/evaluate`,
        {
            headers: fixture.authHeaders,
            timeout: 60_000,
        },
    ), 'backtest evaluation');

    return {
        ...fixture,
        backtestRunId: run.backtestRunId,
        evalReportId: evaluation.evalReportId,
    };
}
