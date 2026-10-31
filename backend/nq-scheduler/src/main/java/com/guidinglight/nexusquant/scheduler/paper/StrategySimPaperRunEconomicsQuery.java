package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunCanonicalEconomicsQuery;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 复用 /facts 的同一只读估值，不引入第二套 Paper 盈亏算法。 */
@Component
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class StrategySimPaperRunEconomicsQuery implements PaperRunCanonicalEconomicsQuery {
    private final StrategySimRunService facts;

    public StrategySimPaperRunEconomicsQuery(StrategySimRunService facts) {
        this.facts = facts;
    }

    @Override
    public BigDecimal pnl(String paperRunId) {
        return facts.facts(paperRunId).pnl();
    }
}
