package com.guidinglight.nexusquant.trading.application.reconciliation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 一次 bounded remote read 的不可变汇总。 */
public record RemoteSnapshotBatch(
        List<RemoteOrderSnapshot> orders,
        boolean readOnlyPermissionConfirmed,
        boolean complete,
        int pageCount,
        int recordCount,
        Instant observedAt
) {
    public RemoteSnapshotBatch {
        orders = List.copyOf(orders == null ? List.of() : orders);
        if (pageCount < 0 || recordCount < 0) throw new IllegalArgumentException("counts must not be negative");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
