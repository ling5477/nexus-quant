package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.paper.api.dto.PaperPortfolioSummaryResponse;
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
 * PaperPortfolioController —— Paper 组合看板只读聚合入口（GateJ 后产品化 Loop-13）。
 *
 * 跨多个 Paper run 聚合组合总览、策略/发布维度排行、Run 排行与数据质量提示，供前端组合看板消费。
 * 只读：不触发任何状态机、调度、回测、发布或外部调用；只覆盖 SIM/Paper，LIVE 未开启。
 */
@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/portfolio")
@Tag(name = "Paper Portfolio API", description = "GateJ 后 Paper 组合看板只读聚合接口。")
public class PaperPortfolioController {

    private final PaperTradingApiService apiService;

    public PaperPortfolioController(PaperTradingApiService apiService) {
        this.apiService = Objects.requireNonNull(apiService, "apiService must not be null");
    }

    @GetMapping("/summary")
    @Operation(summary = "查询 Paper 组合看板只读聚合",
            description = "跨多个 Paper run 聚合组合总资产/总 PnL/累计收益率/最大回撤、按 strategyVersionId 与 publishId 的收益排行、"
                    + "Run 排行（收益最高/回撤最大/风险最高/最近活跃/无交易/风控拦截）与数据质量提示。"
                    + "只读，不触发任何状态机或外部调用，environment 固定 SIM/PAPER、LIVE 未开启。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public PaperPortfolioSummaryResponse summary() {
        TraceIdContext.getOrCreate();
        return PaperPortfolioSummaryResponse.from(apiService.portfolioSummary());
    }
}
