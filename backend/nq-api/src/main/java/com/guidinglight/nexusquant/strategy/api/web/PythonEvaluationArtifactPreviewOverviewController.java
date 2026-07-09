package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PythonEvaluationArtifactPreviewOverviewController 暴露 GateT-4 Evaluation Artifact preview 只读 API。
 *
 * <p>Why：GateT-4 只允许实现 `GET /api/strategy-validation/evaluation-artifacts/preview/overview`
 * 的 No-file baseline read model。本 controller 不接受 request body，不提供 POST/PUT/PATCH/DELETE，
 * 不接受 file path query，不提供 upload/import/bind/execute/validate-file 写侧入口，不读取 artifact 文件
 * 或 manifest，不执行 Python subprocess，不访问网络，不创建 Paper/Shadow/LIVE run，不启动 backtest、
 * runner 或 scheduler，不读取 credential，也不修改 account / order / ledger 状态。
 */
@Validated
@RestController
@RequestMapping("/api/strategy-validation/evaluation-artifacts/preview")
@Tag(
        name = "Python Evaluation Artifact Preview API",
        description = "GateT-4 Python Evaluation Artifact binding preview No-file baseline 只读接口。"
)
public class PythonEvaluationArtifactPreviewOverviewController {

    private final PythonEvaluationArtifactPreviewOverviewQueryService queryService;

    public PythonEvaluationArtifactPreviewOverviewController(PythonEvaluationArtifactPreviewOverviewQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 查询 Python Evaluation Artifact binding preview overview。
     *
     * @return read-only No-file baseline overview；不表达 ML ready、live execution ready 或交易授权
     */
    @GetMapping("/overview")
    @Operation(
            summary = "查询 Python Evaluation Artifact binding preview overview",
            description = "返回 GateT-4 No-file baseline：当前没有 artifact source，且只表达只读诊断边界；"
                    + "不读取 artifact 文件、manifest、任意路径、上传文件或网络资源，不执行 Python，不写库，"
                    + "不创建 Paper/Shadow/LIVE run，不触发回测或交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PythonEvaluationArtifactPreviewOverviewResponse overview() {
        String traceId = TraceIdContext.getOrCreate();
        return PythonEvaluationArtifactPreviewOverviewResponse.from(queryService.overview(traceId));
    }
}
