package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PaperShadowConsistencyDrilldownFacts 是 GateS-2 SELECT-only repository 投影。
 *
 * <p>Why：application service 需要围绕单个 shadowRunId 组装 drilldown，但 core 不能依赖 JDBC。
 * 该 facts 只允许来自 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、
 * `shadow_consistency_reports`；不得携带 account、credential、order、ledger 或 private provider facts。
 */
public record PaperShadowConsistencyDrilldownFacts(
        Optional<ShadowRun> shadowRun,
        Optional<ShadowConsistencyReport> latestConsistency,
        SnapshotFacts snapshotFacts,
        long totalEvents,
        Optional<LatestEventFact> latestEvent,
        Optional<LatestSnapshotFact> latestSnapshot
) {
    public PaperShadowConsistencyDrilldownFacts {
        shadowRun = Objects.requireNonNull(shadowRun, "shadowRun must not be null");
        latestConsistency = Objects.requireNonNull(latestConsistency, "latestConsistency must not be null");
        Objects.requireNonNull(snapshotFacts, "snapshotFacts must not be null");
        latestEvent = Objects.requireNonNull(latestEvent, "latestEvent must not be null");
        latestSnapshot = Objects.requireNonNull(latestSnapshot, "latestSnapshot must not be null");
        if (totalEvents < 0) {
            throw new IllegalArgumentException("totalEvents must not be negative");
        }
    }

    public static PaperShadowConsistencyDrilldownFacts missingRun() {
        return new PaperShadowConsistencyDrilldownFacts(
                Optional.empty(),
                Optional.empty(),
                SnapshotFacts.empty(),
                0,
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * SnapshotFacts 只统计类型与时间，不暴露 snapshot payload。
     */
    public record SnapshotFacts(
            long totalSnapshots,
            long inputMarketdataSnapshots,
            long strategyDecisionSnapshots,
            long riskPreflightSnapshots,
            long orderIntentPreviewSnapshots,
            Instant latestSnapshotAt,
            List<String> latestSnapshotTypes
    ) {
        public SnapshotFacts {
            if (totalSnapshots < 0
                    || inputMarketdataSnapshots < 0
                    || strategyDecisionSnapshots < 0
                    || riskPreflightSnapshots < 0
                    || orderIntentPreviewSnapshots < 0) {
                throw new IllegalArgumentException("snapshot counts must not be negative");
            }
            latestSnapshotTypes = List.copyOf(Objects.requireNonNull(
                    latestSnapshotTypes,
                    "latestSnapshotTypes must not be null"
            ));
        }

        public static SnapshotFacts empty() {
            return new SnapshotFacts(0, 0, 0, 0, 0, null, List.of());
        }
    }

    /**
     * LatestEventFact 保存 latest event 摘要和 evidence anchor 所需字段。
     */
    public record LatestEventFact(
            String eventId,
            String eventType,
            String reasonCode,
            Instant createdAt
    ) {
    }

    /**
     * LatestSnapshotFact 保存 latest snapshot 摘要和 evidence anchor 所需字段，不包含 payload。
     */
    public record LatestSnapshotFact(
            String snapshotId,
            String snapshotType,
            String schemaVersion,
            Instant capturedAt,
            String checksum
    ) {
    }
}
