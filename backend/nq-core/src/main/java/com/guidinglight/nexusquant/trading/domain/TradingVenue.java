package com.guidinglight.nexusquant.trading.domain;

import java.util.Locale;

/** ordinary 交易身份的唯一解析边界；大小写和首尾空白不能改变风控、authority 或路由语义。 */
public enum TradingVenue {
    OKX,
    BINANCE,
    PAPER;

    /** 未知身份在创建业务事实之前拒绝；此类型不授予 adapter readiness 或真实交易权限。 */
    public static TradingVenue parse(String external) {
        if (external == null || external.isBlank()) {
            throw new IllegalArgumentException("venue must not be blank");
        }
        try {
            return valueOf(external.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("unsupported trading venue", invalid);
        }
    }
}
