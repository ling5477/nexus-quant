/**
 * Adapter readiness 只读视图类型，对应后端 GateM-5A `GET /api/adapters/readiness`。
 *
 * Why:
 * 前端只消费后端静态 readiness 决策用于展示；当前 baseline 下所有真实交易能力 `allowed=false`，
 * 不代表任何 venue 可真实交易。这些类型只承载可展示、可脱敏字段，不含 credential / raw payload。
 */
export interface AdapterReadinessItem {
    venue: string;
    capability: string;
    status: string;
    allowed: boolean;
    liveAuthorized: boolean;
    reasons: string[];
    message: string;
}

export interface AdapterReadinessResponse {
    generatedAt: string;
    items: AdapterReadinessItem[];
}
