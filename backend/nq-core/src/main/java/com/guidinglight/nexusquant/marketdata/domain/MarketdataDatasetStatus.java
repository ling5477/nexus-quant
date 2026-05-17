package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataDatasetStatus 描述 GateH-3 数据集生命周期状态。
 * <p>
 * Why:
 * 数据集会被回测配置长期引用，因此状态必须独立于单次质量统计结果：
 * `READY` 表示可作为回测输入，`INVALID` 表示当前范围不可安全使用，`ARCHIVED` 表示保留历史但不再推荐新绑定。
 */
public enum MarketdataDatasetStatus {
    CREATED,
    READY,
    INVALID,
    ARCHIVED
}
