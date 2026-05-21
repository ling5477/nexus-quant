package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperRunAlertRepository {

    void insert(PaperRunAlert alert);

    Optional<PaperRunAlert> findById(String alertId);

    List<PaperRunAlert> listByRunId(String paperRunId, String status, String severity);

    boolean updateStatus(String alertId, PaperRunAlertStatus status, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, Instant updatedAt);

    int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end);
}
