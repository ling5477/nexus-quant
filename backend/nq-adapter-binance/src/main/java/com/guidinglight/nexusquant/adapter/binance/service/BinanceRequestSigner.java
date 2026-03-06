package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * BinanceRequestSigner 负责生成 Binance Spot REST 的 HMAC SHA256 签名。
 *
 * Why:
 * Binance signed endpoint 的签名输入就是最终 query string。
 * 把签名逻辑从 HTTP client 中拆出来，单测才能稳定覆盖 GET / POST / DELETE 的 query 路径。
 */
public class BinanceRequestSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 对编码后的 query string 计算签名。
     *
     * @param encodedQuery 已编码且按最终发送顺序拼接好的 query string
     * @param credentials Binance 凭证
     * @return 小写十六进制签名
     */
    public String sign(String encodedQuery, BinanceApiCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        if (!credentials.isConfigured()) {
            throw new IllegalArgumentException("Binance credentials are not fully configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(credentials.secretKey().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(encodedQuery.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign Binance request", ex);
        }
    }
}
