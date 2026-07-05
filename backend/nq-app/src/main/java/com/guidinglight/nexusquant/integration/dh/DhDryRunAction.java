package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunAction 定义 NQ limited dry-run client 可接受的 DH 只读动作集合。
 *
 * <p>Why: Integration-1 dry-run 只能记录观察、观望或偏向性建议，不能把 DH 输出升级成
 * BUY / SELL / PLACE_ORDER / CANCEL_ORDER，也不能进入 NQ execution / risk / ledger / paper / live 链路。
 */
public enum DhDryRunAction {
    /** 只记录观察结论，不表达交易方向。 */
    OBSERVE,
    /** 只记录不交易结论。 */
    NO_TRADE,
    /** 只记录多头偏向，严禁映射为 BUY。 */
    LONG_BIAS,
    /** 只记录空头偏向，严禁映射为 SELL。 */
    SHORT_BIAS
}
