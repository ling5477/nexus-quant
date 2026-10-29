package com.guidinglight.nexusquant.runtime.api;

import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionOperationsSnapshotQuery;
import com.guidinglight.nexusquant.runtime.api.dto.FakeDryRunOperationsResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** 读取脱敏 execution 运维投影；缺少 datasource/projection 时返回 fail-closed unavailable 快照。 */
@Service
public final class FakeDryRunOperationsService {
    private final Optional<ExecutionOperationsSnapshotQuery> query;
    private final Clock clock;

    public FakeDryRunOperationsService(Optional<ExecutionOperationsSnapshotQuery> query) {
        this.query = query;
        this.clock = Clock.systemUTC();
    }

    public FakeDryRunOperationsResponse currentSnapshot() {
        return query.map(ExecutionOperationsSnapshotQuery::currentSnapshot)
                .map(FakeDryRunOperationsResponse::from)
                .orElseGet(() -> FakeDryRunOperationsResponse.unavailable(Instant.now(clock)));
    }
}
