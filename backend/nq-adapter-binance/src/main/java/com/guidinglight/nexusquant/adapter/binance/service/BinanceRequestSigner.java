package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;

import java.util.Objects;

/**
 * BinanceRequestSigner 负责按 key type 分发 Binance Spot REST 签名实现。
 * <p>
 * Why:
 * 现有 HMAC Testnet 路径必须保持不回归，同时还要补齐 Ed25519 signer。
 * 保留 `BinanceRequestSigner` 作为统一入口，`BinanceHttpClient` 和 `BinanceExchangeAdapter`
 * 就不需要知道具体算法类型，交易所差异仍然被隔离在 adapter-binance 内。
 */
public class BinanceRequestSigner {

    private final BinanceHmacRequestSigner hmacSigner;
    private final BinanceEd25519RequestSigner ed25519Signer;

    public BinanceRequestSigner() {
        this(new BinanceHmacRequestSigner(), new BinanceEd25519RequestSigner());
    }

    BinanceRequestSigner(
            BinanceHmacRequestSigner hmacSigner,
            BinanceEd25519RequestSigner ed25519Signer
    ) {
        this.hmacSigner = Objects.requireNonNull(hmacSigner, "hmacSigner must not be null");
        this.ed25519Signer = Objects.requireNonNull(ed25519Signer, "ed25519Signer must not be null");
    }

    /**
     * 对编码后的 query string 计算签名。
     *
     * @param encodedQuery 已编码且按最终发送顺序拼接好的 query string
     * @param credentials  Binance 凭证
     * @return HMAC 模式返回十六进制签名；Ed25519 模式返回 Base64 签名
     */
    public String sign(String encodedQuery, BinanceApiCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        if (!credentials.isConfigured()) {
            throw new IllegalArgumentException("Binance credentials are not fully configured");
        }
        return switch (credentials.keyType()) {
            case HMAC_SHA256 -> hmacSigner.sign(encodedQuery, credentials);
            case ED25519 -> ed25519Signer.sign(encodedQuery, credentials);
        };
    }
}
