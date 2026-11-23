package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.ResearchResultPage;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;

/** 有界读取复用正式评估事实及运行的费用来源范围，不重新计算评估或改变结果。 */
public interface BacktestEvaluationListQueryPort {
    ResearchResultPage<BacktestEvaluationReport> readPage(EvaluationListQuery query);
}
