package com.guidinglight.nexusquant.account.infra.okx.readonly;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountBalanceComparabilityTest {
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    @Test
    void liveManagedProjectionIsExplicitlyNotWholeVenueEquivalent() {
        var decision = AccountBalanceComparability.assess(List.of(snapshot("LIVE", "LEDGER_CASH_PROJECTION",
                "NQ_MANAGED_ACCOUNT")), false, NOW);
        assertEquals(AccountBalanceComparability.Eligibility.NOT_PROVIDER_EQUIVALENT, decision.eligibility());
    }

    @Test
    void simLegacyAndUnknownBasisCannotEnterLiveComparison() {
        assertEquals(AccountBalanceComparability.Eligibility.UNKNOWN,
                AccountBalanceComparability.assess(List.of(snapshot("SIM", "LEDGER_CASH_PROJECTION",
                        "NQ_MANAGED_ACCOUNT")), false, NOW).eligibility());
        assertEquals("LEGACY_SNAPSHOT_ENVIRONMENT_UNKNOWN",
                AccountBalanceComparability.assess(List.of(snapshot("LIVE", "LEDGER_CASH_PROJECTION",
                        "NQ_MANAGED_ACCOUNT")), true, NOW).reason());
        assertEquals(AccountBalanceComparability.Eligibility.UNKNOWN,
                AccountBalanceComparability.assess(List.of(snapshot("LIVE", null,
                        "NQ_MANAGED_ACCOUNT")), false, NOW).eligibility());
        assertEquals(AccountBalanceComparability.Eligibility.UNKNOWN,
                AccountBalanceComparability.assess(List.of(snapshot("LIVE", "POSITION_PROJECTION",
                        "WHOLE_VENUE_ACCOUNT")), false, NOW).eligibility());
    }

    private static JdbcAccountSnapshotReader.Snapshot snapshot(String env, String basis, String scope) {
        return new JdbcAccountSnapshotReader.Snapshot(1, "BTC", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, NOW.minusSeconds(60), NOW.minusSeconds(1), env, basis, scope);
    }
}
