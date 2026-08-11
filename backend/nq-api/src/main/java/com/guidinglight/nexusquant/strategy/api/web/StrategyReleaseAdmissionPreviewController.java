package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Strategy Release-to-Shadow admission preview 的只读 HTTP 入口。
 *
 * <p>Controller 只接受 publishRecordId 并委托 server-owned fact orchestration；不接受 path、digest、
 * validation 或 safety truth，不创建/启动 Shadow Run，不调用 runner/trading/private API，也不写库。
 */
@Validated
@RestController
@RequestMapping("/api/strategy-releases")
@Tag(name = "Strategy Release API", description = "Strategy Release 与 Shadow 准入的只读预览接口。")
public class StrategyReleaseAdmissionPreviewController {

    private final StrategyReleaseAdmissionPreviewService previewService;

    public StrategyReleaseAdmissionPreviewController(StrategyReleaseAdmissionPreviewService previewService) {
        this.previewService = Objects.requireNonNull(previewService, "previewService must not be null");
    }

    /**
     * 查询指定 publish record 的 Shadow admission preview。
     *
     * @param publishRecordId canonical publish anchor；唯一客户端事实
     * @return 只读结果；ELIGIBLE 仅表示可形成内存 creation plan，不表示 run created 或 trading authorized
     * @throws ResponseStatusException publish record 不存在时返回安全 404
     */
    @GetMapping("/{publishRecordId}/shadow-admission-preview")
    @Operation(
            summary = "查询 Strategy Release 的 Shadow 准入预览",
            description = "服务端解析 release、验证 artifact 并复用既有 admission 规则；只查询和解释，"
                    + "不创建/启动 Shadow Run，不触发交易、LIVE 或任何业务写入。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功；可能为 BLOCKED 或 ELIGIBLE"),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "publish record 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyReleaseAdmissionPreviewResponse preview(@PathVariable String publishRecordId) {
        String traceId = TraceIdContext.getOrCreate();
        return previewService.preview(publishRecordId, traceId)
                .map(StrategyReleaseAdmissionPreviewResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "strategy release publish record not found"
                ));
    }
}
