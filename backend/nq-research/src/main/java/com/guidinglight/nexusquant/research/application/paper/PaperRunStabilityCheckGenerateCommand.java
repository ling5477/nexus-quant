package com.guidinglight.nexusquant.research.application.paper;

import java.time.Instant;

public record PaperRunStabilityCheckGenerateCommand(
        String paperRunId,
        Instant checkWindowStart,
        Instant checkWindowEnd
) {}
