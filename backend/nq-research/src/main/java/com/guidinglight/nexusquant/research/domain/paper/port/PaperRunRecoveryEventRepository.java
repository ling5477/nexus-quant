package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperRunRecoveryEventRepository {

    void insert(PaperRunRecoveryEvent event);

    Optional<PaperRunRecoveryEvent> findById(String recoveryEventId);

    List<PaperRunRecoveryEvent> listByRunId(String paperRunId, String recoveryType, String status);

    int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end);
}
