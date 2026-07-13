package com.guidinglight.nexusquant.adapter.okx.service;

/** 显式 OKX 环境；禁止 production/demo 自动 fallback。 */
public enum OkxPrivateEnvironment {
    PRODUCTION(false, "LIVE"),
    DEMO(true, "SIM");

    private final boolean simulatedTradingHeader;
    private final String accountTradeEnvironment;

    OkxPrivateEnvironment(boolean simulatedTradingHeader, String accountTradeEnvironment) {
        this.simulatedTradingHeader = simulatedTradingHeader;
        this.accountTradeEnvironment = accountTradeEnvironment;
    }

    public boolean simulatedTradingHeader() {
        return simulatedTradingHeader;
    }

    public String accountTradeEnvironment() {
        return accountTradeEnvironment;
    }
}
