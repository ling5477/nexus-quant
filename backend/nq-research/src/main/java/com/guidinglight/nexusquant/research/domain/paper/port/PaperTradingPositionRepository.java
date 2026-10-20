package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;

import java.util.List;

public interface PaperTradingPositionRepository {

    void insert(PaperTradingPosition position);

    void upsert(PaperTradingPosition position);

    List<PaperTradingPosition> listByRunId(String paperRunId);
}
