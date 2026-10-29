package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.research.domain.BacktestWindow;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** 先固定选择口径再看训练结果；此接口不接受Test或Holdout得分。 */
public final class WalkForwardPolicy {
    public static final String VERSION="EXPANDING_WALK_FORWARD_V1";
    public static final String SELECTION="AFTER_COST_RETURN_DESC_DRAWDOWN_ASC_ORDINAL_ASC_V1";
    private WalkForwardPolicy() {}
    public record Window(int fold, String role, int startBar, int endBar) {}
    public record Score(int ordinal, String trialId, String versionId, String runId,
                        String evaluationId, BigDecimal totalReturn, BigDecimal maxDrawdown) {}

    public static Window window(int step) {
        if(step<0 || step>=16) throw new IllegalArgumentException("validation step out of bounds");
        int fold=step/4+1, end=240+120*(fold-1);
        return switch(step%4) {
            case 0 -> new Window(fold,"IN_SAMPLE",0,end);
            case 1 -> new Window(fold,fold==4?"FINAL_HOLDOUT":"OUT_OF_SAMPLE",end,end+120);
            case 2 -> new Window(fold,"FEE_STRESS",end,end+120);
            default -> new Window(fold,"SLIPPAGE_STRESS",end,end+120);
        };
    }

    public static List<Score> rank(List<Score> scores) {
        return scores.stream().filter(s->s.totalReturn()!=null && s.maxDrawdown()!=null && s.maxDrawdown().signum()>=0)
                .sorted(Comparator.comparing(Score::totalReturn).reversed()
                        .thenComparing(Score::maxDrawdown).thenComparingInt(Score::ordinal)).toList();
    }

    public static ObjectNode execution(JsonNode baseline, Window window, ObjectMapper mapper) {
        ObjectNode spec=baseline.deepCopy();
        ObjectNode policy=spec.putObject("windowExecution");
        policy.put("version",BacktestWindow.VERSION);
        policy.put("startBar",window.startBar()); policy.put("endBar",window.endBar());
        policy.put("initialPosition","FLAT");policy.put("warmupTrading","DISABLED");
        policy.put("warmupPolicy","HISTORY_ONLY_RESET_STATE_AT_START");
        if(window.role().endsWith("STRESS")) {
            if(baseline.has("feeSource") || baseline.has("feeSourceSnapshotJson") || !baseline.hasNonNull("feeRate")
                    || !baseline.hasNonNull("slippageBps")) return null;
            String field=window.role().equals("FEE_STRESS")?"feeRate":"slippageBps";
            BigDecimal value;
            try { value=new BigDecimal(baseline.path(field).asText()); }
            catch(NumberFormatException e) {return null;}
            if(value.signum()<0) return null;
            BigDecimal stressed=value.multiply(BigDecimal.valueOf(2)).max(value.add(new BigDecimal(field.equals("feeRate")?"0.001":"10")));
            if(stressed.compareTo(new BigDecimal(field.equals("feeRate")?"0.1":"1000"))>0) return null;
            spec.put(field,stressed.stripTrailingZeros().toPlainString());
            ObjectNode cost=spec.putObject("costStress");
            cost.put("source","RESEARCH_HYPOTHESIS_V1");cost.put("field",field);
            cost.put("unit",field.equals("feeRate")?"FRACTION":"BPS");
            cost.put("baseline",baseline.path(field).asText());cost.put("value",spec.path(field).asText());
        }
        return spec;
    }
}
