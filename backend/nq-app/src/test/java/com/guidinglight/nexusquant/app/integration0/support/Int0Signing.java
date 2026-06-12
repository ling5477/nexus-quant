package com.guidinglight.nexusquant.app.integration0.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Int0Signing 是 test-only HMAC-SHA256 签名工具（INT0-T04）。
 *
 * <p>签名原材料 canonical string 由 header 关键字段 + sha256(body) 组成。
 * 本类只使用传入的 test-only 假 secret，<b>绝不</b>读取真实 secret，
 * 也不把 canonical string / signature 原材料打印到日志。
 */
public final class Int0Signing {

    private Int0Signing() {}

    /** canonical：source\n tenant\n requestId\n traceId\n timestamp\n nonce\n sha256(body)。 */
    public static String canonical(Map<String, String> headers, String body) {
        return String.join(
                "\n",
                headers.getOrDefault(Int0Contract.H_SOURCE, ""),
                headers.getOrDefault(Int0Contract.H_TENANT, ""),
                headers.getOrDefault(Int0Contract.H_REQUEST_ID, ""),
                headers.getOrDefault(Int0Contract.H_TRACE_ID, ""),
                headers.getOrDefault(Int0Contract.H_TIMESTAMP, ""),
                headers.getOrDefault(Int0Contract.H_NONCE, ""),
                sha256Hex(body));
    }

    public static String hmacSha256Hex(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            throw new IllegalStateException("HMAC init failed", ex);
        }
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** 常量时间比较，避免签名比较计时侧信道。 */
    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
