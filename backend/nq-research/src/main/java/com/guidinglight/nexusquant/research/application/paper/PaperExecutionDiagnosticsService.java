package com.guidinglight.nexusquant.research.application.paper;

import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * PaperExecutionDiagnosticsService —— Paper 执行诊断只读聚合编排（GateK Batch K1）。
 *
 * 职责：复用 {@link PaperPortfolioService#listRunRefs()} 的批量只读事实（与组合看板同一次批量读取、同口径单 run 派生），
 * 委托 {@link PaperExecutionDiagnosticsAssembler} 归纳为 {@link PaperExecutionDiagnostics}。
 *
 * 边界：
 * 1) 只读；不触发 start / stop / run-once / monitor / schedule / recovery / backtest / publish 等写动作。
 * 2) 不引入 per-run 查询放大：批量读取在 {@link PaperPortfolioService} 内已收敛为固定数量批量查询，本服务不再额外查询。
 * 3) 仅覆盖 SIM/Paper；诊断结论为 Paper 模拟执行事实的规则化归因，不构成真实投资建议。
 * 4) 无 run 时返回稳定空结构（assembler 对空输入返回空总览 / 空清单）。
 */
@Service
public class PaperExecutionDiagnosticsService {

    private final PaperPortfolioService portfolioService;

    public PaperExecutionDiagnosticsService(PaperPortfolioService portfolioService) {
        this.portfolioService = Objects.requireNonNull(portfolioService, "portfolioService must not be null");
    }

    /**
     * 聚合 bounded Paper run 的执行诊断只读结果：总览、主因分布、单 run 诊断、策略/发布维度聚合。
     * 只读，无 run 时返回稳定空结构，不触发任何状态机或外部调用。
     */
    public PaperExecutionDiagnostics diagnose() {
        return PaperExecutionDiagnosticsAssembler.assemble(portfolioService.listRunRefs());
    }
}
