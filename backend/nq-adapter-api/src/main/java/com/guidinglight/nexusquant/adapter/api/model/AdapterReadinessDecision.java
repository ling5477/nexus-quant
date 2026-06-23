package com.guidinglight.nexusquant.adapter.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * AdapterReadinessDecision 是 readiness 评估的不可变结果。
 * <p>
 * Why:
 * GateM-0 需要把“某 venue 的某 capability 当前是否可用、是否 LIVE 授权、因为什么不可用”
 * 作为一个可审计、可传递的只读结果返回给调用方。该 record 在构造期强制 fail-closed 不变量，
 * 任何调用方都无法构造出“未就绪却 allowed=true”的决策，从而避免误判 real-ready。
 *
 * @param venue          统一交易所 / adapter 标识（如 OKX / BINANCE / NOOP）；不可为空
 * @param capability     被评估的能力维度；不可为空
 * @param status         规范化就绪状态；不可为空
 * @param allowed        是否允许该能力真实执行；GateM-0 内真实能力一律 false
 * @param liveAuthorized 是否已获 LIVE 授权；GateL baseline LIVE DISABLED，恒为 false
 * @param reasons        不可用 / 受限原因（非 READY 时必须非空）；构造期拷贝为不可变列表
 * @param checkedAt      评估时间戳；不可为空
 * @param message        可审计、可脱敏的简短说明；不可为空
 */
public record AdapterReadinessDecision(
        String venue,
        AdapterCapability capability,
        AdapterReadinessStatus status,
        boolean allowed,
        boolean liveAuthorized,
        List<AdapterReadinessReason> reasons,
        Instant checkedAt,
        String message
) {

    public AdapterReadinessDecision {
        Objects.requireNonNull(venue, "venue must not be null");
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(reasons, "reasons must not be null");
        Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        Objects.requireNonNull(message, "message must not be null");
        // 拷贝为不可变列表，避免外部持有引用后篡改 readiness 结论。
        reasons = List.copyOf(reasons);
        // Fail-closed 不变量 1：只有 READY 才允许 allowed=true。
        // 这样任何调用方都无法构造“未就绪却被允许执行”的决策。
        if (allowed && status != AdapterReadinessStatus.READY) {
            throw new IllegalArgumentException(
                    "allowed=true requires status=READY, but was " + status);
        }
        // Fail-closed 不变量 2：未允许时必须给出原因，保证拒绝是可解释的。
        if (!allowed && reasons.isEmpty()) {
            throw new IllegalArgumentException(
                    "a not-allowed decision must carry at least one reason");
        }
        // Fail-closed 不变量 3：未获 LIVE 授权时不得允许会改变真实账户状态的能力。
        if (allowed && capability.isLiveMutating() && !liveAuthorized) {
            throw new IllegalArgumentException(
                    "live mutating capability cannot be allowed without live authorization");
        }
    }

    /**
     * 是否就绪并允许执行。
     *
     * @return 仅当 status=READY 且 allowed=true；其余一律 false，便于上层直接 fail-closed
     */
    public boolean isReadyAndAllowed() {
        return status.isReady() && allowed;
    }
}
