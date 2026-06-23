package com.guidinglight.nexusquant.adapters.api.dto;

import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessDecision;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessReason;

import java.util.List;
import java.util.Objects;

/**
 * AdapterReadinessItemResponse 是单个 venue × capability 的只读 readiness 视图。
 * <p>
 * Why:
 * GateM-5A 需要把 {@link AdapterReadinessDecision}（adapter-api 内部模型）映射为对外稳定的 API 响应，
 * 避免前端直接依赖内部 record，也避免泄漏 adapter delegate / raw payload。该 DTO 只承载可审计、可脱敏字段：
 * venue / capability / status / allowed / liveAuthorized / reasons / message，全部来源于静态 readiness 决策，
 * 不包含 credential、apiKey、token、signature、passphrase 或 provider raw payload。
 *
 * @param venue          统一 venue 标识（如 OKX / BINANCE / NOOP），已规范化为大写
 * @param capability     能力维度枚举名（如 PLACE_ORDER / PUBLIC_MARKETDATA）
 * @param status         规范化就绪状态枚举名；当前 baseline 下真实交易能力恒非 READY
 * @param allowed        是否允许真实执行；当前 baseline 下恒为 false（fail-closed）
 * @param liveAuthorized 是否已获 LIVE 授权；当前 baseline LIVE DISABLED，恒为 false
 * @param reasons        fail-closed / 受限原因枚举名列表（非 READY 时非空）
 * @param message        可审计、可脱敏的简短说明（不含 raw payload）
 */
public record AdapterReadinessItemResponse(
        String venue,
        String capability,
        String status,
        boolean allowed,
        boolean liveAuthorized,
        List<String> reasons,
        String message
) {

    /**
     * 从内部 readiness 决策构造对外只读视图。
     *
     * @param decision adapter-api 内部 readiness 决策；不可为空
     * @return 对外 readiness item 响应（枚举转字符串名，reasons 拷贝为不可变列表）
     */
    public static AdapterReadinessItemResponse from(AdapterReadinessDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        return new AdapterReadinessItemResponse(
                decision.venue(),
                decision.capability().name(),
                decision.status().name(),
                decision.allowed(),
                decision.liveAuthorized(),
                decision.reasons().stream().map(AdapterReadinessReason::name).toList(),
                decision.message()
        );
    }
}
