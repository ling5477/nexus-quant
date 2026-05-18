package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.research.api.dto.BacktestPublishRequestBody;
import com.guidinglight.nexusquant.research.api.dto.BacktestPublishResponse;
import com.guidinglight.nexusquant.research.application.eval.api.BacktestRunApiService;
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

/**
 * PublishController 提供 GateI-1 正式发布记录入口。
 *
 * Why:
 * 历史发布接口挂在 `/api/backtest-runs/{runId}/publish` 下，适合回测运行详情页。
 * GateI-1 需要独立 `/api/publishes` 入口展示发布记录与策略版本绑定关系，但仍复用既有发布服务和风控边界。
 */
@Validated
@RestController
@ConditionalOnBean(BacktestRunApiService.class)
@RequestMapping("/api/publishes")
@Tag(name = "Publish API", description = "GateI-1 发布版本管理接口。")
public class PublishController {

    private final BacktestRunApiService backtestRunApiService;

    public PublishController(BacktestRunApiService backtestRunApiService) {
        this.backtestRunApiService = Objects.requireNonNull(backtestRunApiService, "backtestRunApiService must not be null");
    }

    /**
     * 查询发布记录列表。
     *
     * @param strategyVersionId 可选策略版本 ID 过滤条件
     * @return 发布记录列表
     */
    @GetMapping
    @Operation(summary = "查询发布记录列表", description = "返回 GateI-1 发布记录列表，可按策略版本过滤。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<BacktestPublishResponse> list(@RequestParam(required = false) String strategyVersionId) {
        TraceIdContext.getOrCreate();
        return backtestRunApiService.listPublishes().stream()
                .filter(record -> strategyVersionId == null || strategyVersionId.isBlank()
                        || strategyVersionId.equals(record.strategyVersionId()))
                .map(BacktestPublishResponse::from)
                .toList();
    }

    /**
     * 查询发布记录详情。
     *
     * @param publishId 发布记录 ID
     * @return 发布记录详情
     */
    @GetMapping("/{publishId}")
    @Operation(summary = "查询发布记录详情", description = "返回发布记录和发布时固化的 strategy version snapshot。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "发布记录不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestPublishResponse detail(@PathVariable @NotBlank(message = "publishId must not be blank") String publishId) {
        TraceIdContext.getOrCreate();
        return BacktestPublishResponse.from(backtestRunApiService.getPublishById(publishId));
    }

    /**
     * 创建发布记录。
     *
     * @param backtestRunId 回测运行 ID
     * @param request 发布请求；可携带策略版本 ID
     * @return 发布记录
     */
    @PostMapping
    @Operation(summary = "创建发布记录", description = "按 backtestRunId 发布并可选绑定 strategyVersionId。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "发布成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "回测运行或策略版本不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "发布状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BacktestPublishResponse create(
            @RequestParam @NotBlank(message = "backtestRunId must not be blank") String backtestRunId,
            @Valid @RequestBody(required = false) BacktestPublishRequestBody request
    ) {
        TraceIdContext.getOrCreate();
        return BacktestPublishResponse.from(backtestRunApiService.publish(
                backtestRunId,
                request == null ? null : request.displayName(),
                request == null ? null : request.strategyVersionId()
        ));
    }
}
