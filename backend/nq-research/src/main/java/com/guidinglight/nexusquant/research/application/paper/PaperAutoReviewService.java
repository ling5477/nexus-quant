package com.guidinglight.nexusquant.research.application.paper;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * PaperAutoReviewService —— Paper 规则化自动复盘只读聚合编排（GateK Batch K4）。
 *
 * 职责：复用 K1 {@link PaperExecutionDiagnosticsService#diagnose()} 与 K3
 * {@link PaperStrategyEvaluationService#evaluate()} 的只读派生结果（二者各自复用组合看板同一次批量读取），
 * 注入复盘生成时间后委托 {@link PaperAutoReviewAssembler} 归纳为 {@link PaperAutoReview}。
 *
 * 边界：
 * 1) 只读；不触发 start / stop / run-once / monitor / schedule / recovery / backtest / publish 等写动作。
 * 2) 不引入 per-run 查询放大：本服务不直接查仓储，只消费 K1/K3 已收敛的批量只读结果（各自固定数量批量查询）。
 * 3) 不接 AI / LLM / DH runtime、不外呼、不读凭证；复盘为确定性规则化摘要，仅覆盖 SIM/Paper，不构成真实投资建议。
 * 4) 无 run / 无策略时返回稳定空结构（assembler 对空输入返回空复盘），不抛异常。
 */
@Service
public class PaperAutoReviewService {

    private final PaperExecutionDiagnosticsService executionDiagnosticsService;
    private final PaperStrategyEvaluationService strategyEvaluationService;

    public PaperAutoReviewService(
            PaperExecutionDiagnosticsService executionDiagnosticsService,
            PaperStrategyEvaluationService strategyEvaluationService
    ) {
        this.executionDiagnosticsService = Objects.requireNonNull(
                executionDiagnosticsService, "executionDiagnosticsService must not be null");
        this.strategyEvaluationService = Objects.requireNonNull(
                strategyEvaluationService, "strategyEvaluationService must not be null");
    }

    /**
     * 聚合 bounded Paper run 的规则化自动复盘：组合复盘、重点 run 复盘、策略/发布复盘与问题聚类。
     * 只读，无 run / 无策略时返回稳定空结构，不触发任何状态机或外部调用。
     */
    public PaperAutoReview review() {
        PaperExecutionDiagnostics diagnostics = executionDiagnosticsService.diagnose();
        PaperStrategyEvaluation evaluation = strategyEvaluationService.evaluate();
        return PaperAutoReviewAssembler.assemble(diagnostics, evaluation, Instant.now());
    }
}
