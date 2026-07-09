import {apiClient} from '@/api/client';
import type {PythonEvaluationArtifactPreviewOverviewResponse} from '@/types/evaluation-artifact-preview';

/**
 * 读取 GateT-4 Python Evaluation Artifact Preview overview。
 *
 * Why:
 * 该 client 只发起 GET 请求，复用统一 Axios 实例，不提供 upload / import / bind /
 * validate-file / execute / Python subprocess、start / stop / trade、placeOrder /
 * cancelOrder / withdraw / transfer 或任何 private exchange 请求，避免前端形成文件读取、
 * Python 执行、写侧或交易能力。
 */
export async function getEvaluationArtifactPreviewOverview(): Promise<PythonEvaluationArtifactPreviewOverviewResponse> {
    const {data} = await apiClient.get<PythonEvaluationArtifactPreviewOverviewResponse>(
        '/strategy-validation/evaluation-artifacts/preview/overview',
    );
    return data;
}

export const evaluationArtifactPreviewApi = {
    getEvaluationArtifactPreviewOverview,
};
