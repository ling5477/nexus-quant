package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * BinanceEd25519RequestSigner 负责 Binance Ed25519 请求签名。
 * <p>
 * Why:
 * Binance 实盘 key 可能使用 Ed25519，而现有 adapter-binance 只支持 HMAC。
 * 该类把 Ed25519 的 PEM/PKCS8 解析与签名细节隔离在 adapter 内，避免 core 感知任何交易所密钥差异。
 */
public class BinanceEd25519RequestSigner {

    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    /**
     * 对编码后的 query string 计算 Ed25519 签名。
     *
     * @param encodedQuery 已编码且按最终发送顺序拼接好的 query string
     * @param credentials  Binance 凭证，必须携带 Ed25519 private key 或其路径
     * @return Base64 编码后的签名，HTTP client 会继续做 URL 编码
     */
    public String sign(String encodedQuery, BinanceApiCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(parsePrivateKey(credentials.resolvePrivateKeyPem()));
            signature.update(encodedQuery.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign Binance request with Ed25519", ex);
        }
    }

    /**
     * 解析 PKCS8 PEM 私钥。
     * <p>
     * Why:
     * 运行态既可能通过 .env 直接提供 PEM，也可能通过 private key path 提供文件路径。
     * 这里统一兼容两种输入，避免上层反复判断与复制 PEM 清洗逻辑。
     */
    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace(PRIVATE_KEY_BEGIN, "")
                .replace(PRIVATE_KEY_END, "")
                .replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Binance Ed25519 private key is empty");
        }
        byte[] privateKeyBytes = Base64.getDecoder().decode(normalized);
        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
    }
}
