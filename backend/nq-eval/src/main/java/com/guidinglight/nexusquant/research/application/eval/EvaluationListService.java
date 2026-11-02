package com.guidinglight.nexusquant.research.application.eval;

import com.guidinglight.nexusquant.research.domain.ResearchResultPage;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.domain.eval.port.BacktestEvaluationListQueryPort;
import com.guidinglight.nexusquant.research.domain.eval.port.EvaluationListQuery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 只读结果查询使用同一快照，不能让并发重新评估或私有范围变化拆开总数与页记录。 */
@Service
public class EvaluationListService {
    private final BacktestEvaluationListQueryPort queryPort;

    public EvaluationListService(BacktestEvaluationListQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public ResearchResultPage<BacktestEvaluationReport> list(EvaluationListQuery query) {
        return queryPort.readPage(query);
    }
}
