package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.api.service.BacktestConfigApiService;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BacktestConfigController 提供正式回测配置创建接口。
 * <p>
 * Why:
 * Controller 只负责参数边界与业务调用，不再承担 trace header 解析与 MDC 包装。
 */
@Validated
@RestController
@ConditionalOnBean(BacktestConfigApiService.class)
@RequestMapping("/api/backtest-configs")
@Tag(name = "Backtest Config API", description = "正式回测配置创建接口。")
public class BacktestConfigController {

    private final BacktestConfigApiService applicationService;

    public BacktestConfigController(BacktestConfigApiService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    /**
     * 创建回测配置。
     */
    @PostMapping
    @Operation(summary = "创建回测配置", description = "创建一条新的回测配置，供后续回测运行直接引用。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "回测配置创建冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestConfigResponse create(@Valid @RequestBody BacktestConfigCreateRequestBody request) {
        String traceId = TraceIdContext.getOrCreate();
        return BacktestConfigResponse.from(applicationService.create(
                request.researchConfigId(),
                request.name(),
                request.description(),
                request.startTime(),
                request.endTime(),
                request.initialCapital(),
                request.executionSpec(),
                request.evaluationSpec()
        ));
    }
}
