package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ConsistencyEvidenceOverviewController 暴露 GateT-2 consistency evidence overview 只读 API。
 *
 * <p>Why：GateT-2 只允许实现 `GET /api/paper-shadow/consistency/evidence/overview` 的诊断 read model。
 * 本 controller 不接受 request body，不提供 POST/PUT/PATCH/DELETE，不创建 consistency report，不启动
 * runner/scheduler，不调用真实交易所，不读取 credential，也不修改 account / order / ledger / Paper / Shadow 状态。
 */
@Validated
@RestController
@RequestMapping("/api/paper-shadow/consistency/evidence")
@Tag(name = "Consistency Evidence API", description = "GateT-2 Paper vs Shadow consistency evidence 只读接口。")
public class ConsistencyEvidenceOverviewController {

    private final ConsistencyEvidenceOverviewQueryService queryService;

    public ConsistencyEvidenceOverviewController(ConsistencyEvidenceOverviewQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 查询 consistency evidence overview。
     *
     * @return read-only derived consistency evidence overview；不表达交易授权或自动处置
     */
    @GetMapping("/overview")
    @Operation(
            summary = "查询 Paper vs Shadow consistency evidence overview",
            description = "只读聚合本地 consistency evidence，派生 freshness、severity、metricDelta 摘要和安全边界；"
                    + "不写库、不创建 report、不启动 runner、不外联、不读取 credential、不触发交易。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ConsistencyEvidenceOverviewResponse overview() {
        String traceId = TraceIdContext.getOrCreate();
        return ConsistencyEvidenceOverviewResponse.from(queryService.overview(traceId));
    }
}
