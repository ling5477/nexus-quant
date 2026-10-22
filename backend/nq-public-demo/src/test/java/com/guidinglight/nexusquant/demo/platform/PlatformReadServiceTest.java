package com.guidinglight.nexusquant.demo.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 校验呈现层不把注资、无市场估值或过期快照伪装成当前收益。 */
class PlatformReadServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final PlatformReadService service=new PlatformReadService(null,null,mapper);

    @Test void knownCashAndFundingDoNotManufactureEquityOrPnlWithoutValuation() throws Exception {
        var result=service.projectPerformance(UUID.randomUUID(),mapper.readTree("{\"simStatus\":\"NOT_BOUND\"}"),mapper.readTree("""
                {"initialCapital":"10000","currentCash":"10000","fundedAt":"2026-10-22T01:00:00Z",
                 "asOf":"2026-10-22T01:10:00Z","orderCount":0,"tradeCount":0,"snapshots":[]}
                """));
        assertEquals("UNAVAILABLE",result.path("status").asText());
        assertTrue(result.path("equity").isNull()); assertTrue(result.path("netPnl").isNull());
        assertTrue(result.path("returnRate").isNull()); assertEquals("10000",result.path("initialCapital").asText());
    }

    @Test void existingCanonicalCalculatorUsesFundingBaselineAndKeepsHistoricalAsOf() throws Exception {
        var result=service.projectPerformance(UUID.randomUUID(),mapper.readTree("{\"simStatus\":\"STOPPED\"}"),mapper.readTree("""
                {"exchangeAccountId":3,"initialCapital":"10000","currentCash":"10020",
                 "fundedAt":"2026-10-22T01:00:00Z","asOf":"2026-10-22T04:00:00Z",
                 "latestLedgerAt":"2026-10-22T01:01:00Z","orderCount":1,"tradeCount":1,
                 "snapshots":[{"id":"verified-1","at":"2026-10-22T01:10:00Z","equity":"10020",
                   "cash":"9500","realizedPnl":"0","unrealizedPnl":"20","drawdown":"0"}]}
                """));
        assertEquals("STALE",result.path("status").asText());
        assertEquals(0,new BigDecimal("20").compareTo(new BigDecimal(result.path("netPnl").asText())));
        assertEquals(0,new BigDecimal("0.002").compareTo(new BigDecimal(result.path("returnRate").asText())));
        assertEquals("2026-10-22T01:10:00Z",result.path("asOf").asText());
        assertEquals("9500",result.path("currentCash").asText());
        assertEquals(1,result.path("history").size());
    }
}
