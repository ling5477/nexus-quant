package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingQuery;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PythonEvaluationArtifactBindingPreviewController 暴露 GateQ-4 Python artifact binding preview API。
 *
 * <p>Why: controller 只把 request body 转为 core query 并委托 read-only validator。它不会
 * 读取本地 artifact 文件、不会新增 import/upload endpoint、不会写数据库、不会启动策略、Paper run
 * 或 Shadow run，也不会访问外部网络。
 */
@Validated
@RestController
@RequestMapping("/api/research/evaluation-artifacts/binding-preview")
@Tag(name = "Python Evaluation Artifact Binding Preview API", description = "GateQ-4 Python offline artifact 只读绑定预览接口。")
public class PythonEvaluationArtifactBindingPreviewController {

    private final PythonEvaluationArtifactBindingService bindingService;

    public PythonEvaluationArtifactBindingPreviewController(PythonEvaluationArtifactBindingService bindingService) {
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService must not be null");
    }

    /**
     * 生成 Python offline evaluation artifact 到 Java fact source 的只读绑定预览。
     *
     * @param request request body；可空，service 会返回 fail-closed preview 而不是执行导入
     * @return 只读 binding preview；VALID_FOR_BINDING_PREVIEW 不代表写库、发布或交易授权
     */
    @PostMapping
    @Operation(
            summary = "预览 Python evaluation artifact 绑定契约",
            description = "只校验 request body 中的 artifact JSON、expected anchors 和 offline boundary；不上传、"
                    + "不导入、不写库、不启动策略/Paper/Shadow run、不启用 LIVE / AI / DH runtime。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "预览完成，结果可能为 blocked"),
            @ApiResponse(responseCode = "400", description = "请求 JSON 非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PythonEvaluationArtifactBindingPreviewResponse preview(
            @RequestBody(required = false) PythonEvaluationArtifactBindingPreviewRequest request
    ) {
        TraceIdContext.getOrCreate();
        PythonEvaluationArtifactBindingPreviewRequest safeRequest = request == null
                ? new PythonEvaluationArtifactBindingPreviewRequest(null, null, null, null, null, null, null, null, null)
                : request;
        return PythonEvaluationArtifactBindingPreviewResponse.from(bindingService.preview(new PythonEvaluationArtifactBindingQuery(
                safeRequest.artifact(),
                safeRequest.expectedDatasetId(),
                safeRequest.expectedStrategyVersionId(),
                safeRequest.expectedStrategyVersion(),
                safeRequest.expectedEvaluationVersion(),
                safeRequest.expectedChecksum(),
                safeRequest.expectedParametersHash(),
                safeRequest.source(),
                safeRequest.dryRun()
        )));
    }
}
