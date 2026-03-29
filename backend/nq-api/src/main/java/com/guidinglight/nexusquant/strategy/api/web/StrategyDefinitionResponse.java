package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinitionStatus;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyDefinitionResponse 描述接口响应体。
 */
@Schema(name = "StrategyDefinitionResponse", description = "接口响应体")
public record StrategyDefinitionResponse(
        @Schema(description = "strategyId")
        String strategyId,
        @Schema(description = "strategyCode")
        String strategyCode,
        @Schema(description = "strategyName")
        String strategyName,
        @Schema(description = "strategyType")
        String strategyType,
        @Schema(description = "exchangeCode")
        String exchangeCode,
        @Schema(description = "accountId")
        Long accountId,
        @Schema(description = "tradeEnv")
        String tradeEnv,
        @Schema(description = "enabled")
        boolean enabled,
        @Schema(description = "status")
        StrategyDefinitionStatus status,
        @Schema(description = "configSnapshot")
        String configSnapshot,
        @Schema(description = "version")
        int version,
        @Schema(description = "createdAt")
        Instant createdAt,
        @Schema(description = "updatedAt")
        Instant updatedAt
) {
    public static StrategyDefinitionResponse from(StrategyDefinition definition) {
        return new StrategyDefinitionResponse(
                definition.strategyId(),
                definition.strategyCode(),
                definition.strategyName(),
                definition.strategyType(),
                definition.exchangeCode(),
                definition.accountId(),
                definition.tradeEnv(),
                definition.enabled(),
                definition.status(),
                definition.configSnapshot(),
                definition.version(),
                definition.createdAt(),
                definition.updatedAt()
        );
    }
}



