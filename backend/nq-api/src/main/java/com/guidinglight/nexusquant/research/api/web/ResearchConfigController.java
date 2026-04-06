package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.research.api.application.ResearchConfigApiService;
import com.guidinglight.nexusquant.research.api.dto.ResearchConfigCreateRequestBody;
import com.guidinglight.nexusquant.research.api.dto.ResearchConfigResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ResearchConfigController 提供正式研究配置创建接口。
 * <p>
 * Why:
 * Step 4 要把 trace 读取统一收口到过滤器和 `TraceIdContext`，
 * 因此 Controller 不再手工解析 header，也不再自行管理 MDC 生命周期。
 */
@Validated
@RestController
@ConditionalOnBean(ResearchConfigApiService.class)
@RequestMapping("/api/research-configs")
@Tag(name = "Research Config API", description = "正式研究配置创建接口。")
public class ResearchConfigController {

    private final ResearchConfigApiService applicationService;

    public ResearchConfigController(ResearchConfigApiService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    /**
     * 查询研究配置列表。
     */
    @GetMapping
    @Operation(summary = "查询研究配置列表", description = "按 sourceStrategyId 可选过滤研究配置列表，默认按创建时间倒序返回。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<ResearchConfigResponse> list(
            @Parameter(description = "按 sourceStrategyId 过滤，可空")
            @RequestParam(required = false)
            @Size(max = 64, message = "sourceStrategyId length must be less than or equal to 64")
            String sourceStrategyId
    ) {
        TraceIdContext.getOrCreate();
        return applicationService.list(sourceStrategyId).stream()
                .map(ResearchConfigResponse::from)
                .toList();
    }

    /**
     * 查询研究配置详情。
     */
    @GetMapping("/{configId}")
    @Operation(summary = "查询研究配置详情", description = "按 researchConfigId 查询单条研究配置详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "研究配置不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResearchConfigResponse detail(
            @PathVariable
            @NotBlank(message = "configId must not be blank")
            String configId
    ) {
        TraceIdContext.getOrCreate();
        return ResearchConfigResponse.from(applicationService.getByResearchConfigId(configId));
    }

    /**
     * 创建研究配置。
     */
    @PostMapping
    @Operation(summary = "创建研究配置", description = "创建一条新的研究配置，供后续回测配置与回测运行引用。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "研究配置创建冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResearchConfigResponse create(@Valid @RequestBody ResearchConfigCreateRequestBody request) {
        String traceId = TraceIdContext.getOrCreate();
        return ResearchConfigResponse.from(applicationService.create(
                request.sourceStrategyId(),
                request.name(),
                request.description(),
                request.parameterSchema(),
                request.parameterDefaults(),
                request.datasetSpec()
        ));
    }
}



