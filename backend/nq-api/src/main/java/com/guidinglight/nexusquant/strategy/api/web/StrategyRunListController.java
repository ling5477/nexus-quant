package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.api.dto.StrategyRunPageResponse;
import com.guidinglight.nexusquant.strategy.application.StrategyRunListService;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunListQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 新增分页读取，保留旧数组及精确详情合同；不开放无范围的全局运行目录。 */
@RestController
public class StrategyRunListController {
    private final StrategyRunListService service;

    public StrategyRunListController(StrategyRunListService service) {
        this.service = service;
    }

    @GetMapping("/api/strategy-runs/page")
    @Operation(summary = "分页查询策略运行历史", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyRunPageResponse list(
            @RequestParam(required = false) String strategyId,
            @RequestParam(required = false) String scheduleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return StrategyRunPageResponse.from(
                service.list(
                        new StrategyRunListQuery(
                                strategyId, scheduleId, status, triggerType, limit, offset)));
    }
}
