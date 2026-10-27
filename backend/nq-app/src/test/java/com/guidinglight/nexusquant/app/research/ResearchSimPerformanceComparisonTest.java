package com.guidinglight.nexusquant.app.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimPerformanceQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 正式绩效的估值覆盖与账户全运行统计不能拼成同一时间范围。 */
class ResearchSimPerformanceComparisonTest {
    private static final Instant START=Instant.parse("2026-10-01T00:00:00Z");
    private static final StrategySimAnalysisQuery.Metrics TOTALS=new StrategySimAnalysisQuery.Metrics(
            List.of(),2,List.of(new StrategySimAnalysisQuery.FeeTotal("USDT","2.50",2,2)),0,3,false);

    @Test void executionsOutsideValuationCoverageCannotSupplyPeriodFeesOrTradeCount() {
        var period=ResearchSimComparisonService.forwardPeriod("version",performance("AVAILABLE",1),TOTALS);
        assertEquals("NOT_COMPARABLE",period.status());
        assertEquals("EXECUTIONS_OUTSIDE_VALUATION_COVERAGE",period.reason());
        assertEquals("10",period.netPnl());
        assertNull(period.tradeCount());
        assertNull(period.fee());
        assertEquals("UNAVAILABLE",period.feeStatus());
    }

    @Test void missingMarksDoNotTurnKnownAccountTotalsIntoPeriodStatistics() {
        var period=ResearchSimComparisonService.forwardPeriod("version",performance("MARK_SOURCE_UNAVAILABLE",0),TOTALS);
        assertEquals("UNAVAILABLE",period.status());
        assertNull(period.netPnl());
        assertNull(period.tradeCount());
        assertNull(period.fee());
    }

    @Test void completeCoverageRetainsFormalValuesAndUnknownCurrencyCoverage() {
        var period=ResearchSimComparisonService.forwardPeriod("version",performance("AVAILABLE",0),TOTALS);
        assertEquals("AVAILABLE",period.status());
        assertEquals(2L,period.tradeCount());
        assertEquals("2.50",period.fee());
        var incomplete=new StrategySimAnalysisQuery.Metrics(List.of(),2,
                List.of(new StrategySimAnalysisQuery.FeeTotal(null,"2.50",2,1)),0,3,false);
        var missingFee=ResearchSimComparisonService.forwardPeriod("version",performance("AVAILABLE",0),incomplete);
        assertNull(missingFee.fee());
        assertEquals("UNAVAILABLE",missingFee.feeStatus());
    }

    private static StrategySimPerformanceQuery.View performance(String status,long after) {
        return new StrategySimPerformanceQuery.View("sim",status,"ACTUAL_EVENT_TIME","USDT",START,START.plusSeconds(3600),
                START.plusSeconds(3600),new BigDecimal("100"),new BigDecimal("110"),new BigDecimal("10"),
                new BigDecimal("0.1"),BigDecimal.ZERO,BigDecimal.ZERO,null,null,after,List.of(),new BigDecimal("100"));
    }
}
