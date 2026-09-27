package com.guidinglight.nexusquant.account.infra.okx.readonly;

import java.time.Duration;
import java.time.Instant;

/** 区分源事件年龄、投影生成时间和与交易所事实比较所需的同步合同。 */
final class AccountSnapshotFreshness {
    enum State {
        UNKNOWN_PROVENANCE,
        INVALID_FUTURE_TIME,
        EXPLICIT_REFRESH_OVERDUE,
        CURRENT_PROJECTION_VENUE_FRESHNESS_UNPROVEN
    }

    private AccountSnapshotFreshness() {
    }

    static State evaluate(JdbcAccountSnapshotReader.Snapshot snapshot, Instant now, Duration refreshCadence) {
        if (snapshot == null || snapshot.sourceEventAt() == null || snapshot.recordedAt() == null
                || snapshot.tradeEnv() == null || snapshot.balanceBasis() == null || snapshot.balanceScope() == null) {
            return State.UNKNOWN_PROVENANCE;
        }
        if (snapshot.sourceEventAt().isAfter(now) || snapshot.recordedAt().isAfter(now)) {
            return State.INVALID_FUTURE_TIME;
        }
        // 无刷新合同不能根据旧成交时间或长时间没有交易推断投影过期。
        if (refreshCadence != null && refreshCadence.compareTo(Duration.ZERO) > 0
                && snapshot.recordedAt().isBefore(now.minus(refreshCadence))) {
            return State.EXPLICIT_REFRESH_OVERDUE;
        }
        return State.CURRENT_PROJECTION_VENUE_FRESHNESS_UNPROVEN;
    }
}
