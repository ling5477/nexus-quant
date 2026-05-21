package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;

import java.util.List;

public interface PaperRunHeartbeatRepository {

    void insert(PaperRunHeartbeat heartbeat);

    List<PaperRunHeartbeat> listByRunId(String paperRunId);
}
