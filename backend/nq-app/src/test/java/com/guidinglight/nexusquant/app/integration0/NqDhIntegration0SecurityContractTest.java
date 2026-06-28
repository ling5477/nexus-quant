package com.guidinglight.nexusquant.app.integration0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.app.integration0.support.Int0AuditEvent;
import com.guidinglight.nexusquant.app.integration0.support.Int0Contract;
import com.guidinglight.nexusquant.app.integration0.support.Int0ContractValidator;
import com.guidinglight.nexusquant.app.integration0.support.Int0CredentialAccessTracker;
import com.guidinglight.nexusquant.app.integration0.support.Int0NonceStore;
import com.guidinglight.nexusquant.app.integration0.support.Int0RequestFactory;
import com.guidinglight.nexusquant.app.integration0.support.Int0ValidationResult;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * NQ-DH Integration-0 安全类 contract test。
 *
 * <p>覆盖矩阵：INT0-T01（禁止能力）、INT0-T04（HMAC）、INT0-T05（timestamp 窗口）、
 * INT0-T06（nonce replay）、INT0-T09（forbidden field）、INT0-T10（raw prompt/context）、
 * INT0-T13（audit required）、INT0-T15（no credential access）。
 *
 * <p>全部 test-only：固定假 secret、假 tenant/source；不读取 .env、不读取真实凭证、不发真实 HTTP。
 */
class NqDhIntegration0SecurityContractTest {

    private final Int0ContractValidator validator = new Int0ContractValidator();

    private long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private Int0ValidationResult validate(
            JsonNode payload, Map<String, String> headers, long checkNow, Int0NonceStore store) {
        String body = Int0RequestFactory.toJson(payload);
        return validator.validate(headers, body, payload, checkNow, Int0Contract.FAKE_SECRET, store);
    }

    /** INT0-T01：禁止能力。候选/只读 fixture 不含禁止能力；forbidden-calls fixture 必须被扫描命中并整体拒绝。 */
    @Test
    void int0T01_forbiddenCapabilities() {
        assertTrue(
                validator
                        .scanForbiddenCapabilities(Int0RequestFactory.loadFixture("fx-candidate-valid.json"))
                        .isEmpty(),
                "allowed candidate must not contain forbidden capabilities");
        assertTrue(
                validator
                        .scanForbiddenCapabilities(Int0RequestFactory.loadFixture("fx-readonly-query.json"))
                        .isEmpty(),
                "read-only query must not contain forbidden capabilities");

        JsonNode forbidden = Int0RequestFactory.loadFixture("fx-forbidden-calls.json");
        assertFalse(
                validator.scanForbiddenCapabilities(forbidden).isEmpty(),
                "forbidden-calls fixture must be flagged");

        String body = Int0RequestFactory.toJson(forbidden);
        long ts = now();
        Map<String, String> headers = Int0RequestFactory.validHeaders(body, ts, "nonce-int0-t01");
        Int0ValidationResult result =
                validator.validate(headers, body, forbidden, ts, Int0Contract.FAKE_SECRET, new Int0NonceStore());
        assertFalse(result.accepted());
        assertEquals(403, result.statusCode());
        assertEquals("FORBIDDEN_CAPABILITY", result.errorCategory());
    }

    /** INT0-T04：HMAC 正确签名通过、错误签名拒绝；签名原材料/secret 不出现在结果或审计中。 */
    @Test
    void int0T04_hmacSignature() {
        JsonNode payload = Int0RequestFactory.loadFixture("fx-candidate-valid.json");
        String body = Int0RequestFactory.toJson(payload);
        long ts = now();

        Map<String, String> good = Int0RequestFactory.validHeaders(body, ts, "nonce-int0-t04a");
        Int0ValidationResult okResult =
                validator.validate(good, body, payload, ts, Int0Contract.FAKE_SECRET, new Int0NonceStore());
        assertTrue(okResult.accepted(), "correct signature must be accepted");

        Map<String, String> bad = new java.util.LinkedHashMap<>(good);
        bad.put(Int0Contract.H_SIGNATURE, "deadbeefdeadbeefdeadbeef");
        Int0ValidationResult badResult =
                validator.validate(bad, body, payload, ts, Int0Contract.FAKE_SECRET, new Int0NonceStore());
        assertFalse(badResult.accepted(), "bad signature must be rejected");
        assertEquals(401, badResult.statusCode());
        assertEquals(Int0AuditEvent.SIGNATURE_FAILED, badResult.auditEvent().eventType());

        // 签名原材料 / secret 不得出现在结果或审计中（不打印到日志）。
        String rendered = badResult.toString() + badResult.auditEvent().toString() + badResult.details();
        assertFalse(rendered.contains(Int0Contract.FAKE_SECRET), "secret must not leak into result");
        assertFalse(rendered.contains(Int0Signing_canonical(good, body)), "canonical must not leak into result");
    }

    private String Int0Signing_canonical(Map<String, String> headers, String body) {
        return com.guidinglight.nexusquant.app.integration0.support.Int0Signing.canonical(headers, body);
    }

