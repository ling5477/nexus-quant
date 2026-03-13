package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimitRule 对同账户、同 symbol、同方向请求做窗口限频。
 */
public class RateLimitRule implements RiskRule {

    private static final String RULE_CODE = "RATE_LIMIT_EXCEEDED";

    private final PreTradeRiskSettings settings;
    private final Map<String, Deque<Instant>> requestTimes = new ConcurrentHashMap<>();

    public RateLimitRule(PreTradeRiskSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String ruleCode() {
        return RULE_CODE;
    }

    @Override
    public String ruleName() {
        return "RateLimitRule";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Optional<RiskDecisionResult> evaluate(RiskContext context) {
        Instant now = context.now();
        String key = context.command().accountId() + ":" + context.command().symbol() + ":" + context.command().side();
        Deque<Instant> deque = requestTimes.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().plus(settings.rateLimitWindow()).isBefore(now)) {
                deque.removeFirst();
            }
            if (deque.size() >= settings.rateLimitMaxRequests()) {
                return Optional.of(RiskDecisionResult.reject(
                        RULE_CODE,
                        ruleName(),
                        "request rate exceeded configured window",
                        true,
                        RiskSeverity.MEDIUM,
                        context.traceId()
                ));
            }
            deque.addLast(now);
        }
        return Optional.empty();
    }
}
