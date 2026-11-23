package com.guidinglight.nexusquant.research.application.paper.service;

import com.guidinglight.nexusquant.research.application.paper.assembler.PaperAutoReviewAssembler;
import com.guidinglight.nexusquant.research.application.paper.assembler.PaperExecutionDiagnosticsAssembler;
import com.guidinglight.nexusquant.research.application.paper.model.PaperLegacyAnalysisSnapshot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** 历史Paper只读规则分析不合并独立SIM资金，不触发监控、调度或经济写入，也不外呼。 */
@Service
public class PaperLegacyAnalysisService {
    private final PaperPortfolioService portfolio;
    private final PaperStrategyEvaluationService evaluations;

    public PaperLegacyAnalysisService(
            PaperPortfolioService portfolio, PaperStrategyEvaluationService evaluations) {
        this.portfolio = Objects.requireNonNull(portfolio);
        this.evaluations = Objects.requireNonNull(evaluations);
    }

    /** 选择窗口、经济事实、诊断、评估与复盘使用同一RR事务，防止并发提交造成三个区域身份不一致。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public PaperLegacyAnalysisSnapshot read(long owner) {
        if (owner <= 0) throw new IllegalArgumentException("INVALID_LEGACY_ANALYSIS_OWNER");
        var refs = portfolio.readLegacyPortfolio(owner);
        var diagnostics = PaperExecutionDiagnosticsAssembler.assemble(refs.runs());
        var evaluation = evaluations.evaluateLegacy(owner, refs.runs());
        Instant generatedAt = Instant.now();
        var reviews = PaperAutoReviewAssembler.assemble(diagnostics, evaluation, generatedAt);
        return new PaperLegacyAnalysisSnapshot(
                "LEGACY_PAPER",
                200,
                refs.runs().size(),
                refs.hasMore(),
                generatedAt,
                refs.portfolio(),
                refs.runs(),
                diagnostics,
                evaluation,
                reviews);
    }
}
