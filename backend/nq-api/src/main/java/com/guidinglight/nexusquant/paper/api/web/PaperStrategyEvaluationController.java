package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.paper.api.dto.PaperStrategyEvaluationResponse;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PaperStrategyEvaluationController —— Paper 策略评估只读聚合入口（GateK Batch K3）。
 *
 * 从 strategyVersionId / publishId 维度评估 Paper 模拟表现、Paper vs Backtest 偏差、样本充足性与风险调整评分，
 * 供前端策略评估视图消费。只读：不触发任何状态机、调度、回测、发布或外部调用；只覆盖 SIM/Paper，LIVE 未开启，
 * 评分为 Paper 内部启发式分、非真实投资评级、不构成投资建议。
 */
@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/strategy-evaluations")
@Tag(name = "Paper Strategy Evaluation API", description = "GateK Paper 策略评估只读聚合接口。")
public class PaperStrategyEvaluationController {

    private final PaperTradingApiService apiService;

    public PaperStrategyEvaluationController(PaperTradingApiService apiService) {
        this.apiService = Objects.requireNonNull(apiService, "apiService must not be null");
    }

    @GetMapping
    @Operation(summary = "查询 Paper 策略评估只读聚合",
            description = "从 strategyVersionId / publishId 维度评估 bounded Paper run：总览、各维度评分"
                    + "（sampleScore/riskScore/returnScore/executionScore/backtestDeviationScore/compositeScore）、"
                    + "ratingLabel/evaluationConfidence/warnings、Paper vs Backtest 偏差与各维度排行。"
                    + "只读，不触发任何状态机或外部调用，environment 固定 SIM/PAPER、LIVE 未开启；"
                    + "评分为 Paper 内部启发式分，非真实投资评级、不构成投资建议；Backtest 缺失时偏差为 null 并降级。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public PaperStrategyEvaluationResponse strategyEvaluations() {
        TraceIdContext.getOrCreate();
        return PaperStrategyEvaluationResponse.from(apiService.strategyEvaluations());
    }
}
