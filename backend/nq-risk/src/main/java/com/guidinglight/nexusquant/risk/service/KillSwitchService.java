package com.guidinglight.nexusquant.risk.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Durable、fail-closed 的全局 kill-switch application service。
 *
 * <p>职责：从持久化 port 读取不可变 snapshot，并提供唯一 production mutation：engage。
 * 缺记录、数据库异常、非法记录或未来时间戳均转换为 UNKNOWN/BLOCKED；本类没有 release、
 * disable、reset 或 clear 能力，且线程安全性由无共享可变状态与 repository transaction 保证。</p>
 */
public final class KillSwitchService {

    private static final KillSwitchScope GLOBAL_SCOPE = KillSwitchScope.GLOBAL_TRADING;
    private static final String UNKNOWN_TRACE_ID = "KILL_SWITCH_TRACE_UNAVAILABLE";
    private static final String ENGAGE_SOURCE = "OPERATOR_ENGAGE";

    private final KillSwitchStateRepository repository;
    private final Clock clock;

    public KillSwitchService(KillSwitchStateRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 读取全局 kill-switch snapshot。
     *
     * @return 永不抛出存储异常的 fail-closed snapshot
     */
    public KillSwitchSnapshot snapshot() {
        Instant observedAt = clock.instant();
        try {
            KillSwitchState state = repository.findByScope(GLOBAL_SCOPE).orElse(null);
            if (state == null) {
                return unknown(observedAt, "KILL_SWITCH_STATE_MISSING", "DURABLE_STORE");
            }
            if (state.updatedAt().isAfter(observedAt)) {
                return unknown(observedAt, "KILL_SWITCH_UPDATED_AT_FUTURE", state.source());
            }
            return snapshot(state, observedAt);
        } catch (RuntimeException ex) {
            return unknown(observedAt, "KILL_SWITCH_STATE_READ_FAILED", "DURABLE_STORE");
        }
    }

    /**
     * 显式 engage 全局 kill switch。
     *
     * <p>该操作不访问交易所或 credential，不在数据库事务中执行外部调用；重复 engage 返回同一
     * current state。没有对应 disengage 方法。</p>
     *
     * @param expectedVersion optimistic-lock 版本
     * @param reasonCode      脱敏原因码
     * @param updatedBy       操作者标识
     * @param traceId         调用链标识
     * @return engage 后的 durable snapshot
     */
    public KillSwitchSnapshot engage(
            long expectedVersion,
            String reasonCode,
            String updatedBy,
            String traceId
    ) {
        Instant occurredAt = clock.instant();
        KillSwitchState state = repository.engage(new KillSwitchEngageCommand(
                GLOBAL_SCOPE,
                expectedVersion,
                reasonCode,
                ENGAGE_SOURCE,
                updatedBy,
                traceId,
                occurredAt
        ));
        return snapshot(state, occurredAt);
    }

    private static KillSwitchSnapshot snapshot(KillSwitchState state, Instant observedAt) {
        return new KillSwitchSnapshot(
                state.scope(),
                state.status(),
                state.version(),
                state.reasonCode(),
                state.source(),
                state.updatedAt(),
                observedAt,
                state.traceId()
        );
    }

    private static KillSwitchSnapshot unknown(Instant observedAt, String reasonCode, String source) {
        return new KillSwitchSnapshot(
                GLOBAL_SCOPE,
                KillSwitchStatus.UNKNOWN,
                0,
                reasonCode,
                source,
                null,
                observedAt,
                UNKNOWN_TRACE_ID
        );
    }
}
