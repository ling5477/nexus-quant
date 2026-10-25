package com.guidinglight.nexusquant.paper.api.dto;

import java.time.LocalDate;

public record PaperRunDailyReportGenerateRequestBody(
        LocalDate reportDate
) {}
