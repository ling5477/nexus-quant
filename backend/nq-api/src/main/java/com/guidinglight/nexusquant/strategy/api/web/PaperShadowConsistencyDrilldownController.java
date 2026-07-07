package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PaperShadowConsistencyDrilldownController 暴露 GateS-2 Paper vs Shadow consistency drilldown 只读 API。
 *
 * <p>Why：`docs/current/GATES_1_READ_MODEL_WO.md` 已把
 * `GET /api/paper-shadow/consistency/drilldown` 作为 GateS-2 Paper vs Shadow 增强路径。
 * 本 controller 只处理 GET 查询，不提供 POST/PUT/PATCH/DELETE，不创建 report，不启动 runner/scheduler，
 * 不调用真实交易所，不读取 credential，也不修改 account / ledger / order / paper facts。
 */
@Validated
@RestController
@RequestMapping("/api/paper-shadow/consistency")
@Tag(name = "Paper Shadow Consistency API", description = "GateS-2 Paper vs Shadow consistency 只读诊断接口。")
public class PaperShadowConsistencyDrilldownController {

    private final PaperShadowConsistencyDrilldownQueryService queryService;

    public PaperShadowConsistencyDrilldownController(PaperShadowConsistencyDrilldownQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 查询单个 Shadow Run 的 Paper vs Shadow consistency drilldown。
     *
     * @param shadowRunId 本地 Shadow Run id；只用于 SELECT 查询
     * @return drilldown read model；固定 diagnosticOnly / noSideEffect / notTradingAuthorization
     */
    @GetMapping("/drilldown")
    @Operation(
            summary = "查询 Paper vs Shadow consistency drilldown",
            description = "只读返回单个 Shadow Run 的 consistency report、snapshot/event 摘要和安全边界；"
                    + "不创建 report、不启动 runner、不外联、不读取 credential、不触发交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "查询参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Shadow Run 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaperShadowConsistencyDrilldownResponse drilldown(@RequestParam UUID shadowRunId) {
        String traceId = TraceIdContext.getOrCreate();
        return PaperShadowConsistencyDrilldownResponse.from(queryService.drilldown(shadowRunId, traceId));
    }
}
