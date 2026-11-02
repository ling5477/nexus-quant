package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PositionCurveSnapshot;

import java.util.List;

public interface PositionCurveSnapshotRepository {

    void insert(PositionCurveSnapshot snapshot);

    List<PositionCurveSnapshot> listByRunId(String paperRunId);
}
