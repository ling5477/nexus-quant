package com.guidinglight.nexusquant.app.integration0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.app.integration0.support.Int0Contract;
import com.guidinglight.nexusquant.app.integration0.support.Int0ContractValidator;
import com.guidinglight.nexusquant.app.integration0.support.Int0NonceStore;
import com.guidinglight.nexusquant.app.integration0.support.Int0RequestFactory;
import com.guidinglight.nexusquant.app.integration0.support.Int0ValidationResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * NQ-DH Integration-0 契约结构类 contract test。
 *
 * <p>覆盖矩阵：INT0-T02（可开放能力）、INT0-T03（header 缺失）、INT0-T07（tenant mismatch）、
 * INT0-T08（payload 超 64 KiB）、INT0-T11（candidate schema）、INT0-T12（feedback schema）。
 *
 * <p>全部为 test-only 内存校验，不连接真实 NQ 入站端点、真实 DH client、真实交易所或真实凭证。
 */
class NqDhIntegration0ContractValidationTest {

    private final Int0ContractValidator validator = new Int0ContractValidator();

    private long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private Int0ValidationResult validateFixture(String fixture, String nonce) {
        JsonNode payload = Int0RequestFactory.loadFixture(fixture);
        String body = Int0RequestFactory.toJson(payload);
        Map<String, String> headers = Int0RequestFactory.validHeaders(body, now(), nonce);
        return validator.validate(headers, body, payload, now(), Int0Contract.FAKE_SECRET, new Int0NonceStore());
    }

    /** INT0-T02：可开放能力（候选信号、只读查询）通过校验且不含禁止能力。 */
    @Test
    void int0T02_allowedCapabilities_areAcceptedWithoutForbiddenActions() {
        Int0ValidationResult candidate = validateFixture("fx-candidate-valid.json", "nonce-int0-t02a");
        assertTrue(candidate.accepted(), "valid candidate must be accepted");
        assertEquals(200, candidate.statusCode());

        Int0ValidationResult readonly = validateFixture("fx-readonly-query.json", "nonce-int0-t02b");
        assertTrue(readonly.accepted(), "read-only query must be accepted");

        JsonNode candidatePayload = Int0RequestFactory.loadFixture("fx-candidate-valid.json");
        assertTrue(
                validator.scanForbiddenCapabilities(candidatePayload).isEmpty(),
                "allowed candidate must not contain forbidden capabilities");
    }

    /** INT0-T03：缺任一 required header 必须拒绝，并按冻结状态码映射。 */
    @Test
    void int0T03_missingRequiredHeader_isRejected() {
        JsonNode payload = Int0RequestFactory.loadFixture("fx-candidate-valid.json");
        String body = Int0RequestFactory.toJson(payload);
        long ts = now();

        for (String header : Int0Contract.REQUIRED_HEADERS) {
            Map<String, String> headers =
                    new LinkedHashMap<>(Int0RequestFactory.validHeaders(body, ts, "nonce-int0-t03"));
            headers.remove(header);
            Int0ValidationResult result =
                    validator.validate(headers, body, payload, ts, Int0Contract.FAKE_SECRET, new Int0NonceStore());
            assertFalse(result.accepted(), "missing header must be rejected: " + header);
            int expected = expectedStatusForMissing(header);
            assertEquals(expected, result.statusCode(), "status for missing " + header);
        }
    }

    private int expectedStatusForMissing(String header) {
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

    /** INT0-T07：header tenant 与 payload tenantId 不一致必须 403。 */
    @Test
    void int0T07_tenantMismatch_isRejected() {
        Int0ValidationResult result = validateFixture("fx-tenant-mismatch.json", "nonce-int0-t07");
        assertFalse(result.accepted());
        assertEquals(403, result.statusCode());
        assertEquals("TENANT_MISMATCH", result.errorCategory());
    }

    /** INT0-T08：payload 超 64 KiB 必须 413，不截断接受。 */
    @Test
    void int0T08_oversizePayload_isRejected() {
        // 动态生成超过 64 KiB 的 payload（rationaleSummary 填充，仍为脱敏假数据）。
        var node = Int0RequestFactory.mapper().createObjectNode();
        node.put("contractName", "DHSignalCandidate");
        node.put("tenantId", Int0Contract.FAKE_TENANT);
        node.put("filler", "x".repeat(70 * 1024));
        String body = Int0RequestFactory.toJson(node);
        assertTrue(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > Int0Contract.MAX_PAYLOAD_BYTES);

        long ts = now();
        Map<String, String> headers = Int0RequestFactory.validHeaders(body, ts, "nonce-int0-t08");
        Int0ValidationResult result =
                validator.validate(headers, body, node, ts, Int0Contract.FAKE_SECRET, new Int0NonceStore());
        assertFalse(result.accepted());
        assertEquals(413, result.statusCode());
        assertEquals("PAYLOAD_TOO_LARGE", result.errorCategory());
    }

    /** INT0-T11：DHSignalCandidate schema 合法通过、非法拒绝（缺字段/confidence 越界/executionIntent/禁止字段）。 */
    @Test
    void int0T11_candidateSchemaValidation() {
        JsonNode valid = Int0RequestFactory.loadFixture("fx-candidate-valid.json");
        assertTrue(validator.validateCandidate(valid).isEmpty(), "valid candidate must pass schema");

        JsonNode invalid = Int0RequestFactory.loadFixture("fx-candidate-invalid.json");
        assertFalse(validator.validateCandidate(invalid).isEmpty(), "invalid candidate must fail schema");

        // 端到端：非法 candidate 必须被整体拒绝（禁止字段先命中 400）。
        Int0ValidationResult result = validateFixture("fx-candidate-invalid.json", "nonce-int0-t11");
        assertFalse(result.accepted());
        assertEquals(400, result.statusCode());
    }

    /** INT0-T12：NQFeedbackEvent schema 合法通过、非法拒绝（sourceSystem 伪造/eventType 未知/禁止字段）。 */
    @Test
    void int0T12_feedbackSchemaValidation() {
        JsonNode valid = Int0RequestFactory.loadFixture("fx-feedback-valid.json");
        assertTrue(validator.validateFeedback(valid).isEmpty(), "valid feedback must pass schema");

        JsonNode invalid = Int0RequestFactory.loadFixture("fx-feedback-invalid.json");
        assertFalse(validator.validateFeedback(invalid).isEmpty(), "invalid feedback must fail schema");

        Int0ValidationResult result = validateFixture("fx-feedback-invalid.json", "nonce-int0-t12");
        assertFalse(result.accepted());
        assertEquals(400, result.statusCode());
    }
}
