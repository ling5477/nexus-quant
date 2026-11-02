package com.guidinglight.nexusquant.risk.model;

/** 风控使用订单已冻结的交易环境；无法识别的身份必须保留为 UNKNOWN。 */
public enum TradeEnvironment {
    SIM, LIVE, UNKNOWN;

    public static TradeEnvironment fromOrder(String value) {
        if ("SIM".equals(value)) return SIM;
        if ("LIVE".equals(value)) return LIVE;
        return UNKNOWN;
    }
}
