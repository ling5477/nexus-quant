package com.guidinglight.nexusquant.scheduler.lock;

import java.util.Objects;

/**
 * SchedulerLockKey 是通用 scheduler execution lock 的稳定业务无关标识。
 *
 * <p>namespace 隔离不同调度类别，name 标识类别内的具体任务。两者只允许短且非空的
 * 非敏感标识，禁止把 tenant、账户、订单、credential 或原始 payload 放入 lock key。
 */
public record SchedulerLockKey(String namespace, String name) {

    /** 单段 lock key 的安全上限，避免无限长输入参与跨实例协议。 */
    public static final int MAX_COMPONENT_LENGTH = 128;

    public SchedulerLockKey {
        namespace = normalize(namespace, "namespace");
        name = normalize(name, "name");
    }

    private static String normalize(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > MAX_COMPONENT_LENGTH) {
            throw new IllegalArgumentException(field + " must not exceed " + MAX_COMPONENT_LENGTH + " characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return normalized;
    }
}
