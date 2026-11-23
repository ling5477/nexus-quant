package com.guidinglight.nexusquant.scheduler.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * SchedulerExecutionLock 为 scheduler 提供跨实例、非阻塞的互斥执行边界。
 *
 * <p>实现必须保证未取得锁时不调用 action，取得锁时 action 最多执行一次，并让锁生命周期
 * 覆盖完整 callback。该 contract 不包含交易、review lifecycle 或具体 scheduler 语义。
 */
public interface SchedulerExecutionLock {

    /**
     * 在指定 key 的互斥边界内执行 callback。
     *
     * @param key 稳定且不含敏感业务数据的 lock key
     * @param timeout 本次事务执行上限；实现必须校验正值及安全上限
     * @param action 仅在成功取得锁后调用一次的 callback
     * @return 明确区分完成、竞争失败、callback 失败、超时与中断的结果
     * @param <T> callback 返回类型
     */
    <T> SchedulerLockExecution<T> executeWithLock(
            SchedulerLockKey key,
            Duration timeout,
            Supplier<T> action
    );
}
