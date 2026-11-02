package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.research.api.dto.BacktestPublishResponse;
import com.guidinglight.nexusquant.research.api.dto.ResearchResultPageResponse;
import com.guidinglight.nexusquant.research.application.PublishListService;
import com.guidinglight.nexusquant.research.domain.port.PublishListQuery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 只读发布分页不触发发布重试，不使用客户端所有者或快照替代正式来源。 */
@Validated
@RestController
@ConditionalOnBean(PublishListService.class)
@RequestMapping("/api/publishes/page")
public class PublishListController {
    private final PublishListService service;

    public PublishListController(PublishListService service) {
        this.service = service;
    }

    @GetMapping
    public ResearchResultPageResponse<BacktestPublishResponse> list(
            @RequestParam(required = false) @Size(max = 128) String researchConfigId,
            @RequestParam(required = false) @Size(max = 128) String backtestConfigId,
            @RequestParam(required = false) @Size(max = 128) String sourceStrategyId,
            @RequestParam(required = false) @Size(max = 128) String strategyVersionId,
            @RequestParam(required = false) @Size(max = 32) String publishStatus,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ResearchResultPageResponse.from(
                service.list(
                        new PublishListQuery(
                                researchConfigId,
                                backtestConfigId,
                                sourceStrategyId,
                                strategyVersionId,
                                publishStatus,
                                limit,
                                offset)),
                BacktestPublishResponse::from);
    }
}
