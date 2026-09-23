package com.guidinglight.nexusquant.research.application.paper.command;

import java.time.LocalDate;

public record PaperRunDailyReportGenerateCommand(
        String paperRunId,
        LocalDate reportDate
) {}
