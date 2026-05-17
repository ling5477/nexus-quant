package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataIngestionStatus 固定 GateH-2 接入任务和运行记录状态集合。
 * <p>
 * Why:
 * DB check constraint、API 响应和 application service 必须共享同一套状态名，避免 controller 或 repository
 * 各自拼字符串后出现不可迁移的历史值。
 */
public enum MarketdataIngestionStatus {
    CREATED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    PARTIAL
}
