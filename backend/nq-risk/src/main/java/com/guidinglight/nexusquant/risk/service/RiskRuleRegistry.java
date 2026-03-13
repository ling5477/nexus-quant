package com.guidinglight.nexusquant.risk.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * RiskRuleRegistry 负责冻结风控规则顺序。
 * <p>
 * Why:
 * GateD 明确要求规则顺序稳定，否则重复请求、限频、数值校验的拒绝结果会因为注册顺序漂移而不可复盘。
 */
public class RiskRuleRegistry {

    private final List<RiskRule> rules;

    public RiskRuleRegistry(List<RiskRule> rules) {
        Objects.requireNonNull(rules, "rules must not be null");
        this.rules = rules.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(RiskRule::order).thenComparing(RiskRule::ruleCode))
                .toList();
    }

    public List<RiskRule> rules() {
        return rules;
    }
}
