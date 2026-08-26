package com.guidinglight.nexusquant.adapter.okx.service;

import java.util.Set;

/**
 * GateY-6B OKX Spot provider 的 exact operation allowlist。
 *
 * <p>method/path 由 enum 固定，仅供 future transport implementation 审查；provider surface 不接受
 * raw method/path。该枚举存在不表示 credential、network、private trading 或 LIVE 已授权。</p>
 */
public enum OkxSpotProviderOperation {
    PLACE_LIMIT("POST", "/api/v5/trade/order", true),
    QUERY_ORDER("GET", "/api/v5/trade/order", false),
    CANCEL_ORDER("POST", "/api/v5/trade/cancel-order", true),
    READ_ORDER("GET", "/api/v5/trade/order", false),
    READ_FILLS("GET", "/api/v5/trade/fills", false),
    READ_CLOCK("GET", "/api/v5/public/time", false);

    private static final Set<OkxSpotProviderOperation> EXACT_ALLOWLIST = Set.of(values());

    private final String method;
    private final String path;
    private final boolean mutation;

    OkxSpotProviderOperation(String method, String path, boolean mutation) {
        this.method = method;
        this.path = path;
        this.mutation = mutation;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public boolean mutation() {
        return mutation;
    }

    public static Set<OkxSpotProviderOperation> exactAllowlist() {
        return EXACT_ALLOWLIST;
    }
}
