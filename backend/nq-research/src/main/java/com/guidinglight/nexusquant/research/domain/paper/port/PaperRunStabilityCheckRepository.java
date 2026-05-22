package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheck;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperRunStabilityCheckRepository {

    void upsert(PaperRunStabilityCheck check);

    Optional<PaperRunStabilityCheck> findById(String stabilityCheckId);

    Optional<PaperRunStabilityCheck> findByRunIdAndWindow(String paperRunId, Instant windowStart, Instant windowEnd);

    List<PaperRunStabilityCheck> listByRunId(String paperRunId, String status);
}
