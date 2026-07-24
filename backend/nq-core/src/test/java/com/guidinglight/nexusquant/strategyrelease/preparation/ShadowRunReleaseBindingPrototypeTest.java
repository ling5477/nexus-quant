package com.guidinglight.nexusquant.strategyrelease.preparation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 发布锚点与 artifact digest 的纯领域合同测试；不创建 Shadow Run 或数据库事实。 */
class ShadowRunReleaseBindingPrototypeTest {

    private static final String PUBLISH_ID = "publish-2026-alpha";
    private static final String DIGEST = "a".repeat(64);

    @Test
    void shouldClassifyAllAllowedBindingModes() {
        ShadowRunReleaseBindingPrototype unbound = ShadowRunReleaseBindingPrototype.legacyUnbound();
        ShadowRunReleaseBindingPrototype publishOnly =
                ShadowRunReleaseBindingPrototype.legacyPublishOnly(PUBLISH_ID);
        ShadowRunReleaseBindingPrototype releaseBound =
                ShadowRunReleaseBindingPrototype.releaseBound(PUBLISH_ID, DIGEST);

        assertEquals(ShadowRunReleaseBindingMode.LEGACY_UNBOUND, unbound.bindingMode());
        assertEquals(ShadowRunReleaseBindingMode.LEGACY_PUBLISH_ONLY, publishOnly.bindingMode());
        assertEquals(ShadowRunReleaseBindingMode.RELEASE_BOUND, releaseBound.bindingMode());
        assertFalse(unbound.eligibleForFutureAdmission());
        assertFalse(publishOnly.eligibleForFutureAdmission());
        assertTrue(releaseBound.eligibleForFutureAdmission());
    }

    @Test
    void shouldRejectDigestWithoutPublishAnchor() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ShadowRunReleaseBindingPrototype(
                        null,
                        DIGEST,
                        ShadowRunReleaseBindingMode.RELEASE_BOUND
                )
        );

        assertEquals("artifactDigest requires publishRecordId", exception.getMessage());
    }

    @Test
    void shouldAcceptNonUuidPublishIdBecausePublishRecordIdIsVarcharBusinessIdentity() {
        ShadowRunReleaseBindingPrototype binding =
                ShadowRunReleaseBindingPrototype.legacyPublishOnly("release-alpha-001");

        assertEquals("release-alpha-001", binding.publishRecordId());
        assertEquals(ShadowRunReleaseBindingMode.LEGACY_PUBLISH_ONLY, binding.bindingMode());
    }

    @Test
    void shouldRejectBlankOversizedControlSensitiveAndPathLikePublishIds() {
        List<String> invalidPublishIds = List.of(
                " ",
                "p".repeat(129),
                "publish\n001",
                "credential-reference",
                "C:\\publish-001",
                "../publish-001"
        );

        for (String invalidPublishId : invalidPublishIds) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ShadowRunReleaseBindingPrototype.legacyPublishOnly(invalidPublishId),
                    invalidPublishId
            );
        }
    }

    @Test
    void shouldRejectInvalidArtifactDigestVariants() {
        List<String> invalidDigests = List.of(
                " ",
                "a".repeat(63),
                "a".repeat(65),
                "A".repeat(64),
                "g".repeat(64)
        );

        for (String invalidDigest : invalidDigests) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ShadowRunReleaseBindingPrototype.releaseBound(PUBLISH_ID, invalidDigest),
                    invalidDigest
            );
        }
    }

    @Test
    void shouldKeepBindingImmutableAndRejectUpgradeOrDowngrade() {
        ShadowRunReleaseBindingPrototype unbound = ShadowRunReleaseBindingPrototype.legacyUnbound();
        ShadowRunReleaseBindingPrototype releaseBound =
                ShadowRunReleaseBindingPrototype.releaseBound(PUBLISH_ID, DIGEST);
        ShadowRunReleaseBindingPrototype sameReleaseBound =
                ShadowRunReleaseBindingPrototype.releaseBound(PUBLISH_ID, DIGEST);

        assertDoesNotThrow(() -> ShadowRunReleaseBindingPrototype.requireUnchanged(releaseBound, sameReleaseBound));
        assertThrows(
                IllegalStateException.class,
                () -> ShadowRunReleaseBindingPrototype.requireUnchanged(unbound, releaseBound)
        );
        assertThrows(
                IllegalStateException.class,
                () -> ShadowRunReleaseBindingPrototype.requireUnchanged(releaseBound, unbound)
        );
        assertThrows(
                IllegalStateException.class,
                () -> ShadowRunReleaseBindingPrototype.requireUnchanged(
                        releaseBound,
                        ShadowRunReleaseBindingPrototype.releaseBound(PUBLISH_ID, "b".repeat(64))
                )
        );
    }

    @Test
    void shouldAllowMultipleCreationIdentitiesForTheSamePublishAndDigest() {
        ShadowRunReleaseBindingPrototype binding =
                ShadowRunReleaseBindingPrototype.releaseBound(PUBLISH_ID, DIGEST);
        ShadowRunCreationIdentityPrototype first = new ShadowRunCreationIdentityPrototype("shadow-create-001", binding);
        ShadowRunCreationIdentityPrototype second = new ShadowRunCreationIdentityPrototype("shadow-create-002", binding);

        assertEquals(first.releaseBinding(), second.releaseBinding());
        assertNotEquals(first.creationDeduplicationKey(), second.creationDeduplicationKey());
        assertEquals("shadow-create-001", first.creationDeduplicationKey());
        assertEquals("shadow-create-002", second.creationDeduplicationKey());
    }

    @Test
    void shouldKeepProvenanceModelFreeOfSensitiveTradingFieldsAndDiagnosticOnly() {
        ShadowRunReleaseBindingPrototype binding =
                ShadowRunReleaseBindingPrototype.releaseBound(PUBLISH_ID, DIGEST);
        ShadowRunCreationIdentityPrototype creation = new ShadowRunCreationIdentityPrototype("shadow-create-001", binding);
        List<String> componentNames = Arrays.stream(ShadowRunReleaseBindingPrototype.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        List.of("credential", "account", "order", "absolutePath", "privateEndpoint")
                .forEach(forbidden -> assertFalse(componentNames.contains(forbidden), forbidden));
        assertTrue(binding.diagnosticOnly());
        assertTrue(binding.notTradingAuthorization());
        assertTrue(binding.liveDisabled());
        assertTrue(creation.diagnosticOnly());
        assertSame(binding, creation.releaseBinding());
    }
}
