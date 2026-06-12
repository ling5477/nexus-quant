package com.guidinglight.nexusquant.app.integration0.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Int0ContractValidator 是 test-only Integration-0 入站契约校验器。
 *
 * <p>它把已冻结的安全策略组合成一个校验管线：header 存在性 -> content-type -> source allowlist ->
 * payload 上限 -> timestamp 窗口 -> HMAC 签名 -> nonce 防重放 -> tenant 绑定 -> 禁止字段 ->
 * 禁止能力 -> schema。任一步失败按冻结拒绝码返回，并产出脱敏 audit event。
 *
 * <p>本类不连接任何真实 NQ 入站端点、真实 DH client、真实交易所或真实凭证；纯内存校验。
 */
public final class Int0ContractValidator {

    /** 仅校验 header 存在性与基本规则，返回拒绝结果或 null（通过）。 */
    public Int0ValidationResult validateHeaders(Map<String, String> headers) {
        String source = headers.get(Int0Contract.H_SOURCE);
        String tenant = headers.get(Int0Contract.H_TENANT);
        String requestId = headers.get(Int0Contract.H_REQUEST_ID);
        String traceId = headers.get(Int0Contract.H_TRACE_ID);

        for (String required : Int0Contract.REQUIRED_HEADERS) {
            if (isBlank(headers.get(required))) {
                int status = statusForMissingHeader(required);
                String category = "MISSING_HEADER:" + required;
                return Int0ValidationResult.reject(
                        status,
                        category,
                        List.of("missing required header: " + required),
                        Int0AuditEvent.rejected(
                                Int0AuditEvent.REJECTED, source, tenant, requestId, traceId, category));
            }
        }
        if (!Int0Contract.CONTENT_TYPE_JSON.equalsIgnoreCase(headers.get(Int0Contract.H_CONTENT_TYPE))) {
            return Int0ValidationResult.reject(
                    400,
                    "BAD_CONTENT_TYPE",
                    List.of("Content-Type must be application/json"),
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.REJECTED, source, tenant, requestId, traceId, "BAD_CONTENT_TYPE"));
        }
        if (!Int0Contract.ALLOWED_SOURCES.contains(source)) {
            return Int0ValidationResult.reject(
                    403,
                    "SOURCE_NOT_ALLOWED",
                    List.of("source not in allowlist"),
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.REJECTED, source, tenant, requestId, traceId, "SOURCE_NOT_ALLOWED"));
        }
        return null;
    }

    /**
     * 完整入站校验管线。
     *
     * @param headers canonical headers
     * @param body 原始 JSON 字符串（用于 payload 上限与签名）
     * @param payload 已解析的 JSON
     * @param nowEpochSeconds 当前时间（秒）
     * @param secret test-only 假 secret
     * @param nonceStore test-only 内存 nonce store
     */
    public Int0ValidationResult validate(
            Map<String, String> headers,
            String body,
            JsonNode payload,
            long nowEpochSeconds,
            String secret,
            Int0NonceStore nonceStore) {

        Int0ValidationResult headerResult = validateHeaders(headers);
        if (headerResult != null) {
            return headerResult;
        }

        String source = headers.get(Int0Contract.H_SOURCE);
        String tenant = headers.get(Int0Contract.H_TENANT);
        String requestId = headers.get(Int0Contract.H_REQUEST_ID);
        String traceId = headers.get(Int0Contract.H_TRACE_ID);

        // payload 上限（413）
        int size = body.getBytes(StandardCharsets.UTF_8).length;
        if (size > Int0Contract.MAX_PAYLOAD_BYTES) {
            return Int0ValidationResult.reject(
                    413,
                    "PAYLOAD_TOO_LARGE",
                    List.of("payload bytes=" + size + " exceeds " + Int0Contract.MAX_PAYLOAD_BYTES),
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.PAYLOAD_TOO_LARGE, source, tenant, requestId, traceId, "PAYLOAD_TOO_LARGE"));
        }

        // timestamp 窗口（401）
        long ts;
        try {
            ts = Long.parseLong(headers.get(Int0Contract.H_TIMESTAMP));
        } catch (NumberFormatException ex) {
            return Int0ValidationResult.reject(
                    401,
                    "TIMESTAMP_INVALID",
                    List.of("timestamp not numeric"),
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.REJECTED, source, tenant, requestId, traceId, "TIMESTAMP_INVALID"));
        }
        if (Math.abs(nowEpochSeconds - ts) > Int0Contract.TIMESTAMP_WINDOW_SECONDS) {
            return Int0ValidationResult.reject(
                    401,
                    "TIMESTAMP_OUT_OF_WINDOW",
                    List.of("timestamp outside +-" + Int0Contract.TIMESTAMP_WINDOW_SECONDS + "s window"),
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.REJECTED, source, tenant, requestId, traceId, "TIMESTAMP_OUT_OF_WINDOW"));
        }

        // HMAC 签名（401）。注意：不把 canonical / signature 原材料放入 details 或 audit。
        String expected = Int0Signing.hmacSha256Hex(Int0Signing.canonical(headers, body), secret);
        if (!Int0Signing.constantTimeEquals(expected, headers.get(Int0Contract.H_SIGNATURE))) {
            return Int0ValidationResult.reject(
                    401,
                    "SIGNATURE_INVALID",
                    List.of("signature mismatch"),
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.SIGNATURE_FAILED, source, tenant, requestId, traceId, "SIGNATURE_INVALID"));
        }

        // nonce 防重放（409）
        boolean firstSeen = nonceStore.registerIfAbsent(
                source, tenant, requestId, headers.get(Int0Contract.H_NONCE));
        if (!firstSeen) {
            return Int0ValidationResult.reject(
                    409,
                    "REPLAY",
                    List.of("nonce replay detected"),
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.REPLAY_REJECTED, source, tenant, requestId, traceId, "REPLAY"));
        }

        // tenant 绑定（403）：header tenant 必须与 payload tenantId 一致
        String payloadTenant = payload.path("tenantId").asText(null);
        if (payloadTenant != null && !payloadTenant.equals(tenant)) {
            return Int0ValidationResult.reject(
                    403,
                    "TENANT_MISMATCH",
                    List.of("header tenant != payload tenantId"),
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.TENANT_MISMATCH, source, tenant, requestId, traceId, "TENANT_MISMATCH"));
        }

        // 禁止字段（400）
        List<String> forbiddenFields = scanForbiddenFields(payload);
        if (!forbiddenFields.isEmpty()) {
            return Int0ValidationResult.reject(
                    400,
                    "FORBIDDEN_FIELD",
                    forbiddenFields,
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.FORBIDDEN_FIELD_REJECTED, source, tenant, requestId, traceId, "FORBIDDEN_FIELD"));
        }

        // 禁止能力（403）
        List<String> forbiddenCaps = scanForbiddenCapabilities(payload);
        if (!forbiddenCaps.isEmpty()) {
            return Int0ValidationResult.reject(
                    403,
                    "FORBIDDEN_CAPABILITY",
                    forbiddenCaps,
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.REJECTED, source, tenant, requestId, traceId, "FORBIDDEN_CAPABILITY"));
        }

        // schema（400）
        List<String> schemaErrors = validateByContractName(payload);
        if (!schemaErrors.isEmpty()) {
            return Int0ValidationResult.reject(
                    400,
                    "SCHEMA",
                    schemaErrors,
                    Int0AuditEvent.rejected(
                            Int0AuditEvent.REJECTED, source, tenant, requestId, traceId, "SCHEMA"));
        }

        return Int0ValidationResult.accept(Int0AuditEvent.received(source, tenant, requestId, traceId));
    }

    /** 递归扫描禁止的敏感字段，返回命中字段路径（不含字段值）。 */
    public List<String> scanForbiddenFields(JsonNode node) {
        List<String> hits = new ArrayList<>();
        scanFieldNames(node, "$", Int0Contract.FORBIDDEN_FIELDS, hits);
        return hits;
    }

    /** 递归扫描禁止的能力 / 动作 token（字段名或字符串值）。 */
    public List<String> scanForbiddenCapabilities(JsonNode node) {
        List<String> hits = new ArrayList<>();
        scanCapabilities(node, "$", hits);
        return hits;
    }

    private void scanFieldNames(JsonNode node, String path, List<String> forbidden, List<String> hits) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String norm = Int0Contract.normalize(entry.getKey());
                for (String token : forbidden) {
                    if (norm.equals(Int0Contract.normalize(token))) {
                        hits.add(path + "." + entry.getKey());
                    }
                }
                scanFieldNames(entry.getValue(), path + "." + entry.getKey(), forbidden, hits);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                scanFieldNames(node.get(i), path + "[" + i + "]", forbidden, hits);
            }
        }
    }

    private void scanCapabilities(JsonNode node, String path, List<String> hits) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                matchCapabilityToken(entry.getKey(), path + "." + entry.getKey() + " (field)", hits);
                scanCapabilities(entry.getValue(), path + "." + entry.getKey(), hits);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                scanCapabilities(node.get(i), path + "[" + i + "]", hits);
            }
        } else if (node.isTextual()) {
            matchCapabilityToken(node.asText(), path + " (value)", hits);
        }
    }

    private void matchCapabilityToken(String candidate, String where, List<String> hits) {
        String norm = Int0Contract.normalize(candidate);
        for (String cap : Int0Contract.FORBIDDEN_CAPABILITIES) {
            if (norm.equals(Int0Contract.normalize(cap))) {
                hits.add(where + " -> " + cap);
            }
        }
    }

    private List<String> validateByContractName(JsonNode payload) {
        String contractName = payload.path("contractName").asText("");
        if ("DHSignalCandidate".equals(contractName)) {
            return validateCandidate(payload);
        }
        if ("NQFeedbackEvent".equals(contractName)) {
            return validateFeedback(payload);
        }
        // 其它（如只读查询）：仅要求 executionIntent 不为 true
        List<String> errors = new ArrayList<>();
        if (payload.path("executionIntent").asBoolean(false)) {
            errors.add("executionIntent must not be true");
        }
        return errors;
    }

    public List<String> validateCandidate(JsonNode payload) {
        List<String> errors = new ArrayList<>();
        for (String field : Int0Contract.CANDIDATE_REQUIRED_FIELDS) {
            if (payload.path(field).isMissingNode() || payload.path(field).isNull()) {
                errors.add("missing field: " + field);
            }
        }
        if (payload.has("confidence")) {
            double confidence = payload.path("confidence").asDouble(-1);
            if (confidence < 0.0 || confidence > 1.0) {
                errors.add("confidence out of [0,1]: " + confidence);
            }
        }
        if (payload.path("executionIntent").asBoolean(true)) {
            errors.add("executionIntent must be false for candidate");
        }
        return errors;
    }

    public List<String> validateFeedback(JsonNode payload) {
        List<String> errors = new ArrayList<>();
        for (String field : Int0Contract.FEEDBACK_REQUIRED_FIELDS) {
            if (payload.path(field).isMissingNode() || payload.path(field).isNull()) {
                errors.add("missing field: " + field);
            }
        }
        if (!"nexus-quant".equals(payload.path("sourceSystem").asText(""))) {
            errors.add("sourceSystem must be 'nexus-quant'");
        }
        String eventType = payload.path("eventType").asText("");
        if (!Int0Contract.ALLOWED_FEEDBACK_EVENT_TYPES.contains(eventType)) {
            errors.add("eventType not in allowed set: " + eventType);
        }
        return errors;
    }

    private static int statusForMissingHeader(String header) {
        // 认证类（签名 / 时间戳 / nonce）-> 401；来源 / 租户 -> 403；幂等 / 追踪 / content-type -> 400
        if (Int0Contract.H_SIGNATURE.equals(header)
                || Int0Contract.H_TIMESTAMP.equals(header)
                || Int0Contract.H_NONCE.equals(header)) {
            return 401;
        }
        if (Int0Contract.H_SOURCE.equals(header) || Int0Contract.H_TENANT.equals(header)) {
            return 403;
        }
        return 400;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
