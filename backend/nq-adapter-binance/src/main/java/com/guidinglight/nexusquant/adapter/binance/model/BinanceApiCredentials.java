package com.guidinglight.nexusquant.adapter.binance.model;

/**
 * BinanceApiCredentials 封装 Binance REST 所需凭证。
 *
 * @param apiKey Binance API key
 * @param secretKey Binance secret key
 */
public record BinanceApiCredentials(
        String apiKey,
        String secretKey
) {

    /**
     * Why:
     * 无 key 阶段依然需要构造 client 与单测；这里提供统一判定，
     * 避免调用方把空字符串当成“已配置”继续走签名路径。
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
