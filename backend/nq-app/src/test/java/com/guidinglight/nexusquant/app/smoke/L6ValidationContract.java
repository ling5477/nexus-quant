package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel;
import java.util.HashSet;
import java.util.Set;

/** 执行完成与来源可用性分开判定；唯一例外绑定来源键及真实 no-file 实现标识。 */
final class L6ValidationContract {
    static final String GAP = "EVALUATION_ARTIFACT_PREVIEW";
    static final Set<String> SOURCES = Set.of("SHADOW_VALIDATION_WORKFLOW", "SHADOW_RUNS",
            "CONSISTENCY_EVIDENCE", "INCIDENT_REPLAY_REVIEW", GAP);
    private static final ObjectMapper JSON = new ObjectMapper();

    static ObjectNode evaluate(ValidationOperationsRuntimeEvidenceOverviewReadModel overview,
            long attempts, long completed, long failures) {
        if (failures != 0) throw new IllegalStateException("VALIDATION_SCHEDULER_EXECUTION_FAILED");
        if (attempts <= 0 || completed <= 0 || overview == null) {
            throw new IllegalStateException("VALIDATION_EXECUTION_NOT_COMPLETED");
        }
        var seen = new HashSet<String>();
        var unavailable = new HashSet<String>();
        ObjectNode result = JSON.createObjectNode();
        var sources = result.putArray("sources");
        for (var source : overview.sources()) {
            if (!seen.add(source.sourceKey()) || !SOURCES.contains(source.sourceKey())) reject();
            ReadModelEvidenceMetadata m = source.evidenceMetadata();
            if (source.sourceKey().equals(GAP)) {
                if (!m.source().equals("LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW")
                        || m.availability() != Availability.UNAVAILABLE
                        || m.freshnessStatus() != FreshnessStatus.UNKNOWN
                        || m.lastCalculatedAt() != null || m.ageSeconds() != null) reject();
                unavailable.add(source.sourceKey());
            } else if (m.availability() != Availability.AVAILABLE
                    || m.freshnessStatus() != FreshnessStatus.FRESH || m.lastCalculatedAt() == null) reject();
            sources.addObject().put("sourceKey", source.sourceKey()).put("source", m.source())
                    .put("availability", m.availability().name()).put("freshness", m.freshnessStatus().name());
        }
        if (!seen.equals(SOURCES) || !unavailable.equals(Set.of(GAP))
                || overview.evidenceMetadata().availability() != Availability.PARTIAL
                || overview.evidenceMetadata().freshnessStatus() != FreshnessStatus.UNKNOWN) reject();
        return result.put("executionStatus", "VALIDATION_EXECUTION_COMPLETED")
                .put("sourceAvailabilityStatus", "VALIDATION_AGGREGATE_DEGRADED_EXPECTED")
                .put("unexpectedDegradation", 0).put("attempts", attempts).put("completed", completed)
                .put("gapDisposition", "DEFER_UNTIL_TRIGGER / NOT_REQUIRED_FOR_L6")
                .put("gapTrigger", "Formal introduction of the file/artifact source");
    }

    private static void reject() { throw new IllegalStateException("VALIDATION_UNEXPECTED_DEGRADATION"); }
}
