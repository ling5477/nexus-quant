package com.guidinglight.nexusquant.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class StableCapabilityPropertyResolverTest {
    @Test
    void stableValueAndDefaultRemainAvailable() {
        var environment = new MockEnvironment().withProperty("nq.okx.capability.timeout", "PT2S");
        assertEquals("PT2S", StableCapabilityPropertyResolver.value(environment, "nq.okx.capability.timeout", "PT5S"));
        assertEquals("PT5S", StableCapabilityPropertyResolver.value(environment, "nq.okx.capability.missing", "PT5S"));
    }

    @Test
    void safetyBooleansRequireExplicitExactValues() {
        var environment = new MockEnvironment();
        String key = "nq.okx.capability.enabled";
        assertFalse(StableCapabilityPropertyResolver.matchesExactBoolean(environment, key, false));
        for (String invalid : new String[]{"", "yes", "0", "invalid"}) {
            environment.setProperty(key, invalid);
            assertFalse(StableCapabilityPropertyResolver.matchesExactBoolean(environment, key, true));
            assertFalse(StableCapabilityPropertyResolver.matchesExactBoolean(environment, key, false));
        }
        environment.setProperty(key, " TRUE ");
        assertTrue(StableCapabilityPropertyResolver.matchesExactBoolean(environment, key, true));
        environment.setProperty(key, "false");
        assertTrue(StableCapabilityPropertyResolver.matchesExactBoolean(environment, key, false));
    }
}
