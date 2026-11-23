package com.guidinglight.nexusquant.research.application.eval;

import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;

/** 只携带本次已写入的正式失败报告；恢复层不得改查其他轮次的最新报告。 */
public final class BacktestEvaluationFailedException extends IllegalStateException {
  private final BacktestEvaluationReport report;

  public BacktestEvaluationFailedException(
      BacktestEvaluationReport report, RuntimeException cause) {
    super("backtest evaluation failed: " + report.failureMessage(), cause);
    this.report = report;
  }

  public BacktestEvaluationReport report() {
    return report;
  }
}
