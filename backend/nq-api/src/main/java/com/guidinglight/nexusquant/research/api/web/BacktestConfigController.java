package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.marketdata.application.MarketdataDatasetService;
import com.guidinglight.nexusquant.research.application.api.backtest.BacktestConfigApiService;
import com.guidinglight.nexusquant.research.api.dto.BacktestConfigCreateRequestBody;
import com.guidinglight.nexusquant.research.api.dto.BacktestConfigResponse;
import com.guidinglight.nexusquant.research.api.dto.BacktestDatasetBindingRequestBody;
import com.guidinglight.nexusquant.research.api.dto.BacktestStrategyVersionBindingRequestBody;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final MarketdataDatasetService marketdataDatasetService;

    public BacktestConfigController(
            BacktestConfigApiService applicationService,
            MarketdataDatasetService marketdataDatasetService
    ) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
        this.marketdataDatasetService = Objects.requireNonNull(
                marketdataDatasetService,
                "marketdataDatasetService must not be null"
        );
    }

    /**
     * 查询回测配置列表。
     */
    @GetMapping
    @Operation(summary = "查询回测配置列表", description = "按 researchConfigId 可选过滤回测配置列表，默认按创建时间倒序返回。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "关联研究配置不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<BacktestConfigResponse> list(
            @Parameter(description = "按 researchConfigId 过滤，可空")
            @RequestParam(required = false)
            @Size(max = 64, message = "researchConfigId length must be less than or equal to 64")
            String researchConfigId
    ) {
        TraceIdContext.getOrCreate();
        return applicationService.list(researchConfigId).stream()
                .map(BacktestConfigResponse::from)
                .toList();
    }

    /**
     * 查询回测配置详情。
     */
    @GetMapping("/{configId}")
    @Operation(summary = "查询回测配置详情", description = "按 backtestConfigId 查询单条回测配置详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "回测配置不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestConfigResponse detail(
            @PathVariable
            @NotBlank(message = "configId must not be blank")
            String configId
    ) {
        TraceIdContext.getOrCreate();
        return BacktestConfigResponse.from(applicationService.getByBacktestConfigId(configId));
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

    /**
     * 绑定 GateH-3 marketdata dataset 到回测配置。
     * Why:
     * controller 只负责 HTTP 参数边界和跨应用服务调用：dataset 详情由 marketdata application 生成快照，
     * backtest 配置由 research application 持久化绑定；这里不写 SQL、不启动回测、不改变回测算法。
     */
    @PatchMapping("/{configId}/dataset")
    @Operation(summary = "绑定行情数据集", description = "把已创建的 marketdata dataset 绑定到回测配置并保存 dataset 快照。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "绑定成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "回测配置或数据集不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestConfigResponse bindDataset(
            @PathVariable
            @NotBlank(message = "configId must not be blank")
            String configId,
            @Valid @RequestBody BacktestDatasetBindingRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        String datasetSnapshot = marketdataDatasetService.buildDatasetSnapshot(java.util.UUID.fromString(request.datasetId()));
        return BacktestConfigResponse.from(applicationService.bindDataset(
                configId,
                request.datasetId(),
                datasetSnapshot
        ));
    }

    /**
     * 绑定 GateI-2 strategy version 到回测配置。
     * Why:
     * controller 不接收调用方自带快照，只传递 strategyVersionId 给 application service；
     * 版本快照、参数快照由后端从 `strategy_versions` 固化，避免前端伪造回测输入血缘。
     */
    @PatchMapping("/{configId}/strategy-version")
    @Operation(summary = "绑定策略版本", description = "把 strategy version 绑定到回测配置，并保存版本快照和参数快照。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "绑定成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "回测配置或策略版本不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestConfigResponse bindStrategyVersion(
            @PathVariable
            @NotBlank(message = "configId must not be blank")
            String configId,
            @Valid @RequestBody BacktestStrategyVersionBindingRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        return BacktestConfigResponse.from(applicationService.bindStrategyVersion(
                configId,
                request.strategyVersionId()
        ));
    }
}




