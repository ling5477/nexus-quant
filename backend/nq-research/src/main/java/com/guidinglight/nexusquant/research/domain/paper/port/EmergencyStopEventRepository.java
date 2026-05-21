package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopStatus;

import java.time.Instant;
import java.util.List;

public interface EmergencyStopEventRepository {

    void insert(EmergencyStopEvent event);

    List<EmergencyStopEvent> listByRunId(String paperRunId);

    boolean updateStatus(String emergencyStopId, EmergencyStopStatus status, Instant resolvedAt, String resultJson);
}
