package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingOrderResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingPositionResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingRunCreateRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingRunResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingTradeResponse;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunCreateCommand;
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/runs")
@Tag(name = "Paper Trading API", description = "GateI-3 SIM/Paper Trading 运行闭环接口。")
public class PaperTradingController {

    private final PaperTradingApiService apiService;

    public PaperTradingController(PaperTradingApiService apiService) {
        this.apiService = Objects.requireNonNull(apiService, "apiService must not be null");
    }

    @GetMapping
    @Operation(summary = "查询 Paper run 列表", description = "按 publishId 或 status 过滤 Paper Trading run 列表。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<PaperTradingRunResponse> list(
            @RequestParam(required = false) String publishId,
            @RequestParam(required = false) String status
    ) {
        TraceIdContext.getOrCreate();
        return apiService.list(publishId, status).stream()
                .map(PaperTradingRunResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "创建 Paper run", description = "基于 publishId 创建 Paper Trading run，固化 publish/strategy version/dataset/param/config 快照。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "404", description = "发布记录不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperTradingRunResponse create(@Valid @RequestBody PaperTradingRunCreateRequestBody request) {
        TraceIdContext.getOrCreate();
        var command = new PaperTradingRunCreateCommand(
                request.publishId(),
                request.tradeEnv(),
                request.exchangeCode(),
                request.marketType(),
                request.symbol(),
                request.intervalCode(),
                request.configSnapshotJson(),
                "system"
        );
        return PaperTradingRunResponse.from(apiService.create(command));
    }

    @GetMapping("/{paperRunId}")
    @Operation(summary = "查询 Paper run 详情", description = "按 paperRunId 查询 Paper Trading run 详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperTradingRunResponse detail(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperTradingRunResponse.from(apiService.getById(paperRunId));
    }

    @PostMapping("/{paperRunId}/start")
    @Operation(summary = "启动 Paper run", description = "把 CREATED 状态的 Paper run 推进到 RUNNING。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "启动成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperTradingRunResponse start(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperTradingRunResponse.from(apiService.start(paperRunId));
    }

    @PostMapping("/{paperRunId}/stop")
    @Operation(summary = "停止 Paper run", description = "把 RUNNING 状态的 Paper run 推进到 STOPPED。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "停止成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperTradingRunResponse stop(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperTradingRunResponse.from(apiService.stop(paperRunId));
    }

    @GetMapping("/{paperRunId}/orders")
    @Operation(summary = "查询 Paper orders", description = "返回指定 Paper run 的订单事实列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperTradingOrderResponse> orders(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listOrders(paperRunId).stream()
                .map(PaperTradingOrderResponse::from)
                .toList();
    }

    @GetMapping("/{paperRunId}/trades")
    @Operation(summary = "查询 Paper trades", description = "返回指定 Paper run 的成交事实列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperTradingTradeResponse> trades(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listTrades(paperRunId).stream()
                .map(PaperTradingTradeResponse::from)
                .toList();
    }

    @GetMapping("/{paperRunId}/positions")
    @Operation(summary = "查询 Paper positions", description = "返回指定 Paper run 的持仓事实列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperTradingPositionResponse> positions(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return apiService.listPositions(paperRunId).stream()
                .map(PaperTradingPositionResponse::from)
                .toList();
    }
}
