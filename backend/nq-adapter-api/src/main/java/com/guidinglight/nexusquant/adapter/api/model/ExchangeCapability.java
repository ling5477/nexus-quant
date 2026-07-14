package com.guidinglight.nexusquant.adapter.api.model;

/**
 * ExchangeCapability 描述交易所能力合同，而不是运行时授权或可调用性。
 *
 * <p>GateW-1 使用该枚举将公开行情、私有只读、交易写侧和资金动作分开建模。调用方不能仅因
 * capability 存在就推断可以访问网络、读取凭证或执行交易；实际放行必须由 endpoint guard 的
 * default-deny 决策决定。</p>
 */
public enum ExchangeCapability {

    PUBLIC_MARKET_DATA,
    PRIVATE_ACCOUNT_CONFIGURATION_READ,
    PRIVATE_ACCOUNT_BALANCE_READ,
    PRIVATE_PERMISSION_READ,
    PRIVATE_OPEN_ORDERS_READ,
    PRIVATE_ORDER_HISTORY_READ,
    PRIVATE_RECENT_FILLS_READ,
    ORDER_PREVIEW_LOCAL,
    ORDER_SUBMISSION,
    ORDER_CANCEL,
    TRANSFER,
    WITHDRAW,

    /** 未登记或缺失能力的 fail-closed 占位。 */
    UNKNOWN
}
