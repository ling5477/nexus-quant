package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetStrategy;
import java.math.BigDecimal;
import java.util.*;

/** 参数展开先检查预算，再沿正式策略校验；规范化身份拒绝数值尾零造成的重复候选。 */
public final class ExperimentParameters {
    public static final int MAX_TRIALS = 16;
    private ExperimentParameters() {}

    public static List<JsonNode> expand(String type, JsonNode source, JsonNode candidates,
            JsonNode grid, ObjectMapper mapper) {
        List<String> fields = switch (type) {
            case SpotSmaTargetStrategy.EXECUTABLE -> List.of("window", "investedExposure");
            case SpotTargetStrategy.CHANNEL_BREAKOUT -> List.of("entryLookbackBars", "exitLookbackBars", "investedExposure");
            default -> throw new IllegalArgumentException("unsupported experiment strategy");
        };
        boolean explicit = candidates != null && !candidates.isNull();
        boolean product = grid != null && !grid.isNull();
        if (explicit == product) throw new IllegalArgumentException("provide candidates or grid");
        List<JsonNode> expanded = new ArrayList<>();
        if (explicit) {
            if (!candidates.isArray() || candidates.isEmpty() || candidates.size() > MAX_TRIALS)
                throw new IllegalArgumentException("candidate count must be 1..16");
            candidates.forEach(expanded::add);
        } else {
            exactFields(grid, fields);
            int count = 1;
            for (String field : fields) {
                JsonNode axis = grid.path(field);
                if (!axis.isArray() || axis.isEmpty() || axis.size() > MAX_TRIALS
                        || count > MAX_TRIALS / axis.size())
                    throw new IllegalArgumentException("parameter product exceeds 16 trials");
                count *= axis.size();
            }
            expanded.add(mapper.createObjectNode());
            for (String field : fields) {
                List<JsonNode> next = new ArrayList<>();
                for (JsonNode prefix : expanded) for (JsonNode value : grid.path(field)) {
                    ObjectNode item = prefix.deepCopy();
                    item.set(field, value);
                    next.add(item);
                }
                expanded = next;
            }
        }
        List<JsonNode> normalized = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (JsonNode item : expanded) {
            exactFields(item, fields);
            ObjectNode canonical = mapper.createObjectNode();
            for (String field : fields) {
                JsonNode value = item.path(field);
                if (field.equals("investedExposure")) {
                    if (!value.isNumber() && !value.isTextual()) throw new IllegalArgumentException("invalid exposure");
                    if(value.asText().length()>64) throw new IllegalArgumentException("exposure exceeds input budget");
                    BigDecimal exposure = new BigDecimal(value.asText()).stripTrailingZeros();
                    if (exposure.precision() > 38 || exposure.scale() < -18 || exposure.scale() > 18)
                        throw new IllegalArgumentException("exposure precision exceeds budget");
                    canonical.put(field, exposure.toPlainString());
                } else {
                    if (!value.isIntegralNumber() || !value.canConvertToInt())
                        throw new IllegalArgumentException(field + " must be an integer");
                    canonical.put(field, value.intValue());
                }
            }
            ObjectNode snapshot = mapper.createObjectNode();
            snapshot.put("strategyVersionId", "parameter-validation");
            snapshot.put("checksum", "parameter-validation");
            snapshot.set("sourceSnapshotJson", source);
            snapshot.set("paramSnapshotJson", canonical);
            SpotTargetStrategy.fromSnapshot(snapshot.toString(), mapper);
            if (!identities.add(canonical.toString())) throw new IllegalArgumentException("duplicate parameter candidate");
            normalized.add(canonical);
        }
        return List.copyOf(normalized);
    }

    private static void exactFields(JsonNode node, List<String> fields) {
        if (node == null || !node.isObject() || node.size() != fields.size())
            throw new IllegalArgumentException("invalid parameter fields");
        node.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw new IllegalArgumentException("unsupported parameter: " + field);
        });
    }
}
