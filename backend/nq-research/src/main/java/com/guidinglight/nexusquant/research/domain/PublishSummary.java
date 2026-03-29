package com.guidinglight.nexusquant.research.domain;

import java.time.Instant;

/**
 * PublishSummary 表示 run detail / run list 使用的最小发布摘要。
 */
public record PublishSummary(
        PublishStatus publishStatus,
        Instant publishedAt,
        String targetStrategyDefinitionId,
        String publishName,
        String sourceBacktestRunId
) {
}

