package com.guidinglight.nexusquant.research.application.paper.command;

import java.time.Instant;

public record PaperRunStabilityCheckGenerateCommand(
        String paperRunId,
        Instant checkWindowStart,
        Instant checkWindowEnd
) {}
