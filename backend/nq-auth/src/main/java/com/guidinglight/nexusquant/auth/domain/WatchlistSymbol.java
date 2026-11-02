package com.guidinglight.nexusquant.auth.domain;

/**
 * 自选只表达用户查看的公开标的，不授予交易所或交易环境权限。
 */
public record WatchlistSymbol(String exchangeCode, String marketType, String symbol) {
    public WatchlistSymbol {
        if (exchangeCode == null
                || !exchangeCode.matches("[A-Z0-9_]{2,24}")
                || !"SPOT".equals(marketType)
                || symbol == null
                || !symbol.matches("[A-Z0-9][A-Z0-9_.:/-]{1,63}")) {
            throw new IllegalArgumentException("invalid watchlist symbol");
        }
    }
}
