package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;

import java.util.List;

public interface EquityCurveSnapshotRepository {

    void insert(EquityCurveSnapshot snapshot);

    List<EquityCurveSnapshot> listByRunId(String paperRunId);
}
