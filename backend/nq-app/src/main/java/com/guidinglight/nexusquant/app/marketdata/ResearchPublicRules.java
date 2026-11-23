package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxVenueRuleFactsReader;
import com.guidinglight.nexusquant.strategy.domain.PublicReplayRuleIdentity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

/** 研究使用当前公开规则；完整原响应和标的身份随运行冻结，不声称是历史时点规则。 */
public final class ResearchPublicRules {
    private ResearchPublicRules() {}

    public static ObjectNode snapshot(String symbol, String raw, Instant observedAt, ObjectMapper mapper) {
        if (!Set.of("BTC-USDT", "ETH-USDT").contains(symbol) || raw == null || raw.length() > 256_000)
            throw new IllegalArgumentException("RESEARCH_RULE_SCOPE_INVALID");
        try {
            var facts = OkxVenueRuleFactsReader.parseSnapshot(mapper.readTree(raw), Set.of(symbol), observedAt);
            var fact = facts.facts().getFirst();
            if (!symbol.equals(fact.instId()) || !"SPOT".equals(fact.instType())
                    || !"LIVE".equals(fact.state()))
                throw new IllegalArgumentException("RESEARCH_RULE_NOT_TRADABLE");
            ObjectNode rule = mapper.createObjectNode();
            rule.put("policy", "CURRENTLY_OBSERVED_PUBLIC_RULES");
            rule.put("instrument", symbol);
            rule.put("state", fact.state());
            rule.put("tickSize", decimal(fact.tickSize()));
            rule.put("lotSize", decimal(fact.lotSize()));
            rule.put("minimumSize", decimal(fact.minimumSize()));
            rule.put("observedAt", observedAt.toString());
            ObjectNode result = mapper.createObjectNode();
            result.put("requestPath", path(symbol));
            result.put("rawResponse", raw);
            result.put("rawSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8))));
            result.put("ruleSha256", PublicReplayRuleIdentity.sha256(rule));
            result.set("rule", rule);
            return result;
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalArgumentException("RESEARCH_RULE_RESPONSE_INVALID", failure);
        }
    }

    public static String path(String symbol) {
        if (!Set.of("BTC-USDT", "ETH-USDT").contains(symbol))
            throw new IllegalArgumentException("RESEARCH_RULE_SCOPE_INVALID");
        return "/api/v5/public/instruments?instType=SPOT&instId=" + symbol;
    }

    public static void validate(JsonNode spec, JsonNode dataset, ObjectMapper mapper) {
        if (!dataset.has("frozenInput")) return;
        try {
            JsonNode stored = mapper.readTree(spec.path("researchRuleSnapshotJson").asText());
            if (stored == null || !stored.isObject()) throw new IllegalArgumentException("missing rule");
            JsonNode rule = stored.path("rule");
            ObjectNode derived = snapshot(dataset.path("symbol").asText(),
                    stored.path("rawResponse").asText(), Instant.parse(rule.path("observedAt").asText()), mapper);
            if (!derived.equals(stored) || !"CURRENTLY_OBSERVED_PUBLIC_RULES".equals(spec.path("rulePolicy").asText())
                    || !"OKX".equals(dataset.path("exchangeCode").asText())
                    || !"SPOT".equals(dataset.path("marketType").asText()))
                throw new IllegalArgumentException("rule identity");
            check(spec, "priceTick", rule, "tickSize");
            check(spec, "quantityStep", rule, "lotSize");
            check(spec, "minimumQuantity", rule, "minimumSize");
        } catch (java.io.IOException | RuntimeException failure) {
            throw new IllegalArgumentException("RESEARCH_RULE_SNAPSHOT_INVALID", failure);
        }
    }

    private static void check(JsonNode spec, String field, JsonNode rule, String ruleField) {
        if (new BigDecimal(spec.path(field).asText()).compareTo(new BigDecimal(rule.path(ruleField).asText())) != 0)
            throw new IllegalArgumentException("rule parameter mismatch");
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
