import {apiClient} from '@/api/client';
import {
    originalResult,
    submitResultAction,
    type OriginalResultReceipt,
} from '@/features/research-result-recovery';
import {parseExactJson} from '@/api/exact-json';
import {validateResultPage} from '@/api/result-page';
const decimals = new Set([
    'initialCapital',
    'finalCashBalance',
    'finalPositionMarketValue',
    'finalEquity',
    'realizedPnl',
    'unrealizedPnl',
    'netPnl',
    'totalReturnRate',
    'totalReturn',
    'annualizedReturn',
    'totalFee',
    'totalSlippage',
    'winRate',
    'maxDrawdown',
    'maxDrawdownRate',
    'profitLossRatio',
    'sharpeRatio',
    'startingEquity',
    'strategyReturn',
    'fee',
    'feeNativeAmount',
    'slippage',
    'feeRate',
    'slippageBps',
    'requestedInitialCapital',
    'entryPrice',
    'endPrice',
    'quantity',
    'benchmarkReturn',
    'strategyVsBenchmarkDifference',
]);
const exactResponse = {transformResponse: [(text: string) => parseExactJson(text, decimals)]};
import type {
    BacktestEvaluationDetailItem,
    BacktestEvaluationListItem,
    EvaluationPage,
    EvaluationPageRequest,
} from '@/types/evaluations';

export function validateEvaluationPage(
    value: unknown,
    request: EvaluationPageRequest,
): EvaluationPage {
    return validateResultPage<BacktestEvaluationListItem>(value, request, (value) => {
        if (!value || typeof value !== 'object') return null;
        const row = value as Partial<BacktestEvaluationListItem>;
        return typeof row.evalReportId === 'string' &&
            Boolean(row.evalReportId.trim()) &&
            typeof row.backtestRunId === 'string' &&
            Boolean(row.backtestRunId.trim()) &&
            ['SUCCEEDED', 'FAILED'].includes(row.evaluationStatus ?? '')
            ? row.evalReportId
            : null;
    });
}

interface EvaluationListRequest {
    researchConfigId?: string;
    backtestConfigId?: string;
}

export const evaluationsApi = {
    async page(request: EvaluationPageRequest, signal?: AbortSignal): Promise<EvaluationPage> {
        const {data} = await apiClient.get<unknown>('/evaluations/page', {
            ...exactResponse,
            params: request,
            signal,
        });
        return validateEvaluationPage(data, request);
    },
    async forRun(runId: string): Promise<BacktestEvaluationDetailItem> {
        const {data} = await apiClient.get<BacktestEvaluationDetailItem>(
            `/backtest-runs/${runId}/evaluation`,
            exactResponse,
        );
        return data;
    },
    async list(request: EvaluationListRequest): Promise<BacktestEvaluationListItem[]> {
        const {data} = await apiClient.get<BacktestEvaluationListItem[]>('/evaluations', {
            ...exactResponse,
            params: request,
        });
        return data;
    },
    async detail(evaluationId: string): Promise<BacktestEvaluationDetailItem> {
        const {data} = await apiClient.get<BacktestEvaluationDetailItem>(
            `/evaluations/${evaluationId}`,
            exactResponse,
        );
        return data;
    },
    async requestResult(
        requestId: string,
    ): Promise<OriginalResultReceipt<BacktestEvaluationDetailItem>> {
        const {data} = await apiClient.get<OriginalResultReceipt<BacktestEvaluationDetailItem>>(
            `/backtest-evaluation-requests/${encodeURIComponent(requestId)}`,
            exactResponse,
        );
        return data;
    },
    async requestReport(requestId: string): Promise<BacktestEvaluationDetailItem> {
        const receipt = await evaluationsApi.requestResult(requestId);
        const report = originalResult(receipt, requestId);
        if (
            report.backtestRunId !== receipt.target ||
            report.evaluationStatus !== receipt.state ||
            !report.evalReportId
        )
            throw new Error('EVALUATION_RECEIPT_MISMATCH');
        return {...report, evaluationRequestId: receipt.requestId};
    },
    async evaluate(runId: string, requestId?: string): Promise<BacktestEvaluationDetailItem> {
        if (!requestId)
            return submitResultAction('EVALUATE', runId, (id) =>
                evaluationsApi.evaluate(runId, id),
            );
        const {data} = await apiClient.post<OriginalResultReceipt<BacktestEvaluationDetailItem>>(
            `/backtest-evaluation-requests/${encodeURIComponent(requestId)}`,
            undefined,
            {...exactResponse, params: {target: runId}},
        );
        const report = originalResult(data, requestId, runId);
        if (
            report.backtestRunId !== runId ||
            report.evaluationStatus !== data.state ||
            !report.evalReportId
        )
            throw new Error('EVALUATION_RECEIPT_MISMATCH');
        return {...report, evaluationRequestId: data.requestId};
    },
};
