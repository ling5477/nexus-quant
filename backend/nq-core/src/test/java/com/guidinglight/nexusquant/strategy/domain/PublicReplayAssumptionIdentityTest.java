package com.guidinglight.nexusquant.strategy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PublicReplayAssumptionIdentityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void canonicalIdentityChangesForRuleFeeAndSlippageButNotPropertyOrderOrDecimalScale() throws Exception {
        var first = mapper.readTree("""
                {"feeRate":"0.0010","slippageBps":"10.0","ruleSha256":"abc",
                 "costSource":"EXPERIMENT_ASSUMPTION"}
                """);
        var reordered = mapper.readTree("""
                {"costSource":"EXPERIMENT_ASSUMPTION","ruleSha256":"abc",
                 "slippageBps":"10","feeRate":"0.001"}
                """);
        String identity = PublicReplayAssumptionIdentity.sha256(first);
        assertEquals(identity, PublicReplayAssumptionIdentity.sha256(reordered));
        for (String changed : new String[] {
                "{\"feeRate\":\"0.002\",\"slippageBps\":\"10\",\"ruleSha256\":\"abc\",\"costSource\":\"EXPERIMENT_ASSUMPTION\"}",
                "{\"feeRate\":\"0.001\",\"slippageBps\":\"11\",\"ruleSha256\":\"abc\",\"costSource\":\"EXPERIMENT_ASSUMPTION\"}",
                "{\"feeRate\":\"0.001\",\"slippageBps\":\"10\",\"ruleSha256\":\"def\",\"costSource\":\"EXPERIMENT_ASSUMPTION\"}"
        }) {
            assertNotEquals(identity, PublicReplayAssumptionIdentity.sha256(mapper.readTree(changed)));
        }
    }
}
