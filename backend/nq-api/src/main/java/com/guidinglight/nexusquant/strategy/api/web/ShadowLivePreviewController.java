package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewQuery;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewService;
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
 * ShadowLivePreviewController 暴露 GateQ-3 Shadow Live no-side-effect preview API。
 *
 * <p>Why: controller 只解析 query 参数并委托 read-only service 聚合 GateQ-1/GateQ-2 本地事实；
 * 它不会启动 Shadow runner、不会创建 Paper/Shadow run、不会触发策略执行，也不会调用真实交易所或读取敏感材料。
 */
@Validated
@RestController
@RequestMapping("/api/strategies/shadow-live/preview")
@Tag(name = "Shadow Live Preview API", description = "GateQ-3 Shadow Live no-side-effect 只读预览接口。")
public class ShadowLivePreviewController {

    private final ShadowLivePreviewService shadowLivePreviewService;

    public ShadowLivePreviewController(ShadowLivePreviewService shadowLivePreviewService) {
        this.shadowLivePreviewService = Objects.requireNonNull(
                shadowLivePreviewService,
                "shadowLivePreviewService must not be null"
        );
    }

    /**
     * 查询当前 facts 是否足以生成 Shadow Live no-side-effect preview。
     *
     * @param strategyId 可选策略定义 ID 或 strategyCode，仅用于 scope 校验和回显
     * @param strategyVersionId 必填语义字段；为空时 service fail-closed，而不是伪造 ready
     * @param datasetId 可选 datasetId；为空或质量不足时 fail-closed
     * @param evaluationId 可选 evaluation report id；为空时由 GateQ-1/GateQ-2 只读 facts 尝试解析
     * @param publishId 可选 publish record id；为空时由 GateQ-1/GateQ-2 只读 facts 尝试解析
     * @param paperRunId 可选 Paper run id；为空时由 GateQ-1/GateQ-2 只读 facts 尝试解析 SIM evidence
     * @param shadowRunId 可选 Shadow run id；当前生产 fact source 未实现时返回 NOT_AVAILABLE / blocked
     * @return 只读 preview 结果；READY_FOR_NO_SIDE_EFFECT_PREVIEW 也不代表交易授权或实盘放行
     */
    @GetMapping
    @Operation(
            summary = "查询 Shadow Live no-side-effect preview skeleton",
            description = "只读聚合 Strategy Evaluation Gate 与 Paper/Shadow Comparison 结果；不启动 Shadow runner、"
                    + "不写库、不外联、不读取敏感材料、不启用 LIVE / AI / DH runtime。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ShadowLivePreviewResponse getPreview(
            @RequestParam(required = false) String strategyId,
            @RequestParam(required = false) String strategyVersionId,
            @RequestParam(required = false) UUID datasetId,
            @RequestParam(required = false) String evaluationId,
            @RequestParam(required = false) String publishId,
            @RequestParam(required = false) String paperRunId,
            @RequestParam(required = false) String shadowRunId
    ) {
        TraceIdContext.getOrCreate();
        return ShadowLivePreviewResponse.from(shadowLivePreviewService.preview(
                new ShadowLivePreviewQuery(
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
