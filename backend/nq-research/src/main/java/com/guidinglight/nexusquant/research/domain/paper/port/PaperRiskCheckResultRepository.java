package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;

import java.util.List;

public interface PaperRiskCheckResultRepository {

    void insert(PaperRiskCheckResult result);

    List<PaperRiskCheckResult> listByRunId(String paperRunId);
}
