package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DuplicateRequestRule 使用 `accountId + idempotencyKey` 做重复请求拦截。
 * <p>
 * Why:
 * GateD 第二批已经把 `requestId / idempotencyKey` 收口进 contracts/core。
 * 这里必须改用稳定幂等键，而不是继续把“本次请求 ID”和“幂等键”混成一个概念。
 */
public class DuplicateRequestRule implements RiskRule {

    private static final String RULE_CODE = "DUPLICATE_REQUEST";

    private final PreTradeRiskSettings settings;
    private final Map<String, Instant> recentRequests = new ConcurrentHashMap<>();

    public DuplicateRequestRule(PreTradeRiskSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String ruleCode() {
        return RULE_CODE;
    }

    @Override
    public String ruleName() {
        return "DuplicateRequestRule";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public Optional<RiskDecisionResult> evaluate(RiskContext context) {
        Instant now = context.now();
        String key = context.command().accountId() + ":" + context.command().idempotencyKey();
        Instant previous = recentRequests.get(key);
        if (previous != null && previous.plus(settings.duplicateWindow()).isAfter(now)) {
            return Optional.of(RiskDecisionResult.reject(
                    RULE_CODE,
                    ruleName(),
                    "duplicate idempotencyKey detected inside duplicate window",
                    true,
                    RiskSeverity.MEDIUM,
                    context.traceId()
            ));
        }
        recentRequests.put(key, now);
        return Optional.empty();
    }
}
