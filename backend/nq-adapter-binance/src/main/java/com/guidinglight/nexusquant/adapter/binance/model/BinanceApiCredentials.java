package com.guidinglight.nexusquant.adapter.binance.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * BinanceApiCredentials 封装 Binance REST 所需凭证。
 *
 * @param apiKey         Binance API key
 * @param secretKey      HMAC 模式使用的 secret key
 * @param keyType        当前使用的 Binance key type
 * @param privateKey     Ed25519 模式可直接提供的 private key PEM
 * @param privateKeyPath Ed25519 模式可选的 private key 文件路径
 */
public record BinanceApiCredentials(
        String apiKey,
        String secretKey,
        BinanceKeyType keyType,
        String privateKey,
        String privateKeyPath
) {

    public BinanceApiCredentials(String apiKey, String secretKey) {
        this(apiKey, secretKey, BinanceKeyType.HMAC_SHA256, null, null);
    }

    public BinanceApiCredentials {
        keyType = keyType == null ? BinanceKeyType.HMAC_SHA256 : keyType;
    }

    /**
     * Why:
     * 无 key 阶段依然需要构造 client 与单测；这里提供统一判定，
     * 避免调用方把空字符串当成“已配置”继续走签名路径。
     */
    public boolean isConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return switch (keyType) {
            case HMAC_SHA256 -> secretKey != null && !secretKey.isBlank();
            case ED25519 -> (privateKey != null && !privateKey.isBlank())
                    || (privateKeyPath != null && !privateKeyPath.isBlank());
        };
    }

    /**
     * 解析当前 Ed25519 模式的 private key PEM。
     * <p>
     * Why:
     * 为了不把文件系统读取散落到 HTTP client / signer / runtime config，多种输入方式在凭证对象里统一收口。
     */
    public String resolvePrivateKeyPem() {
        if (privateKey != null && !privateKey.isBlank()) {
            return privateKey;
        }
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            throw new IllegalArgumentException("Binance Ed25519 private key is missing");
        }
        try {
            return Files.readString(Path.of(privateKeyPath));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Binance Ed25519 private key path is unreadable: " + privateKeyPath, ex);
        }
    }
}
