package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.research.api.dto.BacktestEvaluationResponse;
import com.guidinglight.nexusquant.research.api.dto.ResearchResultPageResponse;
import com.guidinglight.nexusquant.research.application.eval.EvaluationListService;
import com.guidinglight.nexusquant.research.domain.eval.port.EvaluationListQuery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 评估分页复用正式认证及运行读取范围，旧数组和重新评估入口保持原合同。 */
@Validated
@RestController
@ConditionalOnBean(EvaluationListService.class)
@RequestMapping("/api/evaluations/page")
public class EvaluationListController {
    private final EvaluationListService service;

    public EvaluationListController(EvaluationListService service) {
        this.service = service;
    }

    @GetMapping
    public ResearchResultPageResponse<BacktestEvaluationResponse> list(
            @RequestParam(required = false) @Size(max = 128) String researchConfigId,
            @RequestParam(required = false) @Size(max = 128) String backtestConfigId,
            @RequestParam(required = false) @Size(max = 128) String backtestRunId,
            @RequestParam(required = false) @Size(max = 32) String evaluationStatus,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ResearchResultPageResponse.from(
                service.list(
                        new EvaluationListQuery(
                                researchConfigId,
                                backtestConfigId,
                                backtestRunId,
                                evaluationStatus,
                                limit,
                                offset)),
                BacktestEvaluationResponse::from);
    }
}
