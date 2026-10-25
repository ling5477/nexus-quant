package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.api.dto.StrategySchedulePageResponse;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleListService;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleListQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 新增独立分页入口，旧数组和详情继续兼容，不改变计划扫描或交易写入权限。 */
@RestController
public class StrategyScheduleListController {
    private final StrategyScheduleListService service;

    public StrategyScheduleListController(StrategyScheduleListService service) {
        this.service = service;
    }

    @GetMapping("/api/strategy-schedules/page")
    @Operation(summary = "分页查询策略调度计划", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategySchedulePageResponse list(
            @RequestParam String strategyId,
            @RequestParam(required = false) String scheduleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scheduleType,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return StrategySchedulePageResponse.from(
                service.list(
                        new StrategyScheduleListQuery(
                                strategyId, scheduleId, status, scheduleType, limit, offset)));
    }
}
