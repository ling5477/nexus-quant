package com.guidinglight.nexusquant.trading.application.reconciliation.port;

import com.guidinglight.nexusquant.trading.application.reconciliation.command.ReconciliationRequest;
import com.guidinglight.nexusquant.trading.application.reconciliation.model.LocalOrderSnapshot;

import java.util.List;

/** 只暴露 bounded SELECT 语义的本地快照窄端口。 */
@FunctionalInterface
public interface LocalOrderSnapshotReadPort {
    List<LocalOrderSnapshot> read(ReconciliationRequest request);
}
