package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.api.dto.StrategyDefinitionPageResponse;
import com.guidinglight.nexusquant.strategy.application.StrategyDefinitionListService;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionListQuery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 沿用正式认证与定义读取范围；过滤条件不授予策略创建、运行或交易权限。 */
@Validated
@RestController
@ConditionalOnBean(StrategyDefinitionListService.class)
@RequestMapping("/api/strategies/page")
public class StrategyDefinitionListController {
    private final StrategyDefinitionListService service;

    public StrategyDefinitionListController(StrategyDefinitionListService service) {
        this.service = service;
    }

    @GetMapping
    public StrategyDefinitionPageResponse list(
            @RequestParam(required = false) @Size(max = 128) String strategyId,
            @RequestParam(required = false) @Size(max = 128) String strategyCode,
            @RequestParam(required = false) @Size(max = 255) String strategyName,
            @RequestParam(required = false) @Size(max = 64) String strategyType,
            @RequestParam(required = false) @Size(max = 64) String exchangeCode,
            @RequestParam(required = false) @Size(max = 64) String tradeEnv,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return StrategyDefinitionPageResponse.from(
                service.list(
                        new StrategyDefinitionListQuery(
                                strategyId,
                                strategyCode,
                                strategyName,
                                strategyType,
                                exchangeCode,
                                tradeEnv,
                                enabled,
                                limit,
                                offset)));
    }
}
