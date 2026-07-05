package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonQuery;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PaperShadowComparisonController 暴露 GateQ-2 Paper vs Shadow 只读 API。
 *
 * <p>Why: controller 只解析 query 参数并委托 read-only service 聚合本地事实；它不会启动
 * Shadow runner、不会创建 Paper/Shadow run、不会触发 evaluation/publish 写侧，也不会调用真实交易所或读取敏感材料。
 */
@Validated
@RestController
@RequestMapping("/api/strategies/paper-shadow/comparison")
@Tag(name = "Paper Shadow Comparison API", description = "GateQ-2 Paper vs Shadow 只读对照接口。")
public class PaperShadowComparisonController {

    private final PaperShadowComparisonService paperShadowComparisonService;

    public PaperShadowComparisonController(PaperShadowComparisonService paperShadowComparisonService) {
        this.paperShadowComparisonService = Objects.requireNonNull(
                paperShadowComparisonService,
                "paperShadowComparisonService must not be null"
        );
    }

    /**
     * 查询某个 strategy version 在现有 dataset/evaluation/publish/Paper/Shadow 事实下的只读对照准备度。
     *
     * @param strategyId 可选策略定义 ID 或 strategyCode，仅用于 scope 校验和回显
     * @param strategyVersionId 必填语义字段；为空时 service fail-closed，而不是伪造 ready
     * @param datasetId 可选 datasetId；为空或不存在时 fail-closed
     * @param evaluationId 可选 evaluation report id；为空时 repository 尝试按 strategyVersion/publish 解析
     * @param publishId 可选 publish record id；为空时 repository 尝试按 evaluation/strategyVersion 解析
     * @param paperRunId 可选 Paper run id；为空时 repository 尝试按 publish/strategyVersion 解析 SIM evidence
     * @param shadowRunId 可选 Shadow run id；当前生产 fact source 未实现时返回 NOT_IMPLEMENTED / BLOCKED
     * @return 只读 comparison 结果；READY_FOR_COMPARISON 也仅代表可进入只读对照查看
     */
    @GetMapping
    @Operation(
            summary = "查询 Paper vs Shadow 只读 comparison baseline",
            description = "只读聚合 strategy version、dataset quality、evaluation、publish trace、SIM Paper evidence "
                    + "和 Shadow 未实现状态；不启动 Shadow runner、不接 LIVE、不接 AI/DH runtime、不调用真实交易所。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperShadowComparisonResponse getComparison(
            @RequestParam(required = false) String strategyId,
            @RequestParam(required = false) String strategyVersionId,
            @RequestParam(required = false) UUID datasetId,
            @RequestParam(required = false) String evaluationId,
            @RequestParam(required = false) String publishId,
            @RequestParam(required = false) String paperRunId,
            @RequestParam(required = false) String shadowRunId
    ) {
        TraceIdContext.getOrCreate();
        return PaperShadowComparisonResponse.from(paperShadowComparisonService.compare(
                new PaperShadowComparisonQuery(
                        strategyId,
                        strategyVersionId,
                        datasetId,
                        evaluationId,
                        publishId,
                        paperRunId,
                        shadowRunId
                )
        ));
    }
}
