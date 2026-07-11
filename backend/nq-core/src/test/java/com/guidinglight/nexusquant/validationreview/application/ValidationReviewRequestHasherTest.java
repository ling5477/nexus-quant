package com.guidinglight.nexusquant.validationreview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** GateV-2 canonical request hash 的顺序、换行和敏感输入回归。 */
class ValidationReviewRequestHasherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ValidationReviewRequestHasher hasher = new ValidationReviewRequestHasher(objectMapper);
    private final UUID caseId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void shouldRemainStableAcrossMapOrderAndLineEndings() {
        ObjectNode first = objectMapper.createObjectNode();
        first.put("zeta", 2);
        first.set("nested", objectMapper.createObjectNode().put("b", true).put("a", "safe"));
        ObjectNode second = objectMapper.createObjectNode();
        second.set("nested", objectMapper.createObjectNode().put("a", "safe").put("b", true));
        second.put("zeta", 2);

        String firstHash = hasher.canonicalize(
                caseId,
                ValidationReviewAction.ACKNOWLEDGE,
                3,
                "line one\r\nline two",
                first
        ).requestHash();
        String secondHash = hasher.canonicalize(
                caseId,
                ValidationReviewAction.ACKNOWLEDGE,
                3,
                "line one\nline two",
                second
        ).requestHash();

        assertEquals(firstHash, secondHash);
        assertEquals(64, firstHash.length());
    }

    @Test
    void shouldChangeWhenLogicalRequestChangesAndRejectSensitiveMetadata() {
        ObjectNode safe = objectMapper.createObjectNode().put("note", "local diagnosis");
        assertNotEquals(
                hasher.canonicalize(caseId, ValidationReviewAction.ACKNOWLEDGE, 0, "review", safe).requestHash(),
                hasher.canonicalize(caseId, ValidationReviewAction.ESCALATE, 0, "review", safe).requestHash()
        );
        ObjectNode unsafe = objectMapper.createObjectNode().put("apiKey", "redacted");
        assertThrows(IllegalArgumentException.class, () -> hasher.canonicalize(
                caseId,
                ValidationReviewAction.ACKNOWLEDGE,
                0,
                "review",
                unsafe
        ));
    }
}
