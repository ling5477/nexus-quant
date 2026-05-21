package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunScheduleCreateRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunScheduleFireResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunScheduleResponse;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunScheduleStatusUpdateRequestBody;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.research.application.paper.PaperRunScheduleCreateCommand;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/schedules")
@Tag(name = "Paper Trading Schedule API", description = "GateJ-1 Paper run 调度计划与触发记录接口。")
public class PaperTradingScheduleController {

    private final PaperTradingApiService apiService;

    public PaperTradingScheduleController(PaperTradingApiService apiService) {
        this.apiService = Objects.requireNonNull(apiService, "apiService must not be null");
    }

    @GetMapping
    @Operation(summary = "查询调度计划列表", description = "按 paperRunId / status 过滤调度计划。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<PaperRunScheduleResponse> list(
            @RequestParam(required = false) String paperRunId,
            @RequestParam(required = false) String status
    ) {
        TraceIdContext.getOrCreate();
        return apiService.listSchedules(paperRunId, status).stream()
                .map(PaperRunScheduleResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "创建调度计划", description = "为指定 Paper run 创建调度计划，第一版默认 ENABLED。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "404", description = "Paper run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunScheduleResponse create(@Valid @RequestBody PaperRunScheduleCreateRequestBody request) {
        TraceIdContext.getOrCreate();
        var command = new PaperRunScheduleCreateCommand(
                request.paperRunId(),
                request.scheduleName(),
                request.cronExpr(),
                request.timezone(),
                request.requestJson(),
                "system"
        );
        return PaperRunScheduleResponse.from(apiService.createSchedule(command));
    }

    @GetMapping("/{scheduleId}")
    @Operation(summary = "查询调度计划详情", description = "按 scheduleId 查询调度计划详情。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "调度计划不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunScheduleResponse detail(@PathVariable @NotBlank String scheduleId) {
        TraceIdContext.getOrCreate();
        return PaperRunScheduleResponse.from(apiService.getScheduleById(scheduleId));
    }

    @PatchMapping("/{scheduleId}/status")
    @Operation(summary = "更新调度计划状态", description = "ENABLED / DISABLED / PAUSED。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "无效状态", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "调度计划不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunScheduleResponse updateStatus(
            @PathVariable @NotBlank String scheduleId,
            @Valid @RequestBody PaperRunScheduleStatusUpdateRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        return PaperRunScheduleResponse.from(apiService.updateScheduleStatus(scheduleId, request.status()));
    }

    @PostMapping("/{scheduleId}/run-once")
    @Operation(summary = "手动触发一次调度", description = "对 ENABLED 状态的调度计划手动触发一次，并写入 fire 记录。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "触发成功"),
            @ApiResponse(responseCode = "404", description = "调度计划不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "调度状态非 ENABLED", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperRunScheduleFireResponse runOnce(@PathVariable @NotBlank String scheduleId) {
        TraceIdContext.getOrCreate();
        return PaperRunScheduleFireResponse.from(apiService.runScheduleOnce(scheduleId));
    }

    @GetMapping("/{scheduleId}/fires")
    @Operation(summary = "查询调度触发记录", description = "返回指定调度计划的 fire 记录列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "调度计划不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<PaperRunScheduleFireResponse> fires(@PathVariable @NotBlank String scheduleId) {
        TraceIdContext.getOrCreate();
        return apiService.listFires(scheduleId).stream()
                .map(PaperRunScheduleFireResponse::from)
                .toList();
    }
}
