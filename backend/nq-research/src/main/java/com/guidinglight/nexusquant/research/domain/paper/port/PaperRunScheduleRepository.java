package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunSchedule;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperRunScheduleRepository {

    void insert(PaperRunSchedule schedule);

    Optional<PaperRunSchedule> findById(String scheduleId);

    List<PaperRunSchedule> list(String paperRunId, String status);

    boolean updateStatus(String scheduleId, PaperRunScheduleStatus status, Instant updatedAt);

    boolean updateLastFireTime(String scheduleId, Instant lastFireTime, Instant updatedAt);
}
