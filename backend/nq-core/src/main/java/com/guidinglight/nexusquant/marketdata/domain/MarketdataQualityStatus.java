package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataQualityStatus 描述行情数据集和覆盖统计的质量状态。
 * <p>
 * Why:
 * GateH-3 只做回测输入前置质量判断，不改变回测算法；该枚举让 dataset、coverage 与前端展示使用同一套状态口径。
 */
public enum MarketdataQualityStatus {
    OK,
    GAP_DETECTED,
    INCOMPLETE,
    INVALID
}
