package com.guidinglight.nexusquant.strategy.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.strategy.application.StrategyRunDetailService;
import com.guidinglight.nexusquant.strategy.application.exception.StrategyRunReadNotFoundException;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeItem;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

class StrategyRunDetailControllerTest {
    @Test
    void transfersExactOrderDecimalsAndFormalFieldNamesAsStrings() throws Exception {
        var service = mock(StrategyRunDetailService.class);
        var time = Instant.parse("2026-10-01T00:00:00Z");
        when(service.orders(any()))
                .thenReturn(
                        new StrategyRunRelatedPage<>(
                                "run",
                                List.of(
                                        new StrategyRunOrderItem(
                                                "order",
                                                "client",
                                                "external",
                                                "ACCEPTED",
                                                "BTC-USDT",
                                                "BUY",
                                                "LIMIT",
                                                new BigDecimal("9007199254740993.12345678"),
                                                new BigDecimal("0.00000001"),
                                                time,
                                                time)),
                                1,
                                20,
                                0));
        var mvc = MockMvcBuilders.standaloneSetup(new StrategyRunDetailController(service)).build();
        mvc.perform(get("/api/strategy-runs/run/orders/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyRunId").value("run"))
                .andExpect(jsonPath("$.items[0].price").isString())
                .andExpect(jsonPath("$.items[0].price").value("9007199254740993.12345678"))
                .andExpect(jsonPath("$.items[0].quantity").value("0.00000001"))
                .andExpect(jsonPath("$.items[0].orderStatus").value("ACCEPTED"));
    }

    @Test
    void preservesPositiveZeroNegativeAndUnknownTradeFeesWithoutConvertingCurrency()
            throws Exception {
        var service = mock(StrategyRunDetailService.class);
        var time = Instant.parse("2026-10-01T00:00:00Z");
        var mvc = MockMvcBuilders.standaloneSetup(new StrategyRunDetailController(service)).build();
        for (String fee : new String[] {"0.00000001", "0.00000000", "-0.00000001", null}) {
            when(service.trades(any()))
                    .thenReturn(
                            new StrategyRunRelatedPage<>(
                                    "run",
                                    List.of(
                                            new StrategyRunTradeItem(
                                                    "trade",
                                                    "order",
                                                    "external-trade",
                                                    "external-order",
                                                    "BTC-USDT",
                                                    new BigDecimal("9007199254740993.12345678"),
                                                    new BigDecimal("0.00000001"),
                                                    fee == null ? null : new BigDecimal(fee),
                                                    fee == null ? null : "BNB",
                                                    time)),
                                    1,
                                    20,
                                    0));
            var result =
                    mvc.perform(get("/api/strategy-runs/run/trades/page"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.items[0].price").isString())
                            .andExpect(
                                    jsonPath("$.items[0].price")
                                            .value("9007199254740993.12345678"));
            if (fee == null)
                result.andExpect(jsonPath("$.items[0].fee").isEmpty())
                        .andExpect(jsonPath("$.items[0].feeCurrency").isEmpty());
            else
                result.andExpect(jsonPath("$.items[0].fee").isString())
                        .andExpect(jsonPath("$.items[0].fee").value(fee))
                        .andExpect(jsonPath("$.items[0].feeCurrency").value("BNB"));
        }
    }

    @Test
    void rejectsBadWindowsBeforeReadingAndReturns404ForMissingRuns() throws Exception {
        var service = mock(StrategyRunDetailService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new StrategyRunDetailController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        for (String kind : List.of("orders", "trades")) {
            for (String[] field :
                    new String[][] {
                        {"limit", "0"},
                        {"limit", "101"},
                        {"offset", "-1"},
                        {"offset", "2147483648"},
                        {"limit", "1.5"}
                    }) {
                mvc.perform(
                                get("/api/strategy-runs/run/" + kind + "/page")
                                        .param(field[0], field[1]))
                        .andExpect(status().isBadRequest());
            }
        }
        verifyNoInteractions(service);
        when(service.overview("missing")).thenThrow(new StrategyRunReadNotFoundException());
        mvc.perform(get("/api/strategy-runs/missing/overview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
