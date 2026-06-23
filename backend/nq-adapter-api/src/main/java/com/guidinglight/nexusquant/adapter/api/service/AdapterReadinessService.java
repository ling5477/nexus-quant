package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCapability;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessDecision;

/**
 * AdapterReadinessService 在运行时回答“某 venue 的某 capability 现在能不能用”。
 * <p>
 * Why:
 * GateM-0 把 GateL 的 No-Real 文档边界落成运行时约束。交易入口、marketdata 入口或上层服务
 * 可以先调用本 service 做 service-level fail-closed guard，而不必立刻新增 HTTP API 或大改主链路。
 * 任何真实交易能力默认不可用；只有未来另起 Gate 接入真实 provider / credential / LIVE 授权后才可能变化。
 *
 * <p>线程安全：实现必须无状态或不可变，可被多线程并发调用。
 */
public interface AdapterReadinessService {

    /**
     * 评估某 venue 的某能力是否就绪。
     *
     * @param venue      统一交易所 / adapter 标识（如 OKX / BINANCE / NOOP）；null / 未知按 fail-closed 处理
     * @param capability 被评估的能力维度；null 按 fail-closed 处理
     * @return 不可变 readiness 决策；GateM-0 内真实能力一律 allowed=false
     */
    AdapterReadinessDecision evaluate(String venue, AdapterCapability capability);

    /**
     * 便捷判断是否允许执行。
     *
     * @param venue      venue 标识
     * @param capability 能力维度
     * @return {@link AdapterReadinessDecision#isReadyAndAllowed()}
     */
    default boolean isAllowed(String venue, AdapterCapability capability) {
        return evaluate(venue, capability).isReadyAndAllowed();
    }

    /**
     * Service-level fail-closed guard：未就绪即抛出，供调用方在执行真实能力前守卫。
     * <p>
     * Why:
     * 让交易 / marketdata 入口可以用一行守卫拒绝未就绪能力，而无需各自重复解释 readiness。
     * 异常信息只包含 venue / capability / status / message，不含 credential 或 raw payload。
     *
     * @param venue      venue 标识
     * @param capability 能力维度
     * @return 就绪决策（仅当 allowed）
     * @throws IllegalStateException 当能力未就绪 / 不被允许时抛出
     */
    default AdapterReadinessDecision requireReady(String venue, AdapterCapability capability) {
        AdapterReadinessDecision decision = evaluate(venue, capability);
        if (!decision.isReadyAndAllowed()) {
            throw new IllegalStateException(
                    "adapter capability not ready: venue=" + decision.venue()
                            + ", capability=" + decision.capability()
                            + ", status=" + decision.status()
                            + ", message=" + decision.message());
        }
        return decision;
    }
}
