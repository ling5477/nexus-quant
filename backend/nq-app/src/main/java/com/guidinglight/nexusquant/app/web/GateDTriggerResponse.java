package com.guidinglight.nexusquant.app.web;

/**
 * GateDTriggerResponse 统一描述 GateD 本地验收入口的触发结果。
 * <p>
 * Why:
 * 验收入口的职责是触发服务并返回最小可观测结果，因此响应只暴露 traceId、动作名与结果摘要，
 * 不在 controller 层复制业务实体。
 *
 * @param action  触发动作
 * @param traceId 本次调用 traceId
 * @param detail  最小执行摘要
 */
public record GateDTriggerResponse(
        String action,
        String traceId,
        String detail
) {
}
