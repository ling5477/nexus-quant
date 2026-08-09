package com.guidinglight.nexusquant.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityPropertyResolverTest {

    private static final String STABLE_KEY = "nq.okx.capability.enabled";
    private static final String LEGACY_KEY = "nq.gatew.capability.enabled";

    @Test
    void stableKeyAloneTakesEffect() {
        MockEnvironment environment = new MockEnvironment().withProperty(STABLE_KEY, "stable");

        assertEquals("stable", CapabilityPropertyResolver.stableFirst(
                environment,
                STABLE_KEY,
                LEGACY_KEY,
                "default"
        ));
    }

    @Test
    void legacyKeyAloneRemainsCompatible() {
        MockEnvironment environment = new MockEnvironment().withProperty(LEGACY_KEY, "legacy");

        assertEquals("legacy", CapabilityPropertyResolver.stableFirst(
                environment,
                STABLE_KEY,
                LEGACY_KEY,
                "default"
        ));
    }

    @Test
    void stableKeyWinsForNonSafetyConflict() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(STABLE_KEY, "stable")
                .withProperty(LEGACY_KEY, "legacy");

        assertEquals("stable", CapabilityPropertyResolver.stableFirst(
                environment,
                STABLE_KEY,
                LEGACY_KEY,
                "default"
        ));
    }

    @Test
    void safetyBooleanConflictFailsClosed() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(STABLE_KEY, "true")
                .withProperty(LEGACY_KEY, "false");

        assertFalse(CapabilityPropertyResolver.matchesExactBoolean(
                environment,
                STABLE_KEY,
                LEGACY_KEY,
                true
        ));
    }

    @Test
    void matchingStableAndLegacySafetyValuesAreAccepted() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(STABLE_KEY, "true")
                .withProperty(LEGACY_KEY, "true");

        assertTrue(CapabilityPropertyResolver.matchesExactBoolean(
                environment,
                STABLE_KEY,
                LEGACY_KEY,
                true
        ));
    }
}
