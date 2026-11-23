import {createRecoverably} from '@/features/research/creation-operations';
import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
// 费用原币换算和优惠币余额也属于金融事实，必须在JSON解析前保留词法精度。
const exactResponse = {
    transformResponse: [
        (text: string) =>
            parseExactJson(
                text,
                new Set([
                    'initialCapital',
                    'cashBalance',
                    'positionMarketValue',
                    'realizedPnl',
                    'unrealizedPnl',
                    'totalFee',
                    'totalSlippage',
                    'equity',
                    'netPnl',
                    'quantity',
                    'tradePrice',
                    'feeAmount',
                    'feeNativeAmount',
                    'slippageAmount',
                    'feeConversionPrice',
                    'discountAssetBalance',
                    'discountAssetQuoteValue',
                    'discountConversionPrice',
                ]),
            ),
    ],
};
import type {
    BacktestConfigCreateRequest,
    BacktestConfigListItem,
    BacktestDatasetBindingRequest,
    BacktestRunCreateRequest,
    BacktestRunDetailItem,
    BacktestStrategyVersionBindingRequest,
    SimPnlSnapshotItem,
    BacktestSimTrade,
    BacktestConfigPage,
    BacktestPageRequest,
} from '@/features/backtests/backtestsTypes';

/** 拒绝错页、损坏身份与不完整结果；费用及资金字段仍由精确JSON解析器保留。 */
export function validateBacktestPage(
    value: unknown,
    request: BacktestPageRequest,
): BacktestConfigPage {
    if (!value || typeof value !== 'object') throw new Error('Invalid backtest page response');
    const page = value as Partial<BacktestConfigPage>;
    if (
        !Array.isArray(page.items) ||
        !Number.isSafeInteger(page.total) ||
        page.total! < 0 ||
        page.limit !== request.limit ||
        page.offset !== request.offset ||
        page.items.length !== Math.min(request.limit, Math.max(0, page.total! - request.offset))
    ) {
        throw new Error('Invalid backtest page window');
    }
    const ids = new Set<string>();
    for (const row of page.items) {
        if (
            !row ||
            typeof row.backtestConfigId !== 'string' ||
            !row.backtestConfigId.trim() ||
            typeof row.researchConfigId !== 'string' ||
            !row.researchConfigId.trim() ||
            typeof row.name !== 'string' ||
            !Number.isFinite(Date.parse(row.createdAt)) ||
            ids.has(row.backtestConfigId)
        )
            throw new Error('Invalid backtest page item');
        ids.add(row.backtestConfigId);
    }
    return page as BacktestConfigPage;
}

export const backtestsApi = {
    async page(request: BacktestPageRequest, signal?: AbortSignal): Promise<BacktestConfigPage> {
        const {data} = await apiClient.get<unknown>('/backtest-configs/page', {
            ...exactResponse,
            params: request,
            signal,
        });
        return validateBacktestPage(data, request);
    },
    async list(researchConfigId?: string): Promise<BacktestConfigListItem[]> {
        const {data} = await apiClient.get<BacktestConfigListItem[]>('/backtest-configs', {
            ...exactResponse,
            params: researchConfigId ? {researchConfigId} : undefined,
        });
        return data;
    },
    async detail(configId: string): Promise<BacktestConfigListItem> {
        const {data} = await apiClient.get<BacktestConfigListItem>(
            `/backtest-configs/${configId}`,
            exactResponse,
        );
        return data;
    },
    async create(request: BacktestConfigCreateRequest): Promise<BacktestConfigListItem> {
        return createRecoverably('BACKTEST', request, (receipt) =>
            backtestsApi.detail(receipt.objectId),
        );
    },
    /** 归档仅改变配置生命周期，响应中的资金字段继续使用精确解析。 */
    async archive(configId: string, archiveReason?: string): Promise<BacktestConfigListItem> {
        const {data} = await apiClient.post<BacktestConfigListItem>(
            `/backtest-configs/${encodeURIComponent(configId)}/archive`,
            {archiveReason},
            exactResponse,
        );
        return data;
    },
    async bindDataset(
        configId: string,
        request: BacktestDatasetBindingRequest,
    ): Promise<BacktestConfigListItem> {
        const {data} = await apiClient.patch<BacktestConfigListItem>(
            `/backtest-configs/${configId}/dataset`,
            request,
            exactResponse,
        );
        return data;
    },
    async bindStrategyVersion(
        configId: string,
        request: BacktestStrategyVersionBindingRequest,
    ): Promise<BacktestConfigListItem> {
        const {data} = await apiClient.patch<BacktestConfigListItem>(
            `/backtest-configs/${configId}/strategy-version`,
            request,
            exactResponse,
        );
        return data;
    },
    async createRun(request: BacktestRunCreateRequest): Promise<BacktestRunDetailItem> {
        return createRecoverably('BACKTEST_RUN', request, (receipt) =>
            backtestsApi.getRun(receipt.objectId),
        );
    },
    async getRun(runId: string): Promise<BacktestRunDetailItem> {
        const {data} = await apiClient.get<BacktestRunDetailItem>(`/backtest-runs/${runId}`);
        return data;
    },
    async startRun(runId: string): Promise<BacktestRunDetailItem> {
        const {data} = await apiClient.post<BacktestRunDetailItem>(
            `/backtest-runs/${encodeURIComponent(runId)}/start`,
        );
        return data;
    },
    async listRuns(backtestConfigId: string): Promise<BacktestRunDetailItem[]> {
        const {data} = await apiClient.get<BacktestRunDetailItem[]>('/backtest-runs', {
            params: {backtestConfigId},
        });
        return data;
    },
    async pnlSnapshots(runId: string): Promise<SimPnlSnapshotItem[]> {
        // 回测权益/PnL 时间序列;复用既有 run-level 端点,无新增后端 API。按 snapshotTime 升序返回。
        const {data} = await apiClient.get<SimPnlSnapshotItem[]>(
            `/backtest-runs/${runId}/pnl-snapshots`,
            exactResponse,
        );
        return data;
    },
    /** 同一运行的正式研究成交；只读且可取消，事实金额使用十进制字符串。 */
    async simTrades(runId: string, signal?: AbortSignal): Promise<BacktestSimTrade[]> {
        const {data} = await apiClient.get<BacktestSimTrade[]>(
            `/backtest-runs/${encodeURIComponent(runId)}/sim-trades`,
            {...exactResponse, signal},
        );
        return data;
    },
};
