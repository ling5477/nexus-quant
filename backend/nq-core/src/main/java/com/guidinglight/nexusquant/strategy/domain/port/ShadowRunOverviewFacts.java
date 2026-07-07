package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;

import java.util.Objects;
import java.util.Optional;

/**
 * ShadowRunOverviewFacts 是 GateS-1 read model 的 SELECT-only repository 投影。
 *
 * <p>Why: core service 需要基于本地 Shadow Run facts 计算 overview、boundary flags 和
 * divergence severity。该投影只从 `shadow_runs`、`shadow_run_events`、
 * `shadow_run_snapshots`、`shadow_consistency_reports` 读取，不承载任何写侧命令或交易授权语义。
 */
public record ShadowRunOverviewFacts(
        long totalRuns,
        long runningRuns,
        long blockedRuns,
        long failedRuns,
        long completedRuns,
        long staleRuns,
        Optional<ShadowRun> latestRun,
        Optional<ShadowConsistencyReport> latestConsistency,
        Optional<ShadowRunOverviewEvidenceFact> latestEvent,
        Optional<ShadowRunOverviewEvidenceFact> latestSnapshot
) {
    public ShadowRunOverviewFacts {
        if (totalRuns < 0 || runningRuns < 0 || blockedRuns < 0 || failedRuns < 0 || completedRuns < 0 || staleRuns < 0) {
            throw new IllegalArgumentException("overview counts must not be negative");
        }
        latestRun = Objects.requireNonNull(latestRun, "latestRun must not be null");
        latestConsistency = Objects.requireNonNull(latestConsistency, "latestConsistency must not be null");
        latestEvent = Objects.requireNonNull(latestEvent, "latestEvent must not be null");
        latestSnapshot = Objects.requireNonNull(latestSnapshot, "latestSnapshot must not be null");
    }

    public static ShadowRunOverviewFacts empty() {
        return new ShadowRunOverviewFacts(
                0,
                0,
                0,
                0,
                0,
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
