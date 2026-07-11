package com.guidinglight.nexusquant.validationreview.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewSensitiveDataGuard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/**
 * GateV-2 lifecycle request 的 deterministic canonical hash。
 *
 * <p>对象 key 递归排序、数组顺序保留、编码固定 UTF-8；输入不含 requestId/traceId，因而不依赖
 * Map iteration、locale、timezone 或每次请求变化的链路字段。
 */
public final class ValidationReviewRequestHasher {

    private static final int MAX_REASON_LENGTH = 512;
    private static final int MAX_METADATA_BYTES = 8_192;

    private final ObjectMapper objectMapper;

    /** @param objectMapper 仅用于 canonical JSON 构造和序列化 */
    public ValidationReviewRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 生成 request hash 与可安全写入 lifecycle event 的规范化字段。
     *
     * @param caseId 服务端 path case id
     * @param action 固定 allowlisted action
     * @param expectedVersion 客户端乐观锁版本
     * @param reason 必填人工原因
     * @param metadata 可空 JSON object
     * @return canonical request；hash 为 lowercase SHA-256 hex
     */
    public CanonicalRequest canonicalize(
            UUID caseId,
            ValidationReviewAction action,
            long expectedVersion,
            String reason,
            JsonNode metadata
    ) {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        String normalizedReason = normalizeReason(reason);
        ValidationReviewSensitiveDataGuard.validateText("reason", normalizedReason);
        JsonNode normalizedMetadata = metadata == null ? objectMapper.createObjectNode() : sort(metadata);
        ValidationReviewSensitiveDataGuard.validateObject("metadata", normalizedMetadata);
        byte[] metadataBytes = write(normalizedMetadata);
        if (metadataBytes.length > MAX_METADATA_BYTES) {
            throw new IllegalArgumentException("metadata exceeds max UTF-8 size " + MAX_METADATA_BYTES);
        }

        ObjectNode canonical = objectMapper.createObjectNode();
        canonical.put("action", action.name());
        canonical.put("caseId", caseId.toString());
        canonical.put("expectedVersion", expectedVersion);
        canonical.set("metadata", normalizedMetadata);
        canonical.put("reason", normalizedReason);
        String hash = sha256(write(sort(canonical)));
        return new CanonicalRequest(hash, normalizedReason, normalizedMetadata.deepCopy());
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        String normalized = reason.replace("\r\n", "\n").replace('\r', '\n').trim();
        ValidationReviewCase.requireText(normalized, "reason", MAX_REASON_LENGTH);
        return normalized;
    }

    private JsonNode sort(JsonNode value) {
        if (value == null || value.isNull()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        if (value.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            value.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                sorted.set(name, sort(value.get(name)));
            }
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            value.forEach(item -> sorted.add(sort(item)));
            return sorted;
        }
        return value.deepCopy();
    }

    private byte[] write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to canonicalize validation review request", ex);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /**
     * @param requestHash deterministic SHA-256
     * @param reason 规范化原因
     * @param metadata 递归 key 排序后的脱敏 metadata
     */
    public record CanonicalRequest(String requestHash, String reason, JsonNode metadata) {
        public CanonicalRequest {
            metadata = metadata.deepCopy();
        }

        /** @return 防御性 metadata 副本 */
        public JsonNode metadata() {
            return metadata.deepCopy();
        }
    }
}
