package com.guidinglight.nexusquant.trading.application.reconciliation;

import java.util.List;

/** 只暴露 bounded SELECT 语义的本地快照窄端口。 */
@FunctionalInterface
public interface LocalOrderSnapshotReadPort {
    List<LocalOrderSnapshot> read(ReconciliationRequest request);
}