    /** INT0-T05：timestamp 窗口。RFC3339 UTC Z 窗口内通过；过去超窗与未来超窗均拒绝。 */
    @Test
    void int0T05_timestampWindow() {
        JsonNode payload = Int0RequestFactory.loadFixture("fx-candidate-valid.json");
        String body = Int0RequestFactory.toJson(payload);
        long checkNow = now();

        Map<String, String> inWindow = Int0RequestFactory.validHeaders(body, checkNow, "nonce-int0-t05a");
        assertEquals(
                Instant.ofEpochSecond(checkNow).toString(),
                inWindow.get(Int0Contract.H_TIMESTAMP),
                "valid fixture timestamp must use RFC3339 UTC Z");
        assertTrue(
                validate(payload, inWindow, checkNow, new Int0NonceStore()).accepted(),
                "in-window timestamp must be accepted");

        long pastTs = checkNow - (Int0Contract.TIMESTAMP_WINDOW_SECONDS + 1);
        Map<String, String> past = Int0RequestFactory.validHeaders(body, pastTs, "nonce-int0-t05b");
        Int0ValidationResult pastResult = validate(payload, past, checkNow, new Int0NonceStore());
        assertFalse(pastResult.accepted());
        assertEquals(401, pastResult.statusCode());

        long futureTs = checkNow + (Int0Contract.TIMESTAMP_WINDOW_SECONDS + 1);
        Map<String, String> future = Int0RequestFactory.validHeaders(body, futureTs, "nonce-int0-t05c");
        Int0ValidationResult futureResult = validate(payload, future, checkNow, new Int0NonceStore());
        assertFalse(futureResult.accepted());
        assertEquals(401, futureResult.statusCode());
        assertEquals("TIMESTAMP_OUT_OF_WINDOW", futureResult.errorCategory());
    }

    /** INT0-T05：epoch seconds / epoch milliseconds / 数字时区偏移都不是 canonical timestamp，必须 fail-closed。 */
    @Test
    void int0T05_timestampFormatRejectsEpochAndOffsetValues() {
        JsonNode payload = Int0RequestFactory.loadFixture("fx-candidate-valid.json");
        String body = Int0RequestFactory.toJson(payload);
        long checkNow = now();

        assertTimestampInvalid(payload, body, checkNow, Long.toString(checkNow), "epoch seconds must be rejected");
        assertTimestampInvalid(
                payload, body, checkNow, Long.toString(checkNow * 1000L), "epoch milliseconds must be rejected");
        assertTimestampInvalid(
                payload,
                body,
                checkNow,
                "2026-06-15T20:34:56+08:00",
                "numeric timezone offsets must be rejected");
    }

    private void assertTimestampInvalid(
            JsonNode payload, String body, long checkNow, String timestampValue, String message) {
        Map<String, String> headers =
                new java.util.LinkedHashMap<>(Int0RequestFactory.validHeaders(body, checkNow, "nonce-" + timestampValue));
        headers.put(Int0Contract.H_TIMESTAMP, timestampValue);
        Int0ValidationResult result = validate(payload, headers, checkNow, new Int0NonceStore());
        assertFalse(result.accepted(), message);
        assertEquals(401, result.statusCode());
        assertEquals("TIMESTAMP_INVALID", result.errorCategory());
    }

    /**
     * INT0-T06：nonce replay。第一次通过，同 source+tenant+requestId+nonce 第二次拒绝。
     *
     * <p>注意：本测试使用 test-only 内存 nonce store。Integration-1 前必须实现持久化 / 集中缓存 nonce
     * （DH P1-4 residual: replay nonce persistence），否则多实例防重放失效。
     */
    @Test
    void int0T06_nonceReplay() {
        JsonNode payload = Int0RequestFactory.loadFixture("fx-candidate-valid.json");
        String body = Int0RequestFactory.toJson(payload);
        Int0NonceStore sharedStore = new Int0NonceStore();

        long ts1 = now();
        Map<String, String> first = Int0RequestFactory.validHeaders(body, ts1, "nonce-int0-t06");
        Int0ValidationResult firstResult =
                validator.validate(first, body, payload, ts1, Int0Contract.FAKE_SECRET, sharedStore);
        assertTrue(firstResult.accepted(), "first nonce use must be accepted");

        long ts2 = now();
        Map<String, String> replay = Int0RequestFactory.validHeaders(body, ts2, "nonce-int0-t06");
        Int0ValidationResult replayResult =
                validator.validate(replay, body, payload, ts2, Int0Contract.FAKE_SECRET, sharedStore);
        assertFalse(replayResult.accepted(), "replayed nonce must be rejected");
        assertEquals(409, replayResult.statusCode());
        assertEquals(Int0AuditEvent.REPLAY_REJECTED, replayResult.auditEvent().eventType());
    }

