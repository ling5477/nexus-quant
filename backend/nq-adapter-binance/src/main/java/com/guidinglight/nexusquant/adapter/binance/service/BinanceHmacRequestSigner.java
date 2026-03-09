package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * BinanceHmacRequestSigner 负责 HMAC-SHA256 请求签名。
 * <p>
 * Why:
 * HMAC 是当前 Binance Testnet/既有运行态路径的默认签名方式。
 * 把实现从 dispatcher 中拆出来，可以在引入 Ed25519 后保持 HMAC 路径测试与行为完全稳定。
 */
public class BinanceHmacRequestSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 对编码后的 query string 计算 HMAC SHA256 签名。
     *
     * @param encodedQuery 已编码且按最终发送顺序拼接好的 query string
     * @param credentials  Binance 凭证，必须携带 secret key
     * @return 小写十六进制签名
     */
    public String sign(String encodedQuery, BinanceApiCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(credentials.secretKey().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(encodedQuery.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign Binance request with HMAC SHA256", ex);
        }
    }
}
