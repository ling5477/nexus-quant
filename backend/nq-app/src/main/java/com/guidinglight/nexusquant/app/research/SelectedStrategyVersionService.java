package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersion;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionStatus;
import java.time.Instant;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 只激活当前用户验证中冻结的最终选择；状态及成功审计共享同一租户事务。 */
@Service
public class SelectedStrategyVersionService {
    private final ExperimentValidationService validations;
    private final StrategyExperimentService experiments;
    private final StrategyVersionService versions;
    private final BacktestRunService runs;
    private final AuditLogRepository audit;
    private final ObjectReader reader;

    public SelectedStrategyVersionService(ExperimentValidationService validations,
            StrategyExperimentService experiments, StrategyVersionService versions,
            BacktestRunService runs, AuditLogRepository audit, ObjectMapper mapper) {
        this.validations = validations;
        this.experiments = experiments;
        this.versions = versions;
        this.runs = runs;
        this.audit = audit;
        reader = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Transactional(readOnly = true, timeout = 20)
    public View read(UUID validationId, long owner) {
        Selection selected = resolve(validationId, owner);
        return view(selected, selected.version());
    }

    @Transactional(timeout = 30)
    public View activate(UUID validationId, long owner) {
        Selection selected = resolve(validationId, owner);
        var result = versions.activateSelectedVersion(selected.version());
        if (result.changed()) {
            audit.append("STRATEGY_VERSION", "ACTIVATE_SELECTED_VERSION", Long.toString(owner),
                    TraceIdContext.getOrCreate(), Map.of(
                            "strategyVersionId", result.version().strategyVersionId(),
                            "validationId", validationId.toString(), "selectedTrialId", selected.trialId().toString(),
                            "previousStatus", "DRAFT", "newStatus", "ACTIVE", "actorId", owner,
                            "occurredAt", result.version().updatedAt().toString()));
        }
        return view(selected, result.version());
    }

    private Selection resolve(UUID id, long owner) {
        // 先按owner及现有RLS读取；其他用户/租户与不存在的验证均保持404。
        var validation = validations.get(id, owner);
        var source = experiments.get(validation.sourceExperimentId(), owner);
        require("SUCCEEDED".equals(validation.status()) && "SUCCEEDED".equals(source.status())
                && !source.cancelRequested(), "VALIDATION_NOT_ELIGIBLE_FOR_ACTIVATION");
        var training = validation.steps().stream().filter(s -> s.number() == 12).findFirst()
                .orElseThrow(() -> conflict("FINAL_SELECTION_UNAVAILABLE"));
        require("IN_SAMPLE".equals(training.role()) && training.startBar() == 0 && training.endBar() == 600
                && training.experimentId() != null && training.selectedAt() != null && training.selection() != null,
                "FINAL_SELECTION_NOT_FROZEN");
        JsonNode selected = training.selection().path("selected");
        var trial = source.trials().stream()
                .filter(t -> t.trialId().toString().equals(selected.path("trialId").asText()))
                .max(Comparator.comparingInt(StrategyExperimentService.Trial::attempt))
                .orElseThrow(() -> conflict("SELECTED_TRIAL_NOT_IN_SOURCE"));
        require(trial.ordinal() == selected.path("ordinal").asInt(-1)
                && trial.strategyVersionId().equals(selected.path("versionId").asText())
                && trial.comparable() && "SUCCEEDED".equals(trial.status())
                && "SUCCEEDED".equals(trial.runStatus()) && "SUCCEEDED".equals(trial.evaluationStatus())
                && trial.evaluationId() != null, "SELECTED_TRIAL_NOT_SUCCEEDED");
        var finalResult = validation.results().stream().filter(r -> r.step() == 12
                        && r.strategyVersionId().equals(trial.strategyVersionId())
                        && r.backtestRunId().equals(selected.path("runId").asText())
                        && r.evaluationId() != null && r.evaluationId().equals(selected.path("evaluationId").asText())
                        && r.evaluatedAt() != null && "SUCCEEDED".equals(r.status()))
                .findFirst().orElseThrow(() -> conflict("FINAL_TRAINING_NOT_SUCCEEDED"));
        JsonNode candidate = null;
        for (JsonNode c : validation.frozenPolicy().path("candidates")) {
            if (trial.trialId().toString().equals(c.path("sourceTrialId").asText())) candidate = c;
        }
        require(candidate != null && trial.strategyVersionId().equals(candidate.path("strategyVersionId").asText())
                && trial.parameters().equals(candidate.path("parameters")), "FROZEN_CANDIDATE_MISMATCH");
        StrategyVersion version = versions.findById(trial.strategyVersionId());
        require(version.checksum().matches("[a-f0-9]{64}")
                && read(version.paramSnapshotJson()).equals(trial.parameters())
                && read(version.configSnapshotJson()).equals(source.frozenInput().path("configSnapshot"))
                && read(version.sourceSnapshotJson()).equals(source.frozenInput().path("sourceSnapshot")),
                "SELECTED_VERSION_IDENTITY_MISMATCH");
        verifyRun(runs.getByBacktestRunId(trial.backtestRunId()), version);
        verifyRun(runs.getByBacktestRunId(finalResult.backtestRunId()), version);
        return new Selection(id, trial.trialId(), version, trial.backtestConfigId(), trial.backtestRunId());
    }

    private void verifyRun(BacktestRun run, StrategyVersion version) {
        JsonNode snapshot = read(run.strategyVersionSnapshotJson());
        // 生命周期状态不属于经济身份；历史Run中的DRAFT必须原样保留。
        require(run.status() == BacktestRunStatus.SUCCEEDED
                && version.strategyVersionId().equals(run.strategyVersionId())
                && version.strategyVersionId().equals(snapshot.path("strategyVersionId").asText())
                && version.strategyCode().equals(snapshot.path("strategyCode").asText())
                && version.version() == snapshot.path("version").asInt(-1)
                && version.checksum().equals(snapshot.path("checksum").asText())
                && read(version.paramSnapshotJson()).equals(child(snapshot, "paramSnapshot"))
                && read(version.paramSnapshotJson()).equals(read(run.paramSnapshotJson()))
                && read(version.configSnapshotJson()).equals(child(snapshot, "configSnapshot"))
                && read(version.sourceSnapshotJson()).equals(child(snapshot, "sourceSnapshot")),
                "SELECTED_RUN_STRATEGY_MISMATCH");
    }

    private JsonNode child(JsonNode snapshot, String field) {
        JsonNode value = snapshot.has(field) ? snapshot.path(field) : snapshot.path(field + "Json");
        return value.isTextual() ? read(value.asText()) : value;
    }

    private JsonNode read(String value) {
        try { return reader.readTree(value); }
        catch (IOException | IllegalArgumentException failure) { throw conflict("SELECTED_SNAPSHOT_INVALID"); }
    }

    private View view(Selection selected, StrategyVersion version) {
        return new View(selected.validationId(), selected.trialId(), version.strategyVersionId(),
                version.strategyCode(), version.version(), version.status().name(), version.checksum(),
                read(version.paramSnapshotJson()), selected.backtestConfigId(), selected.backtestRunId(),
                version.status() == StrategyVersionStatus.DRAFT, version.updatedAt());
    }

    private static void require(boolean valid, String reason) { if (!valid) throw conflict(reason); }
    private static ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private record Selection(UUID validationId, UUID trialId, StrategyVersion version, String backtestConfigId, String backtestRunId) {}
    public record View(UUID validationId, UUID selectedTrialId, String strategyVersionId, String strategyCode,
            int version, String status, String checksum, JsonNode parameters, String backtestConfigId,
            String backtestRunId, boolean eligible, Instant updatedAt) {}
}
