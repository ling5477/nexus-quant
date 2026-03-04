package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * OkxRequestSigner 负责生成 OKX v5 的签名头。
 * <p>
 * Why:
 * GateC-1 的真实 REST 调用首先会死在签名错误上；把 pre-hash 与 HMAC-SHA256 逻辑单独收敛，
 * 才能对 GET query / POST body 做稳定单测，而不是在 adapter 里靠猜修 bug。
 */
public class OkxRequestSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 生成鉴权头。
     * <p>
     * Why:
     * OKX 的签名必须覆盖 `timestamp + method + requestPath(with query) + body`。
     * 这里显式接收已经拼好的 `requestPathWithQuery`，避免调用方在 GET 请求里漏掉 query 导致签名漂移。
     *
     * @param credentials          OKX 凭证
     * @param method               HTTP 方法
     * @param requestPathWithQuery 形如 `/api/v5/trade/order?instId=BTC-USDT`
     * @param requestBodyJson      请求体 JSON；GET 无 body 时传空串
     * @param timestamp            已生成的请求时间戳
     * @return OKX 所需签名头集合
     */
    public Map<String, String> signHeaders(
            OkxApiCredentials credentials,
            String method,
            String requestPathWithQuery,
            String requestBodyJson,
            String timestamp
    ) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        if (!credentials.isConfigured()) {
            throw new IllegalArgumentException("OKX credentials are not fully configured");
        }
        String normalizedMethod = normalizeMethod(method);
        String body = requestBodyJson == null ? "" : requestBodyJson;
        String prehash = timestamp + normalizedMethod + requestPathWithQuery + body;
        String signature = sign(prehash, credentials.secretKey());
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("OK-ACCESS-KEY", credentials.apiKey());
        headers.put("OK-ACCESS-SIGN", signature);
        headers.put("OK-ACCESS-TIMESTAMP", timestamp);
        headers.put("OK-ACCESS-PASSPHRASE", credentials.passphrase());
        return headers;
    }

    /**
     * 暴露 pre-hash 用于单测校验 GET query / POST body 是否被纳入签名。
     */
    String buildPrehashForTest(String method, String requestPathWithQuery, String requestBodyJson, String timestamp) {
        return timestamp + normalizeMethod(method) + requestPathWithQuery + (requestBodyJson == null ? "" : requestBodyJson);
    }

    private String sign(String prehash, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign OKX request", ex);
        }
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        return method.trim().toUpperCase();
    }
}
