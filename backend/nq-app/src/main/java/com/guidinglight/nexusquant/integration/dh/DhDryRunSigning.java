package com.guidinglight.nexusquant.integration.dh;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * DhDryRunSigning 生成 value-based HMAC signature material 与签名。
 *
 * <p>Why: 已冻结规则要求 header name 不进入 signature material，但 requestId、traceId、tenantId、source、
 * timestamp、nonce、schemaVersion 和 body hash 必须参与安全绑定，防止 payload 与 header 被拆分重放。</p>
 */
public final class DhDryRunSigning {

    private static final String METHOD = "POST";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private DhDryRunSigning() {
    }

    /**
     * 从 endpoint URL 提取 signature material 使用的 path。
     *
     * @param endpointUrl configured endpoint URL
     * @return URL path；空 path 归一为 `/`
     * @throws IllegalArgumentException endpointUrl 不是合法 URI 时抛出
     */
    public static String endpointPath(String endpointUrl) {
        URI uri = URI.create(endpointUrl);
        String path = uri.getRawPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    /**
     * 生成 value-based signature material。
     *
     * @param path          endpoint path，例如 `/api/ai/decision-dry-runs`
     * @param source        source，必须为 NQ_DRYRUN
     * @param tenantId      tenantId，参与绑定
     * @param requestId     requestId，参与绑定
     * @param traceId       traceId，参与绑定
     * @param timestamp     RFC3339 UTC Z timestamp
     * @param nonce         单次请求唯一 nonce
     * @param schemaVersion schema version
     * @param body          JSON body
     * @return 用换行分隔的 material；不包含任何 header name
     */
    public static String signatureMaterial(
            String path,
            String source,
            String tenantId,
            String requestId,
            String traceId,
            String timestamp,
            String nonce,
            String schemaVersion,
            String body) {
        return String.join(
                "\n",
                METHOD,
                path,
                source,
                tenantId,
                requestId,
                traceId,
                timestamp,
                nonce,
                schemaVersion,
                sha256Hex(body));
    }

    /**
     * 生成 HMAC-SHA256 hex signature。
     *
     * @param secret   HMAC secret；禁止写入日志或 record
     * @param material value-based material
     * @return hex encoded signature
     */
    public static String hmacSha256Hex(String secret, String material) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return hex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("unable to sign DH dry-run request", ex);
        }
    }

    /**
     * 生成 SHA-256 body hash。
     *
     * @param body JSON body
     * @return hex encoded SHA-256
     */
    public static String sha256Hex(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
