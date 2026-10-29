package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import java.util.UUID;

/**
 * Shadow Run repository 乐观锁冲突。
 *
 * <p>该异常表示本地 fact 已被其他写入更新，调用方必须重新读取当前 version 后再判断下一步；
 * 不允许绕过状态机或无条件覆盖状态。
 */
public class ShadowRunOptimisticLockException extends RuntimeException {

    public ShadowRunOptimisticLockException(UUID shadowRunId, long expectedVersion) {
        super("shadow run optimistic lock mismatch: " + shadowRunId + ", expectedVersion=" + expectedVersion);
    }
}
