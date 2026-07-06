package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * GateR-4 Shadow Run 策略决策轨迹模型。
 *
 * <p>职责：把调用方已经计算完成的本地策略决策整理为可复盘的只读 snapshot payload。
 * Why：runner skeleton 不执行真实策略、不读取行情 provider，也不推导交易授权；该模型只保存
 * strategy decision 的诊断轨迹，供后续 replay / consistency report 使用。
 *
 * @param strategyVersionId 策略版本；必须来自调用方本地事实，不触发策略执行
 * @param datasetId         数据集 id；用于把决策轨迹锚定到输入数据
 * @param decisionType      决策类型，例如 OBSERVE / CANDIDATE / NO_SIGNAL；不表示交易许可
 * @param signalSide        信号方向，例如 OBSERVE / LONG_BIAS / SHORT_BIAS；不得映射成真实 BUY / SELL
 * @param confidence        置信度标签；本地诊断字段，不是风控放行
 * @param reasonCodes       决策原因码；用于审计和回放
 * @param features          脱敏特征摘要；必须是 JSON object，不允许 credential/private/order 字段
 * @param inputRefs         输入引用摘要；必须是 JSON object，不允许 private endpoint 或 credential 字段
 * @param traceId           全链路 trace id
 */
public record StrategyDecisionTrace(
        String strategyVersionId,
        UUID datasetId,
        String decisionType,
        String signalSide,
        String confidence,
        List<String> reasonCodes,
        JsonNode features,
        JsonNode inputRefs,
        String traceId
) {

    public StrategyDecisionTrace {
        strategyVersionId = requireText(strategyVersionId, "strategyVersionId");
        Objects.requireNonNull(datasetId, "datasetId must not be null");
        decisionType = requireText(decisionType, "decisionType");
        signalSide = requireText(signalSide, "signalSide");
        confidence = requireText(confidence, "confidence");
        reasonCodes = copyTextList(reasonCodes, "reasonCodes");
        validateObject("features", features);
        validateObject("inputRefs", inputRefs);
        traceId = requireText(traceId, "traceId");
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    static List<String> copyTextList(List<String> values, String fieldName) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> requireText(value, fieldName + " item"))
                .toList();
    }

    static void validateObject(String fieldName, JsonNode value) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        ShadowRunSensitiveDataGuard.validateJson(fieldName, value);
        if (!value.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object");
        }
    }
}
