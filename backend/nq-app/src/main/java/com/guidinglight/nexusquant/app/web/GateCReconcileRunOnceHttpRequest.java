package com.guidinglight.nexusquant.app.web;

/**
 * GateCReconcileRunOnceHttpRequest 描述本地验收触发一次 reconcile 的最小请求体。
 *
 * @param venue 目标 venue；为空时默认 OKX，用于兼容既有 GateC-1 脚本
 * @param limit 本次扫描上限；为空时使用服务默认值
 */
public record GateCReconcileRunOnceHttpRequest(
        String venue,
        Integer limit
) {
}
