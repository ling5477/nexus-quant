package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.api.dto.StrategyVersionPageResponse;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionListService;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionListQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 沿用按策略编码限定的版本读取范围；不改变旧列表、精确详情或版本创建合同。 */
@RestController
public class StrategyVersionQueryController {
    private final StrategyVersionListService service;

    public StrategyVersionQueryController(StrategyVersionListService service) {
        this.service = service;
    }

    @GetMapping("/api/strategies/{strategyCode}/versions/page")
    @Operation(summary = "分页查询策略版本元信息", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyVersionPageResponse list(
            @PathVariable String strategyCode,
            @RequestParam(required = false) String strategyVersionId,
            @RequestParam(required = false) String versionName,
            @RequestParam(required = false) Integer version,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return StrategyVersionPageResponse.from(
                service.list(
                        new StrategyVersionListQuery(
                                strategyCode,
                                strategyVersionId,
                                versionName,
                                version,
                                status,
                                limit,
                                offset)));
    }
}
