package com.guidinglight.nexusquant.research.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;
import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.ExecutionStrategyDefinitionDraft;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * ResearchToExecutionMapper 负责把研究产物整理为执行域 strategy_definition 草稿。
 */
@Component
public class ResearchToExecutionMapper {

    private final ObjectMapper objectMapper;

    public ResearchToExecutionMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public ExecutionStrategyDefinitionDraft map(
            String publishName,
            String backtestRunId,
            ResearchConfig researchConfig,
            BacktestConfig backtestConfig,
            BacktestEvaluationView evaluationView
    ) {
        JsonNode strategySnapshot = readJson(researchConfig.strategySnapshot());
        String sourceStrategyId = text(strategySnapshot, "strategyId");
        String strategyType = text(strategySnapshot, "strategyType");
        String exchangeCode = text(strategySnapshot, "exchangeCode");
        Long accountId = strategySnapshot.get("accountId").asLong();
        String tradeEnv = text(strategySnapshot, "tradeEnv");

        ObjectNode configSnapshot = objectMapper.createObjectNode();
        configSnapshot.put("sourceStrategyId", sourceStrategyId);
        configSnapshot.put("sourceBacktestRunId", backtestRunId);
        configSnapshot.put("sourceResearchConfigId", researchConfig.researchConfigId());
        configSnapshot.put("sourceBacktestConfigId", backtestConfig.backtestConfigId());
        configSnapshot.put("sourceEvalReportId", evaluationView.evalReportId());
        configSnapshot.put("publishedAt", evaluationView.evaluatedAt().toString());
        configSnapshot.set("strategySnapshot", strategySnapshot);
        configSnapshot.set("datasetSpec", readJson(researchConfig.datasetSpec()));
        configSnapshot.set("backtestConfigSnapshot", readJson(backtestConfig.configSnapshot()));
        configSnapshot.set("evaluationSummary", readJson(evaluationView.reportJson()));

        return new ExecutionStrategyDefinitionDraft(
                "str-pub-" + UUID.randomUUID(),
                "pub-" + sourceStrategyId + "-" + shortRunId(backtestRunId),
                publishName,
                strategyType,
                exchangeCode,
                accountId,
                tradeEnv,
                configSnapshot.toString()
        );
    }

    private JsonNode readJson(String rawJson) {
        try {
            return objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid publish snapshot json", ex);
        }
    }

    private String text(JsonNode jsonNode, String fieldName) {
        JsonNode field = jsonNode.get(fieldName);
        if (field == null || field.asText().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be present in strategySnapshot");
        }
        return field.asText().trim();
    }

    private String shortRunId(String backtestRunId) {
        return backtestRunId.length() <= 8 ? backtestRunId : backtestRunId.substring(backtestRunId.length() - 8);
    }
}


