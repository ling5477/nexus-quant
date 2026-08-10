package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ShadowRunReleaseBindingModeTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final String DIGEST = "a".repeat(64);

    @Test
    void shouldDeriveAllPersistedBindingModes() {
        assertEquals(ShadowRunReleaseBindingMode.LEGACY_UNBOUND, run(null, null).releaseBindingMode());
        assertEquals(ShadowRunReleaseBindingMode.LEGACY_PUBLISH_ONLY, run("pub-1", null).releaseBindingMode());
        assertEquals(ShadowRunReleaseBindingMode.RELEASE_BOUND, run("pub-1", DIGEST).releaseBindingMode());
    }

    @Test
    void shouldRejectInvalidDigestsAndDigestWithoutPublishId() {
        for (String invalid : new String[]{
                "a".repeat(63),
                "a".repeat(65),
                "A".repeat(64),
                "g".repeat(64),
                ""
        }) {
            assertThrows(IllegalArgumentException.class, () -> run("pub-1", invalid), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> run(null, DIGEST));
    }

    private static ShadowRun run(String publishId, String artifactDigest) {
        return new ShadowRun(
                UUID.randomUUID(),
                "strategy-version-1",
                UUID.randomUUID(),
                null,
                publishId,
                artifactDigest,
                null,
                ShadowRunStatus.CREATED,
                NOW,
                NOW.plusSeconds(60),
                JsonNodeFactory.instance.objectNode(),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "request-1",
                "idempotency-" + UUID.randomUUID(),
                "trace-1",
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                0,
                NOW,
                NOW,
                null,
                null,
                null
        );
    }
}
