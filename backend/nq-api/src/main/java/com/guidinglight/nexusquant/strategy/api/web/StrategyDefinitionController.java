package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.StrategyDefinitionCreateRequest;
import com.guidinglight.nexusquant.strategy.application.StrategyDefinitionService;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerRequest;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerService;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * StrategyDefinitionController 提供正式策略定义管理与手工触发接口。
 */
@Validated
@RestController
@RequestMapping("/api/strategies")
@Tag(name = "Strategy Definition API", description = "正式策略定义管理与手工触发接口。")
public class StrategyDefinitionController {

    private final StrategyDefinitionService strategyDefinitionService;
    private final StrategyManualTriggerService strategyManualTriggerService;
    private final StrategyVersionService strategyVersionService;

    public StrategyDefinitionController(
            StrategyDefinitionService strategyDefinitionService,
            StrategyManualTriggerService strategyManualTriggerService,
            StrategyVersionService strategyVersionService
    ) {
        this.strategyDefinitionService = Objects.requireNonNull(strategyDefinitionService, "strategyDefinitionService must not be null");
        this.strategyManualTriggerService = Objects.requireNonNull(strategyManualTriggerService, "strategyManualTriggerService must not be null");
        this.strategyVersionService = Objects.requireNonNull(strategyVersionService, "strategyVersionService must not be null");
    }

    @PostMapping
    @Operation(summary = "创建策略定义", description = "创建一条可供后续运行或调度消费的策略定义事实。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "业务状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyDefinitionResponse create(@Valid @RequestBody StrategyDefinitionCreateRequestBody request) {
        TraceIdContext.getOrCreate();
        return StrategyDefinitionResponse.from(strategyDefinitionService.create(new StrategyDefinitionCreateRequest(
                request.strategyCode(),
                request.strategyName(),
                request.strategyType(),
                request.exchangeCode(),
                request.accountId(),
                request.tradeEnv(),
                request.configSnapshot()
        )));
    }

    @GetMapping
    @Operation(summary = "列出策略定义", description = "返回当前可见的策略定义列表，不触发写操作。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<StrategyDefinitionResponse> list() {
        TraceIdContext.getOrCreate();
        return strategyDefinitionService.listAll().stream().map(StrategyDefinitionResponse::from).toList();
    }

    @GetMapping("/{strategyCode}")
    @Operation(summary = "查询策略定义详情", description = "按策略编码查询单条策略定义。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "路径参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "策略定义不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyDefinitionResponse detail(
            @PathVariable @NotBlank(message = "strategyCode must not be blank") String strategyCode
    ) {
        TraceIdContext.getOrCreate();
        return StrategyDefinitionResponse.from(strategyDefinitionService.getByStrategyCode(strategyCode));
    }

    @PatchMapping("/{strategyCode}/status")
    @Operation(summary = "更新策略定义状态", description = "将策略定义切换为启用或禁用状态。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "业务状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyDefinitionResponse updateStatus(
            @PathVariable @NotBlank(message = "strategyCode must not be blank") String strategyCode,
            @Valid @RequestBody StrategyDefinitionStatusUpdateRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        return StrategyDefinitionResponse.from(Boolean.TRUE.equals(request.enabled())
                ? strategyDefinitionService.enableByStrategyCode(strategyCode)
                : strategyDefinitionService.disableByStrategyCode(strategyCode));
    }

    @PostMapping("/{strategyCode}/trigger")
    @Operation(summary = "手工触发策略", description = "创建一次手工策略运行，并按请求参数进入后续执行主链。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "触发成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "业务状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyManualTriggerResponse trigger(
            @PathVariable @NotBlank(message = "strategyCode must not be blank") String strategyCode,
            @Valid @RequestBody StrategyManualTriggerRequestBody request
    ) {
        String traceId = TraceIdContext.getOrCreate();
        return StrategyManualTriggerResponse.from(strategyManualTriggerService.trigger(new StrategyManualTriggerRequest(
                strategyCode,
                request.requestId(),
                request.symbol(),
                request.side(),
                request.orderType(),
                request.quantity(),
                request.price(),
                traceId
        )));
    }

    @GetMapping("/{strategyCode}/versions")
    @Operation(summary = "查询策略版本列表", description = "返回指定策略编码下的 GateI-1 策略版本列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "路径参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<StrategyVersionResponse> versions(
            @PathVariable @NotBlank(message = "strategyCode must not be blank") String strategyCode
    ) {
        TraceIdContext.getOrCreate();
        return strategyVersionService.listByStrategyCode(strategyCode).stream()
                .map(StrategyVersionResponse::from)
                .toList();
    }

    @PostMapping("/{strategyCode}/versions")
    @Operation(summary = "创建策略版本", description = "为指定策略定义创建参数、配置和来源快照。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "策略定义不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "版本创建冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyVersionResponse createVersion(
            @PathVariable @NotBlank(message = "strategyCode must not be blank") String strategyCode,
            @Valid @RequestBody StrategyVersionCreateRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        return StrategyVersionResponse.from(strategyVersionService.create(new StrategyVersionCreateRequest(
                strategyCode,
                request.versionName(),
                request.status(),
                request.paramSnapshotJson(),
                request.configSnapshotJson(),
                request.sourceSnapshotJson(),
                "api"
        )));
    }

    @GetMapping("/{strategyCode}/versions/{versionId}")
    @Operation(summary = "查询策略版本详情", description = "返回指定策略版本的快照详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "路径参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "策略版本不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyVersionResponse versionDetail(
            @PathVariable @NotBlank(message = "strategyCode must not be blank") String strategyCode,
            @PathVariable @NotBlank(message = "versionId must not be blank") String versionId
    ) {
        TraceIdContext.getOrCreate();
        return StrategyVersionResponse.from(strategyVersionService.getById(strategyCode, versionId));
    }
}



