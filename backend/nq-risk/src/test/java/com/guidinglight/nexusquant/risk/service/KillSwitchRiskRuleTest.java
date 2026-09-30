package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.risk.application.command.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.application.port.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.application.rule.KillSwitchRiskRule;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchScope;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchState;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.application.config.PreTradeRiskSettings;
import com.guidinglight.nexusquant.risk.application.rule.MinNotionalRule;
import com.guidinglight.nexusquant.risk.application.rule.RiskRuleRegistry;
import com.guidinglight.nexusquant.risk.model.TradeEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.risk.model.RiskContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class KillSwitchRiskRuleTest {

    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void explicitSimContinuesOnlyWhenKillIsEngagedOrDisengaged() {
        assertTrue(rule(repository(state(KillSwitchStatus.ENGAGED)))
                .evaluate(context(TradeEnvironment.SIM)).isEmpty());
        assertTrue(rule(repository(state(KillSwitchStatus.DISENGAGED)))
                .evaluate(context(TradeEnvironment.SIM)).isEmpty());
    }

    @Test
    void simStillFailsAnotherCanonicalRiskRule() {
        var settings = new PreTradeRiskSettings(true, Set.of(), Map.of(), 8, 8,
                new BigDecimal("20"), new BigDecimal("1000000"), Duration.ofMinutes(5),
                Duration.ofSeconds(1), 5);
        var gate = new PreTradeRiskService(new RiskRuleRegistry(List.of(
                rule(repository(state(KillSwitchStatus.ENGAGED))), new MinNotionalRule(settings))));
        var result = gate.evaluate(context(TradeEnvironment.SIM));
        assertEquals(RiskDecision.REJECT, result.decision());
        assertEquals("MIN_NOTIONAL_NOT_MET", result.ruleCode());
    }

    @Test
    void liveEngagedAndUnknownEnvironmentRejectWhileLiveDisengagedContinues() {
        var engaged = rule(repository(state(KillSwitchStatus.ENGAGED)));
        assertRejected(engaged, TradeEnvironment.LIVE);
        assertRejected(engaged, TradeEnvironment.UNKNOWN);
        assertRejected(engaged, null);
        assertTrue(rule(repository(state(KillSwitchStatus.DISENGAGED)))
                .evaluate(context(TradeEnvironment.LIVE)).isEmpty());
        assertRejected(rule(repository(state(KillSwitchStatus.DISENGAGED))), TradeEnvironment.UNKNOWN);
    }

    @Test
    void missingAndRepositoryFailureRejectEvenForSim() {
        assertRejected(rule(repository(null)), TradeEnvironment.SIM);
        assertRejected(rule(new FailingRepository()), TradeEnvironment.SIM);
    }

    private static void assertRejected(KillSwitchRiskRule rule, TradeEnvironment environment) {
        var result = rule.evaluate(context(environment)).orElseThrow();
        assertEquals(RiskDecision.REJECT, result.decision());
        assertEquals("KILL_SWITCH_TRIGGERED", result.ruleCode());
        assertTrue(result.hardReject());
        assertEquals(false, result.authorizesLiveTrading());
    }

    private static KillSwitchRiskRule rule(KillSwitchStateRepository repository) {
        return new KillSwitchRiskRule(new KillSwitchService(repository, CLOCK));
    }

    private static KillSwitchStateRepository repository(KillSwitchState state) {
        return new KillSwitchStateRepository() {
            @Override
            public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
                return Optional.ofNullable(state);
            }

            @Override
            public KillSwitchState engage(KillSwitchEngageCommand command) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static KillSwitchState state(KillSwitchStatus status) {
        return new KillSwitchState(
                KillSwitchScope.GLOBAL_TRADING,
                status,
                1,
                "TEST_STATE",
                "TEST_FIXTURE",
                NOW.minusSeconds(1),
                "tester",
                "trace-state"
        );
    }

    private static RiskContext context(TradeEnvironment environment) {
        return new RiskContext(
                new PlaceOrderCommand(
                        "order-kill-switch",
                        "request-kill-switch",
                        1001L,
                        "PAPER",
                        "BTC-USDT",
                        "client-kill-switch",
                        "1001:client-kill-switch",
                        "BUY",
                        "LIMIT",
                        new BigDecimal("100"),
                        new BigDecimal("0.1"),
                        "GTC",
                        "strategy",
                        "strategy-1",
                        "trace-kill-switch"
                ),
                NOW,
                "trace-kill-switch",
                environment
        );
    }

    private static final class FailingRepository implements KillSwitchStateRepository {
        @Override
        public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            throw new IllegalStateException("simulated repository failure");
        }

        @Override
        public KillSwitchState engage(KillSwitchEngageCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
