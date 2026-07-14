package com.guidinglight.nexusquant.risk.service;

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

import org.junit.jupiter.api.Test;

class KillSwitchRiskRuleTest {

    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void engagedUnknownMissingAndRepositoryFailureReject() {
        assertRejected(rule(repository(state(KillSwitchStatus.ENGAGED))));
        assertRejected(rule(repository(null)));
        assertRejected(rule(new FailingRepository()));
    }

    @Test
    void disengagedOnlyPassesThisRuleWithoutAuthorizingTrading() {
        KillSwitchRiskRule rule = rule(repository(state(KillSwitchStatus.DISENGAGED)));

        assertTrue(rule.evaluate(context()).isEmpty());
    }

    private static void assertRejected(KillSwitchRiskRule rule) {
        var result = rule.evaluate(context()).orElseThrow();
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

    private static RiskContext context() {
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
                "trace-kill-switch"
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
