package com.guidinglight.nexusquant.scheduler.lock;

import java.util.Objects;
import java.util.Optional;

/**
 * SchedulerLockExecution 描述一次互斥执行尝试的保守结果。
 *
 * <p>只有 {@link Status#ACQUIRED_AND_COMPLETED} 表示 callback 已在持锁事务中正常完成；
 * 其他状态均不得被调用方解释为成功。失败结果保留原始 cause，但 primitive 不负责记录
 * cause 文本，避免日志意外携带业务或敏感数据。
 *
 * @param status 执行状态
 * @param value callback 正常完成时的返回值，可为 {@code null}
 * @param failure 失败、超时或中断原因；成功和未获锁时为空
 * @param <T> callback 返回类型
 */
public record SchedulerLockExecution<T>(Status status, T value, Throwable failure) {

    public SchedulerLockExecution {
        Objects.requireNonNull(status, "status must not be null");
        if ((status == Status.ACQUIRED_AND_COMPLETED || status == Status.NOT_ACQUIRED) && failure != null) {
            throw new IllegalArgumentException("non-failure status must not carry failure");
        }
        if (status != Status.ACQUIRED_AND_COMPLETED && value != null) {
            throw new IllegalArgumentException("non-completed status must not carry value");
        }
        if (status != Status.ACQUIRED_AND_COMPLETED
                && status != Status.NOT_ACQUIRED
                && failure == null) {
            throw new IllegalArgumentException("failure status must carry cause");
        }
    }

    public static <T> SchedulerLockExecution<T> completed(T value) {
        return new SchedulerLockExecution<>(Status.ACQUIRED_AND_COMPLETED, value, null);
    }

    public static <T> SchedulerLockExecution<T> notAcquired() {
        return new SchedulerLockExecution<>(Status.NOT_ACQUIRED, null, null);
    }

    public static <T> SchedulerLockExecution<T> actionFailed(Throwable failure) {
        return new SchedulerLockExecution<>(Status.ACTION_FAILED, null, Objects.requireNonNull(failure));
    }

    public static <T> SchedulerLockExecution<T> timedOut(Throwable failure) {
        return new SchedulerLockExecution<>(Status.TIMED_OUT, null, Objects.requireNonNull(failure));
    }

    public static <T> SchedulerLockExecution<T> interrupted(Throwable failure) {
        return new SchedulerLockExecution<>(Status.INTERRUPTED, null, Objects.requireNonNull(failure));
    }

    /** 返回失败 cause；成功与未获锁返回 empty。 */
    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(failure);
    }

    public enum Status {
        ACQUIRED_AND_COMPLETED,
        NOT_ACQUIRED,
        ACTION_FAILED,
        TIMED_OUT,
        INTERRUPTED
    }
}
