package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.research.api.dto.ResearchConfigPageResponse;
import com.guidinglight.nexusquant.research.application.ResearchConfigListService;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigListQuery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 有界读取复用正式研究事实及认证边界；不增加归档可见性或写操作。 */
@Validated
@RestController
@ConditionalOnBean(ResearchConfigListService.class)
@RequestMapping("/api/research-configs/page")
public class ResearchConfigListController {
    private final ResearchConfigListService service;

    public ResearchConfigListController(ResearchConfigListService service) {
        this.service = service;
    }

    @GetMapping
    public ResearchConfigPageResponse list(
            @RequestParam(required = false) @Size(max = 128) String sourceStrategyId,
            @RequestParam(required = false) @Size(max = 128) String researchConfigId,
            @RequestParam(required = false) @Size(max = 255) String name,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ResearchConfigPageResponse.from(
                service.list(
                        new ResearchConfigListQuery(
                                sourceStrategyId, researchConfigId, name, limit, offset)));
    }
}
