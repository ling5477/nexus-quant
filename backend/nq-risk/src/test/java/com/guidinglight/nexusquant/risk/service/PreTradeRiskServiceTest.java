package com.guidinglight.nexusquant.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.risk.model.RiskContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * PreTradeRiskServiceTest 覆盖 GateD 第一批规则链的最小回归。
 */
class PreTradeRiskServiceTest {

    @Test
    void shouldRejectOrderWhenPrecisionIsInvalid() {
        PreTradeRiskService service = createService(defaultSettings());

        var result = service.evaluate(context("coid-precision", new BigDecimal("100.123456789"), new BigDecimal("0.01000000"), Instant.parse("2026-03-12T09:00:00Z")));

        assertEquals(RiskDecision.REJECT, result.decision());
        assertEquals("INVALID_PRECISION", result.ruleCode());
        assertEquals("OrderPrecisionRule", result.ruleName());
    }

    @Test
    void shouldRejectOrderWhenMinNotionalIsNotMet() {
        PreTradeRiskSettings settings = new PreTradeRiskSettings(
                true,
                Set.of(),
                Map.of("GLOBAL", Set.of("BTC-USDT")),
                8,
                8,
                new BigDecimal("10"),
                new BigDecimal("1000000"),
                Duration.ofMinutes(5),
                Duration.ofSeconds(1),
                5
        );
        PreTradeRiskService service = createService(settings);

        var result = service.evaluate(context("coid-min-notional", new BigDecimal("100.00000000"), new BigDecimal("0.05000000"), Instant.parse("2026-03-12T09:00:01Z")));

        assertEquals(RiskDecision.REJECT, result.decision());
        assertEquals("MIN_NOTIONAL_NOT_MET", result.ruleCode());
    }

    @Test
    void shouldRejectDuplicateRequestBeforeRateLimit() {
        PreTradeRiskService service = createService(defaultSettings());
        RiskContext first = context("coid-dup", new BigDecimal("100.00000000"), new BigDecimal("0.10000000"), Instant.parse("2026-03-12T09:00:02Z"));
        RiskContext second = context("coid-dup", new BigDecimal("100.00000000"), new BigDecimal("0.10000000"), Instant.parse("2026-03-12T09:00:03Z"));

        var firstResult = service.evaluate(first);
        var secondResult = service.evaluate(second);

        assertEquals(RiskDecision.ALLOW, firstResult.decision());
        assertEquals(RiskDecision.REJECT, secondResult.decision());
        assertEquals("DUPLICATE_REQUEST", secondResult.ruleCode());
    }

    @Test
    void shouldRejectRequestWhenRateLimitExceeded() {
        PreTradeRiskSettings settings = new PreTradeRiskSettings(
                true,
                Set.of(),
                Map.of(),
                8,
                8,
                BigDecimal.ZERO,
                new BigDecimal("1000000"),
                Duration.ZERO,
                Duration.ofSeconds(10),
                2
        );
        PreTradeRiskService service = createService(settings);

        assertEquals(RiskDecision.ALLOW, service.evaluate(context("coid-rate-1", new BigDecimal("100.00000000"), new BigDecimal("0.10000000"), Instant.parse("2026-03-12T09:00:10Z"))).decision());
        assertEquals(RiskDecision.ALLOW, service.evaluate(context("coid-rate-2", new BigDecimal("100.00000000"), new BigDecimal("0.10000000"), Instant.parse("2026-03-12T09:00:11Z"))).decision());
        var third = service.evaluate(context("coid-rate-3", new BigDecimal("100.00000000"), new BigDecimal("0.10000000"), Instant.parse("2026-03-12T09:00:12Z")));

        assertEquals(RiskDecision.REJECT, third.decision());
        assertEquals("RATE_LIMIT_EXCEEDED", third.ruleCode());
    }

    private PreTradeRiskService createService(PreTradeRiskSettings settings) {
        KillSwitchService killSwitchService = new KillSwitchService();
        return new PreTradeRiskService(new RiskRuleRegistry(List.of(
                new KillSwitchRiskRule(killSwitchService),
                new AccountTradingEnabledRule(settings),
                new SymbolEnabledRule(settings),
                new DuplicateRequestRule(settings),
                new RateLimitRule(settings),
                new OrderPrecisionRule(settings),
                new MinNotionalRule(settings),
                new MaxOrderAmountRule(settings)
        )));
    }

    private PreTradeRiskSettings defaultSettings() {
        return new PreTradeRiskSettings(
                true,
                Set.of(),
                Map.of("GLOBAL", Set.of("BTC-USDT")),
                8,
                8,
                BigDecimal.ZERO,
                new BigDecimal("1000000"),
                Duration.ofMinutes(5),
                Duration.ofSeconds(10),
                5
        );
    }

    private RiskContext context(String clientOrderId, BigDecimal price, BigDecimal quantity, Instant now) {
        return new RiskContext(
                new PlaceOrderCommand(
                        "ord-" + clientOrderId,
                        "req-" + clientOrderId,
                        1001L,
                        "PAPER",
                        "BTC-USDT",
                        clientOrderId,
                        "1001:" + clientOrderId,
                        "BUY",
                        "LIMIT",
                        price,
                        quantity,
                        "GTC",
                        "strategy",
                        "strategy-1",
                        "trc-" + clientOrderId
                ),
                now,
                "trc-" + clientOrderId
        );
    }
}
