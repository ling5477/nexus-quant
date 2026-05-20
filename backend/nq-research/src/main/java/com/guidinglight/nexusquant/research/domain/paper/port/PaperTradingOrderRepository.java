package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;

import java.util.List;

public interface PaperTradingOrderRepository {

    void insert(PaperTradingOrder order);

    List<PaperTradingOrder> listByRunId(String paperRunId);
}
