package com.guidinglight.nexusquant.strategy.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import com.guidinglight.nexusquant.strategy.application.StrategyRunRelationService;
import com.guidinglight.nexusquant.strategy.application.exception.StrategyRunReadNotFoundException;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunBusinessEventItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunLedgerEntryItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRiskEventItem;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

class StrategyRunRelationControllerTest {
    private static StrategyRunRelationController controller(StrategyRunRelationService service) {
        var auth = mock(GatewayAuthFacade.class);
        var profiles = mock(CurrentUserProfileService.class);
        var claims = mock(TokenClaims.class);
        when(claims.username()).thenReturn("reader");
        when(auth.currentUser()).thenReturn(Optional.of(claims));
        when(profiles.findByUsername("reader"))
                .thenReturn(
                        Optional.of(
                                new AuthUserProfile(
                                        1L, "reader", "synthetic", List.of("VIEWER"), true)));
        return new StrategyRunRelationController(service, auth, profiles);
    }

    @Test
    void ledgerAmountsAndLargeAccountIdentityRemainExactText() throws Exception {
        var service = mock(StrategyRunRelationService.class);
        var time = Instant.parse("2026-10-01T00:00:00Z");
        when(service.ledgerEntries(anyLong(), any()))
                .thenReturn(
                        new StrategyRunRelatedPage<>(
                                "run",
                                List.of(
                                        new StrategyRunLedgerEntryItem(
                                                "entry",
                                                "trade",
                                                "order",
                                                9007199254740993L,
                                                "BNB",
                                                new BigDecimal("-9007199254740993.12345678"),
                                                null,
                                                "DEBIT",
                                                "TRADE",
                                                "trade:LEDGER:FEE_1",
                                                "trace",
                                                time)),
                                1,
                                20,
                                0));
        var mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();
        mvc.perform(get("/api/strategy-runs/run/ledger-entries/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyRunId").value("run"))
                .andExpect(jsonPath("$.items[0].delta").isString())
                .andExpect(jsonPath("$.items[0].delta").value("-9007199254740993.12345678"))
                .andExpect(jsonPath("$.items[0].accountId").isString())
                .andExpect(jsonPath("$.items[0].accountId").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].balanceAfter").isEmpty())
                .andExpect(jsonPath("$.items[0].refType").value("TRADE"));
    }

    @Test
    void nullableRiskFieldsAndRecordedDecisionRemainUnchanged() throws Exception {
        var service = mock(StrategyRunRelationService.class);
        when(service.riskEvents(anyLong(), any()))
                .thenReturn(
                        new StrategyRunRelatedPage<>(
                                "run",
                                List.of(
                                        new StrategyRunRiskEventItem(
                                                "risk",
                                                "order",
                                                null,
                                                "REJECT",
                                                null,
                                                null,
                                                "trace",
                                                Instant.parse("2026-10-01T00:00:00Z"))),
                                1,
                                20,
                                0));
        var mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();
        mvc.perform(get("/api/strategy-runs/run/risk-events/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].decision").value("REJECT"))
                .andExpect(jsonPath("$.items[0].orderId").value("order"))
                .andExpect(jsonPath("$.items[0].ruleId").isEmpty())
                .andExpect(jsonPath("$.items[0].reason").isEmpty());
    }

    /** 白名单投影不包含原始载荷，账户大整数及三种时间语义各自保留。 */
    @Test
    void eventProjectionKeepsIdentityAndDoesNotExposeEnvelopePayload() throws Exception {
        var service = mock(StrategyRunRelationService.class);
        var time = Instant.parse("2026-10-01T00:00:00.123456789Z");
        when(service.businessEvents(anyLong(), any()))
                .thenReturn(
                        new StrategyRunRelatedPage<>(
                                "run",
                                List.of(
                                        new StrategyRunBusinessEventItem(
                                                "event",
                                                "order",
                                                "trade",
                                                9007199254740993L,
                                                "TradeExecuted",
                                                "trade.event.v1",
                                                "nq-scheduler.paper-matching",
                                                "TRADE_ID",
                                                null,
                                                null,
                                                null,
                                                null,
                                                time,
                                                null,
                                                time,
                                                "trace")),
                                1,
                                20,
                                0));
        var mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();
        mvc.perform(get("/api/strategy-runs/run/business-events/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].accountId").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].association").value("TRADE_ID"))
                .andExpect(jsonPath("$.items[0].publishedAt").isEmpty())
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.items[0].payloadJson").doesNotExist());
    }

    @Test
    void invalidWindowsFailBeforeReadingAndMissingRunReturns404() throws Exception {
        var service = mock(StrategyRunRelationService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(controller(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        for (String kind : List.of("risk-events", "ledger-entries", "business-events")) {
            for (String[] field :
                    new String[][] {
                        {"limit", "0"},
                        {"limit", "101"},
                        {"offset", "-1"},
                        {"offset", "2147483648"},
                        {"limit", "1.5"}
                    })
                mvc.perform(
                                get("/api/strategy-runs/run/" + kind + "/page")
                                        .param(field[0], field[1]))
                        .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.riskEvents(anyLong(), any()))
                .thenThrow(new StrategyRunReadNotFoundException());
        when(service.businessEvents(anyLong(), any()))
                .thenThrow(new StrategyRunReadNotFoundException());
        when(service.ledgerEntries(anyLong(), any()))
                .thenThrow(new StrategyRunReadNotFoundException());
        for (String kind : List.of("risk-events", "ledger-entries", "business-events"))
            mvc.perform(get("/api/strategy-runs/missing/" + kind + "/page"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
