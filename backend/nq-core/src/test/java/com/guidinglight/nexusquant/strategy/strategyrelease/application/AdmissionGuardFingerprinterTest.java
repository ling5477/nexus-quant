package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Admission guard v1 必须使用稳定 typed encoding，且 NONE/null 与空字段不可碰撞。 */
class AdmissionGuardFingerprinterTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-08-11T12:34:56.123456789Z");
    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final AdmissionGuardFingerprinter fingerprinter = new AdmissionGuardFingerprinter();

    @Test
    void shouldProduceStableLowercaseSha256ForSameTypedFacts() {
        String first = fingerprinter.fingerprint(state(9), facts(null), EVALUATED_AT);
        String second = fingerprinter.fingerprint(state(9), facts(null), EVALUATED_AT);

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void shouldChangeForRevisionTimestampPolicyAndEveryEvidenceIdentity() {
        String baseline = fingerprinter.fingerprint(state(9), facts(null), EVALUATED_AT);

        assertNotEquals(baseline, fingerprinter.fingerprint(state(10), facts(null), EVALUATED_AT));
        assertNotEquals(baseline, fingerprinter.fingerprint(state(9), facts(null), EVALUATED_AT.plusNanos(1)));
        assertNotEquals(baseline, fingerprinter.fingerprint(state(9), facts(
                new StrategyReleaseAdmissionPreviewFacts.ShadowEvidenceIdentity(
                        UUID.fromString("22222222-2222-4222-8222-222222222222"),
                        "PRECHECKING",
                        EVALUATED_AT.minusSeconds(1)
                )
        ), EVALUATED_AT));
    }

    @Test
    void shouldDistinguishNoneFromPresentIdentityContainingEmptyValues() {
        String none = fingerprinter.fingerprint(state(9), facts(null), EVALUATED_AT);
        StrategyReleaseAdmissionPreviewFacts withPresentEmpty = new StrategyReleaseAdmissionPreviewFacts(
                "backtest-run-001",
                validation(),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                new StrategyReleaseAdmissionPreviewFacts.PaperEvidenceIdentity("", "", "", null),
                null,
                null,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                policy()
        );

        assertNotEquals(none, fingerprinter.fingerprint(state(9), withPresentEmpty, EVALUATED_AT));
    }

    private static StrategyReleaseAdmissionState state(long revision) {
        return new StrategyReleaseAdmissionState(
                "publish-001",
                revision,
                1,
                "a".repeat(64),
                "b".repeat(64),
                "strategy-release-manifest.v1",
                EVALUATED_AT.minusSeconds(100),
                EVALUATED_AT.minusSeconds(200),
                EVALUATED_AT.minusSeconds(100)
        );
    }

    private static StrategyReleaseAdmissionPreviewFacts facts(
            StrategyReleaseAdmissionPreviewFacts.ShadowEvidenceIdentity shadow
    ) {
        return new StrategyReleaseAdmissionPreviewFacts(
                "backtest-run-001",
                validation(),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                new StrategyReleaseAdmissionPreviewFacts.PaperEvidenceIdentity(
                        "paper-run-001",
                        "STOPPED",
                        "SIM",
                        EVALUATED_AT.minusSeconds(2)
                ),
                shadow,
                null,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                policy()
        );
    }

    private static LatestDecisionFact validation() {
        return new LatestDecisionFact(
                "strategy-version-001",
                DATASET_ID,
                "evaluation-001",
                "publish-001",
                "paper-run-001",
                null,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                null,
                null,
                EVALUATED_AT.minusSeconds(2),
                EVALUATED_AT.minusSeconds(2)
        );
    }

    private static ShadowRunCreationPlan.SideEffectPolicy policy() {
        return new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, true, true);
    }
}
