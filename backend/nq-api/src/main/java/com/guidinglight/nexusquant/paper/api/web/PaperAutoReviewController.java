package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.paper.api.dto.PaperAutoReviewResponse;
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
 * PaperAutoReviewController —— Paper 规则化自动复盘只读聚合入口（GateK Batch K4）。
 *
 * 复用 K1 执行诊断与 K3 策略评估，规则化归纳组合 / 重点 run / 策略 / 发布复盘与问题聚类，供前端自动复盘视图消费。
 * 只读：不触发任何状态机、调度、回测、发布或外部调用；不接 AI / DH runtime；只覆盖 SIM/Paper，LIVE 未开启，
 * 复盘为规则化摘要、不构成真实投资建议。
 */
@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/auto-reviews")
@Tag(name = "Paper Auto Review API", description = "GateK Paper 规则化自动复盘只读聚合接口。")
public class PaperAutoReviewController {

    private final PaperTradingApiService apiService;

    public PaperAutoReviewController(PaperTradingApiService apiService) {
        this.apiService = Objects.requireNonNull(apiService, "apiService must not be null");
    }

    @GetMapping
    @Operation(summary = "查询 Paper 规则化自动复盘只读聚合",
            description = "复用执行诊断与策略评估，对 bounded Paper run 做规则化复盘：overview（总览计数与 topIssueCause/"
                    + "topWeakness/generatedAt）、portfolioReview（headline/summary/各 highlights/suggestedNextActions/"
                    + "limitations）、runReviews（重点 run：primaryCause/severity/confidence/reviewSummary/keyFacts/"
                    + "likelyReasons/suggestedActions/tags）、strategyReviews/publishReviews（ratingLabel/compositeScore/"
                    + "strengths/weaknesses/warnings/suggestedActions）、issueClusters（按问题类型聚类）与 safety。"
                    + "只读，不触发任何状态机或外部调用，不接 AI / DH runtime，environment 固定 SIM/Paper、LIVE 未开启，"
                    + "复盘为规则化摘要、不构成真实投资建议；无 run / 无策略时返回稳定空结构。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public PaperAutoReviewResponse autoReviews() {
        TraceIdContext.getOrCreate();
        return PaperAutoReviewResponse.from(apiService.autoReviews());
    }
}
