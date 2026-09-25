package com.guidinglight.nexusquant.strategy.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** 已选择的公开规则字段采用固定顺序、UTC 与规范化 decimal 生成身份。 */
public final class PublicReplayRuleIdentity {
    private PublicReplayRuleIdentity() { }

    public static String sha256(JsonNode rule) {
        if (rule == null || !rule.isObject() || rule.size() != 7) {
            throw new IllegalArgumentException("PUBLIC_RULE_IDENTITY_INVALID");
        }
        String canonical = "PUBLIC_RULE_V1\n"
                + required(rule, "policy") + '\n'
                + required(rule, "instrument") + '\n'
                + required(rule, "state") + '\n'
                + decimal(rule, "tickSize") + '\n'
                + decimal(rule, "lotSize") + '\n'
                + decimal(rule, "minimumSize") + '\n'
                + Instant.parse(required(rule, "observedAt")) + '\n';
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String required(JsonNode rule, String field) {
        JsonNode value = rule.get(field);
        if (value == null || value.isNull() || !value.isValueNode() || value.asText().isBlank()) {
            throw new IllegalArgumentException("PUBLIC_RULE_FIELD_MISSING_" + field);
        }
        return value.asText();
    }

    private static String decimal(JsonNode rule, String field) {
        BigDecimal value = new BigDecimal(required(rule, field));
        if (value.signum() <= 0) throw new IllegalArgumentException("PUBLIC_RULE_VALUE_INVALID_" + field);
        return value.stripTrailingZeros().toPlainString();
    }
}
