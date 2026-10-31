package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import java.util.List;

/** 以 Paper run 的不可变 canonical account 关联读取策略 SIM 经济事实。 */
public interface PaperRunCanonicalFactsRepository {
    boolean isStrategySim(String paperRunId);
    List<PaperTradingOrder> orders(String paperRunId);
    List<PaperTradingTrade> trades(String paperRunId);
    List<PaperTradingPosition> positions(String paperRunId);
}
