package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.research.api.dto.BacktestConfigPageResponse;
import com.guidinglight.nexusquant.research.application.BacktestConfigListService;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigListQuery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 只读分页沿用正式认证和费用来源权限；筛选参数不会成为可执行SQL或所有者身份。 */
@Validated
@RestController
@ConditionalOnBean(BacktestConfigListService.class)
@RequestMapping("/api/backtest-configs/page")
public class BacktestConfigListController {
    private final BacktestConfigListService service;

    public BacktestConfigListController(BacktestConfigListService service) {
        this.service = service;
    }

    @GetMapping
    public BacktestConfigPageResponse list(
            @RequestParam(required = false) @Size(max = 128) String researchConfigId,
            @RequestParam(required = false) @Size(max = 128) String backtestConfigId,
            @RequestParam(required = false) @Size(max = 255) String name,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return BacktestConfigPageResponse.from(
                service.list(
                        new BacktestConfigListQuery(
                                researchConfigId, backtestConfigId, name, limit, offset)));
    }
}
