package com.guidinglight.nexusquant.account.infra.okx.readonly;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 对真实 OKX 全账户余额的比较资格；先判来源与范围，再允许数值运算。 */
final class AccountBalanceComparability {
    enum Eligibility {
        UNKNOWN,
        NOT_PROVIDER_EQUIVALENT
    }

    record Decision(Eligibility eligibility, String reason) {
    }

    private AccountBalanceComparability() {
    }

    static Decision assess(List<JdbcAccountSnapshotReader.Snapshot> live, boolean legacyUnknown, Instant at) {
        if (legacyUnknown) {
            return new Decision(Eligibility.UNKNOWN, "LEGACY_SNAPSHOT_ENVIRONMENT_UNKNOWN");
        }
        if (live.isEmpty()) {
            return new Decision(Eligibility.UNKNOWN, "LIVE_SNAPSHOT_MISSING");
        }
        if (live.stream().anyMatch(s -> AccountSnapshotFreshness.evaluate(s, at, null)
                == AccountSnapshotFreshness.State.INVALID_FUTURE_TIME)) {
            return new Decision(Eligibility.UNKNOWN, "CANONICAL_SNAPSHOT_FUTURE_TIMESTAMP");
        }
        if (live.stream().anyMatch(s -> AccountSnapshotFreshness.evaluate(s, at, null)
                == AccountSnapshotFreshness.State.UNKNOWN_PROVENANCE
                || !"LIVE".equals(s.tradeEnv())
                || !"NQ_MANAGED_ACCOUNT".equals(s.balanceScope())
                || s.balanceBasis() == null
                || !Set.of("POSITION_PROJECTION", "LEDGER_CASH_PROJECTION").contains(s.balanceBasis()))) {
            return new Decision(Eligibility.UNKNOWN, "CANONICAL_SNAPSHOT_PROVENANCE_INVALID");
        }
        // 这些 basis 均由本地成交、仓位和记账形成；缺少外部全资产/冻结同步契约。
        return new Decision(Eligibility.NOT_PROVIDER_EQUIVALENT,
                "NQ_MANAGED_PROJECTION_NOT_WHOLE_VENUE_EQUIVALENT");
    }
}
