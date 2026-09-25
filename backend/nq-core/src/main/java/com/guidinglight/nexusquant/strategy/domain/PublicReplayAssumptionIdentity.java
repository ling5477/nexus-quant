package com.guidinglight.nexusquant.strategy.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/** 费用、滑点及规则假设的稳定身份；字段顺序和数字字符串格式不影响 digest。 */
public final class PublicReplayAssumptionIdentity {
    private PublicReplayAssumptionIdentity() { }

    public static String sha256(JsonNode specification) {
        if (specification == null || !specification.isObject() || specification.isEmpty()) {
            throw new IllegalArgumentException("PUBLIC_REPLAY_ASSUMPTIONS_INVALID");
        }
        TreeMap<String, String> values = new TreeMap<>();
        specification.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull() || !value.isValueNode()) {
                throw new IllegalArgumentException("PUBLIC_REPLAY_ASSUMPTION_VALUE_INVALID");
            }
            String canonical = switch (entry.getKey()) {
                case "quantityStep", "priceTick", "minimumQuantity", "minimumNotional",
                        "feeRate", "slippageBps" -> new BigDecimal(value.asText())
                                .stripTrailingZeros().toPlainString();
                default -> value.asText();
            };
            values.put(entry.getKey(), canonical);
        });
        StringBuilder canonical = new StringBuilder("PUBLIC_REPLAY_ASSUMPTIONS_V1\n");
        values.forEach((key, value) -> canonical.append(key.length()).append(':').append(key)
                .append(value.length()).append(':').append(value).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
