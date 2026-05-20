package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;

import java.util.List;

public interface PaperTradingTradeRepository {

    void insert(PaperTradingTrade trade);

    List<PaperTradingTrade> listByRunId(String paperRunId);
}
