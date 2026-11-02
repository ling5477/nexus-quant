package com.guidinglight.nexusquant.livecontrol.execution.application.port;

/** 读取现有 kill/session/approval/risk/intent/receipt 事实的脱敏运维投影。 */
public interface ExecutionOperationsSnapshotQuery {
    ExecutionOperationsSnapshot currentSnapshot();
}
