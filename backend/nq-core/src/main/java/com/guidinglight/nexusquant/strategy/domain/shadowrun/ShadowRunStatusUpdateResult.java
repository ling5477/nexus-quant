package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import java.util.UUID;

/**
 * Shadow Run 状态更新结果。
 *
 * <p>repository 使用该结果返回状态机流转和乐观锁版本变化，便于调用方审计本地事实更新；
 * 它不携带外部交易结果或真实账户信息。
 */
public record ShadowRunStatusUpdateResult(
        UUID shadowRunId,
        ShadowRunStatus fromStatus,
        ShadowRunStatus toStatus,
        long previousVersion,
        long newVersion
) {
}
