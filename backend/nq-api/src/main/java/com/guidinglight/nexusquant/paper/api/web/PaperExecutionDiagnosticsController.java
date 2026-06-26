package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.paper.api.dto.PaperExecutionDiagnosticsResponse;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PaperExecutionDiagnosticsController —— Paper 执行诊断只读聚合入口（GateK Batch K1）。
 *
 * 对 bounded Paper run 做规则化执行归因（无订单 / 有订单无成交 / 成交亏损 / 风控拦截 / 数据不足 /
 * 高回撤 / 异常终态），并按 strategyVersionId / publishId 聚合，供前端执行诊断视图消费。
 * 只读：不触发任何状态机、调度、回测、发布或外部调用；只覆盖 SIM/Paper，LIVE 未开启，诊断不构成投资建议。
 */
@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/execution-diagnostics")
@Tag(name = "Paper Execution Diagnostics API", description = "GateK Paper 执行诊断只读聚合接口。")
public class PaperExecutionDiagnosticsController {

    private final PaperTradingApiService apiService;

    public PaperExecutionDiagnosticsController(PaperTradingApiService apiService) {
        this.apiService = Objects.requireNonNull(apiService, "apiService must not be null");
    }

    @GetMapping
    @Operation(summary = "查询 Paper 执行诊断只读聚合",
            description = "对 bounded Paper run 做规则化归因：诊断总览（无订单/有订单无成交/成交/成交亏损/风控拦截/"
                    + "数据不足/高回撤/异常终态/运行中计数）、主因分布、单 run 诊断（primaryCause/secondaryCauses/"
                    + "severity/causeConfidence/explanation/suggestedAction）与 strategyVersionId/publishId 维度聚合。"
                    + "只读，不触发任何状态机或外部调用，environment 固定 SIM/PAPER、LIVE 未开启，诊断结论不构成真实投资建议。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public PaperExecutionDiagnosticsResponse executionDiagnostics() {
        TraceIdContext.getOrCreate();
        return PaperExecutionDiagnosticsResponse.from(apiService.executionDiagnostics());
    }
}
