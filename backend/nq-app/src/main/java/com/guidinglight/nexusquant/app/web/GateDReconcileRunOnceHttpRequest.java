package com.guidinglight.nexusquant.app.web;

/**
 * GateDReconcileRunOnceHttpRequest 描述 GateD 本地验收触发一次 reconcile 的请求体。
 *
 * @param venue 目标 venue；为空时默认 OKX
 * @param limit 本次扫描上限；为空时使用服务默认值
 */
public record GateDReconcileRunOnceHttpRequest(
        String venue,
        Integer limit
) {
}
