package com.guidinglight.nexusquant.marketdata.domain.instrument;

/**
 * OkxVenueRuleContract 固化 GateW-3 本地 OKX Spot venue-rule facts 的 NQ contract 标识。
 *
 * <p>该版本是 NQ parser/schema contract，不是 OKX 官方 API 版本，也不构成交易授权。</p>
 */
public final class OkxVenueRuleContract {

    public static final String SOURCE = "OKX_PUBLIC_INSTRUMENTS";
    public static final String SOURCE_SCHEMA_VERSION = "NQ_OKX_VENUE_RULE_FACTS_V1";

    private OkxVenueRuleContract() {
    }
}
