package com.guidinglight.nexusquant.adapter.okx.service;

/**
 * OKX account/config IP allowlist 的脱敏精确匹配结果；不携带 allowlist 原值。
 */
public enum OkxIpAllowlistStatus {
    MATCHED,
    MISSING,
    MISMATCHED,
    UNKNOWN,
    NOT_CHECKED
}
