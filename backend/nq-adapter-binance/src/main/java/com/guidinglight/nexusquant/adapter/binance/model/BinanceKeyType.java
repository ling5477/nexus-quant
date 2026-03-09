package com.guidinglight.nexusquant.adapter.binance.model;

import java.util.Locale;

/**
 * BinanceKeyType 描述 Binance REST request security 当前启用的签名算法。
 * <p>
 * Why:
 * GateC-2 需要在不改变 core/ledger/risk 的前提下兼容 Binance 的多种密钥类型。
 * 把算法选择显式建模成 enum，运行时配置、signer 分发与测试才能围绕同一语义收敛。
 */
public enum BinanceKeyType {

    HMAC_SHA256,
    ED25519;

    /**
     * 解析环境变量中的 key type。
     *
     * @param value 环境变量原始值，允许 hmac/hmac_sha256/ed25519
     * @return 归一化后的签名类型，空值默认 HMAC_SHA256
     */
    public static BinanceKeyType fromEnv(String value) {
        if (value == null || value.isBlank()) {
            return HMAC_SHA256;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "ed25519" -> ED25519;
            case "hmac", "hmac_sha256", "hmac-sha256" -> HMAC_SHA256;
            default -> throw new IllegalArgumentException("unsupported Binance key type: " + value);
        };
    }
}
