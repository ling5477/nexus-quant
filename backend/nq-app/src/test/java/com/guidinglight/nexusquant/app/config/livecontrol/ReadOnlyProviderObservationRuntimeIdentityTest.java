package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.app.config.livecontrol.model.ReadOnlyProviderObservationRuntimeIdentity;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadOnlyProviderObservationRuntimeIdentityTest {
    private static final String RELEASE = "nq-abcdef123456-2222222222222222";
    private static final String COMMIT = "abcdef123456" + "1".repeat(28);
    private static final String MANIFEST = "a".repeat(64);

    @Test
    void keepsArtifactSourceAndManifestIdentitiesDistinct() {
        var identity = identity(RELEASE, COMMIT, MANIFEST);
        assertEquals(RELEASE, identity.releaseId());
        assertEquals(COMMIT, identity.sourceCommit());
        assertEquals(MANIFEST, identity.releaseManifestSha256());
        assertNotEquals(identity.releaseId(), identity.sourceCommit());
    }

    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void rejectsInvalidIdentityBeforeRuntimeCanStart(String release, String commit, String manifest) {
        assertThrows(IllegalArgumentException.class, () -> identity(release, commit, manifest));
    }

    static Stream<Arguments> invalidIdentities() {
        return Stream.of(
                Arguments.of(null, COMMIT, MANIFEST),
                Arguments.of(COMMIT, COMMIT, MANIFEST),
                Arguments.of("nq-test-abcdef123456-2222222222222222", COMMIT, MANIFEST),
                Arguments.of("release", COMMIT, MANIFEST),
                Arguments.of(RELEASE.toUpperCase(java.util.Locale.ROOT), COMMIT, MANIFEST),
                Arguments.of(RELEASE + "\n", COMMIT, MANIFEST),
                Arguments.of("nq-abcdef123456-222222222222222", COMMIT, MANIFEST),
                Arguments.of(RELEASE, null, MANIFEST),
                Arguments.of(RELEASE, "invalid", MANIFEST),
                Arguments.of(RELEASE, COMMIT.toUpperCase(java.util.Locale.ROOT), MANIFEST),
                Arguments.of(RELEASE, "1".repeat(40), MANIFEST),
                Arguments.of(RELEASE, COMMIT, null),
                Arguments.of(RELEASE, COMMIT, ""),
                Arguments.of(RELEASE, COMMIT, "a".repeat(63)),
                Arguments.of(RELEASE, COMMIT, "g".repeat(64)),
                Arguments.of(RELEASE, COMMIT, MANIFEST.toUpperCase(java.util.Locale.ROOT)));
    }

    private static ReadOnlyProviderObservationRuntimeIdentity identity(
            String release, String commit, String manifest) {
        return new ReadOnlyProviderObservationRuntimeIdentity(release, commit, manifest,
                ReadOnlyProviderObservationRuntimeIdentity.CAPABILITY, "127.0.0.1", 21);
    }
}
