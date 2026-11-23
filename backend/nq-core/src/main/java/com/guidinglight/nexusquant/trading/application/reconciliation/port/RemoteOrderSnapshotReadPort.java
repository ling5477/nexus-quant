package com.guidinglight.nexusquant.trading.application.reconciliation.port;

import com.guidinglight.nexusquant.trading.application.reconciliation.command.ReconciliationRequest;
import com.guidinglight.nexusquant.trading.application.reconciliation.model.RemoteSnapshotBatch;

/** 只暴露 typed private-read snapshot；不接受 URL/path/body。 */
@FunctionalInterface
public interface RemoteOrderSnapshotReadPort {
    RemoteSnapshotBatch read(ReconciliationRequest request);
}
