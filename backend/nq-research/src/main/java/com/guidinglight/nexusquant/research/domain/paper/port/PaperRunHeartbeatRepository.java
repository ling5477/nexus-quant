package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperRunHeartbeatRepository {

    void insert(PaperRunHeartbeat heartbeat);

    List<PaperRunHeartbeat> listByRunId(String paperRunId);

    int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end);

    Optional<PaperRunHeartbeat> findLatestByRunId(String paperRunId);
}
