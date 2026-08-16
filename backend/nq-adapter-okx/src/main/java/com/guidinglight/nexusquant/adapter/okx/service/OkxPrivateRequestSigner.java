package com.guidinglight.nexusquant.adapter.okx.service;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * GateW-2 专用 OKX V5 signer；method/path/query/body 均来自 typed request。
 */
public final class OkxPrivateRequestSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = new java.time.format.DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter();

    private final Clock clock;

    public OkxPrivateRequestSigner(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 构造短生命周期认证头。调用方必须 try-with-resources 并在 transport 返回前关闭。
     */
    SignedHeaders sign(OkxPrivateReadRequest request, OkxPrivateCredentialContext credential) {
        Objects.requireNonNull(request, "request must not be null");
        return sign(request.operation().method(), request.pathWithQuery(), "", credential);
    }

    SignedHeaders sign(
            String method,
            String pathWithQuery,
            String body,
            OkxPrivateCredentialContext credential
    ) {
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new IllegalArgumentException("private request method is not allowlisted");
        }
        if (pathWithQuery == null || !pathWithQuery.startsWith("/api/v5/")
                || pathWithQuery.indexOf('\r') >= 0 || pathWithQuery.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("private request path is invalid");
        }
        body = Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(credential, "credential must not be null");
        String timestamp = TIMESTAMP_FORMATTER.format(clock.instant());
        String prehash = timestamp + method + pathWithQuery + body;
        byte[] secretBytes = utf8(credential.secretKey());
        byte[] prehashBytes = prehash.getBytes(StandardCharsets.UTF_8);
        byte[] digest = null;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA256));
            digest = mac.doFinal(prehashBytes);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("OK-ACCESS-KEY", new String(credential.apiKey()));
            headers.put("OK-ACCESS-SIGN", Base64.getEncoder().encodeToString(digest));
            headers.put("OK-ACCESS-TIMESTAMP", timestamp);
            headers.put("OK-ACCESS-PASSPHRASE", new String(credential.passphrase()));
            return new SignedHeaders(headers);
        } catch (Exception ex) {
            throw new OkxPrivateReadException(OkxPrivateReadError.SIGNATURE_FAILURE, ex);
        } finally {
            Arrays.fill(secretBytes, (byte) 0);
            Arrays.fill(prehashBytes, (byte) 0);
            if (digest != null) {
                Arrays.fill(digest, (byte) 0);
            }
        }
    }

    String timestampForTest() {
        return TIMESTAMP_FORMATTER.format(clock.instant());
    }

    String signatureForTest(OkxPrivateReadRequest request, OkxPrivateCredentialContext credential) {
        try (SignedHeaders headers = sign(request, credential)) {
            return headers.values().get("OK-ACCESS-SIGN");
        }
    }

    private static byte[] utf8(char[] chars) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
        return bytes;
    }

    static final class SignedHeaders implements AutoCloseable {
        private final Map<String, String> values;

        private SignedHeaders(Map<String, String> values) {
            this.values = values;
        }

        Map<String, String> values() {
            return values;
        }

        @Override
        public void close() {
            values.clear();
        }

        @Override
        public String toString() {
            return "SignedHeaders[REDACTED]";
        }
    }
}
