package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyStopEventRepository {

    void insert(EmergencyStopEvent event);

    /** 原请求锁与停机事件共用事务；未取得锁时不得另换编号执行。 */
    void lockOperation(UUID operationId);

    Optional<EmergencyStopEvent> findById(String emergencyStopId);

    List<EmergencyStopEvent> listByRunId(String paperRunId);

    boolean updateStatus(String emergencyStopId, EmergencyStopStatus status, Instant resolvedAt, String resultJson);
}
