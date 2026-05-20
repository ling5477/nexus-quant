package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperTradingRunRepository {

    void insert(PaperTradingRun run);

    Optional<PaperTradingRun> findById(String paperRunId);

    List<PaperTradingRun> list(String publishId, String status);

    boolean updateStatus(String paperRunId, PaperTradingRunStatus status, Instant startedAt, Instant stoppedAt, Instant updatedAt);
}
