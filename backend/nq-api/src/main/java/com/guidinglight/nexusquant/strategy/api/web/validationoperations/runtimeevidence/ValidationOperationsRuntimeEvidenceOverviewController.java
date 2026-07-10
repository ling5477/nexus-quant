package com.guidinglight.nexusquant.strategy.api.web.validationoperations.runtimeevidence;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;
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
 * ValidationOperationsRuntimeEvidenceOverviewController 暴露 Validation Operations Runtime Evidence 的只读聚合接口。
 *
 * <p>该 controller 不接受 request body，不提供写方法，不调用来源 controller 或内部 HTTP；它只把当前 traceId
 * 交给 application aggregate service，结果固定为诊断用途且不构成交易授权。
 */
@Validated
@RestController
@RequestMapping("/api/validation-operations/runtime-evidence")
@Tag(name = "Validation Operations Runtime Evidence API", description = "五个既有诊断 metadata 的只读聚合接口。")
public class ValidationOperationsRuntimeEvidenceOverviewController {

    private final ValidationOperationsRuntimeEvidenceOverviewQueryService queryService;

    public ValidationOperationsRuntimeEvidenceOverviewController(
            ValidationOperationsRuntimeEvidenceOverviewQueryService queryService
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 查询 runtime evidence overview。
     *
     * @return 五个固定来源的诊断 metadata 聚合；不表示全部正常、执行就绪或交易授权
     */
    @GetMapping("/overview")
    @Operation(
            summary = "查询 Validation Operations Runtime Evidence Overview",
            description = "只读调用五个既有 overview service 并聚合其 evidence metadata；不写库、不启动 scheduler/runner、"
                    + "不读取 credential、不调用 adapter 或交易接口。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ValidationOperationsRuntimeEvidenceOverviewResponse overview() {
        String traceId = TraceIdContext.getOrCreate();
        return ValidationOperationsRuntimeEvidenceOverviewResponse.from(queryService.overview(traceId));
    }
}