    /** INT0-T09：禁止字段递归扫描命中并整体 400 拒绝。 */
    @Test
    void int0T09_forbiddenFieldRejection() {
        JsonNode payload = Int0RequestFactory.loadFixture("fx-forbidden-fields.json");
        assertTrue(
                validator.scanForbiddenFields(payload).size() >= 18,
                "forbidden-fields fixture must flag the full forbidden token set");

        String body = Int0RequestFactory.toJson(payload);
        long ts = now();
        Map<String, String> headers = Int0RequestFactory.validHeaders(body, ts, "nonce-int0-t09");
        Int0ValidationResult result =
                validator.validate(headers, body, payload, ts, Int0Contract.FAKE_SECRET, new Int0NonceStore());
        assertFalse(result.accepted());
        assertEquals(400, result.statusCode());
        assertEquals("FORBIDDEN_FIELD", result.errorCategory());
    }

    /** INT0-T10：raw prompt/context 字段必须被拒绝。 */
    @Test
    void int0T10_rawPromptContextRejection() {
        JsonNode payload = Int0RequestFactory.loadFixture("fx-raw-prompt-context.json");
        var hits = validator.scanForbiddenFields(payload);
        assertTrue(hits.stream().anyMatch(h -> h.contains("fullPrompt")), "fullPrompt must be flagged");
        assertTrue(hits.stream().anyMatch(h -> h.contains("fullContext")), "fullContext must be flagged");
        assertTrue(hits.stream().anyMatch(h -> h.contains("rawPrompt")), "rawPrompt must be flagged");
        assertTrue(hits.stream().anyMatch(h -> h.contains("rawContext")), "rawContext must be flagged");

        String body = Int0RequestFactory.toJson(payload);
        long ts = now();
        Map<String, String> headers = Int0RequestFactory.validHeaders(body, ts, "nonce-int0-t10");
        Int0ValidationResult result =
                validator.validate(headers, body, payload, ts, Int0Contract.FAKE_SECRET, new Int0NonceStore());
        assertFalse(result.accepted());
        assertEquals(400, result.statusCode());
    }

    /** INT0-T13：accepted/rejected 都产生 test-only audit event，且 audit 不含敏感值。 */
    @Test
    void int0T13_auditEventShapeAndNoSensitiveLeak() {
        JsonNode accepted = Int0RequestFactory.loadFixture("fx-candidate-valid.json");
        String acceptedBody = Int0RequestFactory.toJson(accepted);
        long ts = now();
        Int0ValidationResult acceptResult =
                validator.validate(
                        Int0RequestFactory.validHeaders(acceptedBody, ts, "nonce-int0-t13a"),
                        acceptedBody,
                        accepted,
                        ts,
                        Int0Contract.FAKE_SECRET,
                        new Int0NonceStore());
        assertNotNull(acceptResult.auditEvent());
        assertEquals(Int0AuditEvent.RECEIVED, acceptResult.auditEvent().eventType());
        assertNotNull(acceptResult.auditEvent().traceId());
        assertNotNull(acceptResult.auditEvent().requestId());
        assertNotNull(acceptResult.auditEvent().tenantId());

        JsonNode rejected = Int0RequestFactory.loadFixture("fx-forbidden-fields.json");
        String rejectedBody = Int0RequestFactory.toJson(rejected);
        long ts2 = now();
        Int0ValidationResult rejectResult =
                validator.validate(
                        Int0RequestFactory.validHeaders(rejectedBody, ts2, "nonce-int0-t13b"),
                        rejectedBody,
                        rejected,
                        ts2,
                        Int0Contract.FAKE_SECRET,
                        new Int0NonceStore());
        assertNotNull(rejectResult.auditEvent());
        assertFalse(rejectResult.auditEvent().accepted());

        String renderedAudit = rejectResult.auditEvent().toString();
        assertFalse(renderedAudit.contains("FAKE-PLACEHOLDER"), "audit must not carry payload field values");
        assertFalse(renderedAudit.contains(Int0Contract.FAKE_SECRET), "audit must not carry secret");
    }

    /** INT0-T15：contract validation 不访问任何凭证；fake secret 为固定假值。 */
    @Test
    void int0T15_noCredentialAccess() {
        Int0CredentialAccessTracker tracker = new Int0CredentialAccessTracker();

        // 跑一组校验（accept + 各类 reject），validation 管线不应触达凭证。
        for (String fixture :
                java.util.List.of(
                        "fx-candidate-valid.json",
                        "fx-feedback-valid.json",
                        "fx-forbidden-fields.json",
                        "fx-readonly-query.json")) {
            JsonNode payload = Int0RequestFactory.loadFixture(fixture);
            String body = Int0RequestFactory.toJson(payload);
            long ts = now();
            validator.validate(
                    Int0RequestFactory.validHeaders(body, ts, "nonce-int0-t15-" + fixture),
                    body,
                    payload,
                    ts,
                    Int0Contract.FAKE_SECRET,
                    new Int0NonceStore());
        }

        assertEquals(0, tracker.accessCount(), "contract validation must not access any credential");
        assertEquals("int0-test-secret", Int0Contract.FAKE_SECRET, "must use fixed fake secret");
    }
}
