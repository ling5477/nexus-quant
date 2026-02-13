package com.guidinglight.nexusquant.core.recovery;

/**
 * RecoveryService 定义恢复/回放接口。
 */
public interface RecoveryService {

    /**
     * 触发最小恢复流程。
     *
     * @param traceId 本次恢复任务 traceId
     * @return 恢复报告
     */
    RecoveryReport rebuild(String traceId);
}
