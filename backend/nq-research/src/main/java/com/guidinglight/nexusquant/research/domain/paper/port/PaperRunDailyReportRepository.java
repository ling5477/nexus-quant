package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaperRunDailyReportRepository {

    void upsert(PaperRunDailyReport report);

    Optional<PaperRunDailyReport> findById(String reportId);

    Optional<PaperRunDailyReport> findByRunIdAndDate(String paperRunId, LocalDate reportDate);

    List<PaperRunDailyReport> listByRunId(String paperRunId);
}
