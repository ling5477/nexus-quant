package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.research.api.dto.BacktestEvaluationResponse;
import com.guidinglight.nexusquant.research.application.eval.api.BacktestRunApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EvaluationController 提供 GateI-2 独立评估报告查询入口。
 *
 * Why:
 * 历史评估接口挂在 `/api/backtest-runs/{runId}/evaluation` 下，适合 run 详情页。
 * GateI-2 需要 `/evaluations` 页面直接读取核心指标列表，因此新增只读 controller，
 * 仍复用 eval application service，不在 API 层写 SQL，也不触发 AI 分析或 Paper Trading。
 */
@Validated
@RestController
@ConditionalOnBean(BacktestRunApiService.class)
@RequestMapping("/api/evaluations")
@Tag(name = "Evaluation API", description = "GateI-2 评估报告查询接口。")
public class EvaluationController {

    private final BacktestRunApiService backtestRunApiService;

    public EvaluationController(BacktestRunApiService backtestRunApiService) {
        this.backtestRunApiService = Objects.requireNonNull(backtestRunApiService, "backtestRunApiService must not be null");
    }

    /**
     * 查询评估报告列表。
     *
     * @param researchConfigId 可选研究配置 ID
     * @param backtestConfigId 可选回测配置 ID
     * @return 当前筛选范围内已生成的评估报告列表
     */
    @GetMapping
    @Operation(summary = "查询评估报告列表", description = "按 researchConfigId / backtestConfigId 可选过滤并返回 GateI-2 评估报告核心指标列表。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<BacktestEvaluationResponse> list(
            @RequestParam(required = false) String researchConfigId,
            @RequestParam(required = false) String backtestConfigId
    ) {
        TraceIdContext.getOrCreate();
        return backtestRunApiService.listEvaluations(researchConfigId, backtestConfigId).stream()
                .map(BacktestEvaluationResponse::from)
                .toList();
    }

    /**
     * 查询评估报告详情。
     *
     * @param evaluationId 评估报告 ID
     * @return 评估报告详情
     */
    @GetMapping("/{evaluationId}")
    @Operation(summary = "查询评估报告详情", description = "按 evalReportId 返回评估报告完整指标与 metricsJson。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "评估报告不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestEvaluationResponse detail(
            @PathVariable @NotBlank(message = "evaluationId must not be blank") String evaluationId
    ) {
        TraceIdContext.getOrCreate();
        return BacktestEvaluationResponse.from(backtestRunApiService.getEvaluationById(evaluationId));
    }
}
