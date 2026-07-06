package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshotType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateMachine;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatusUpdateResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * GateR-3 Shadow Run runner skeleton。
 *
 * <p>职责：把调用方提供的本地只读 payload 组装为 Shadow Run 主事实、状态事件和四类快照。
 * Why：GateR-3 需要一个可调用、可测试、可复盘的 runner skeleton，但仍禁止真实策略执行、
 * marketdata 外联、private endpoint、credential 读取、下单、账户/ledger mutation 和后台调度。
 *
 * <p>幂等/事务：幂等由 {@code idempotencyKey} 绑定到 {@code shadow_runs.idempotency_key}；
 * 已存在的 run 直接作为幂等复用结果返回，不会把终态强行推回 {@code RUNNING}。runner 不把整段
 * run 包成一个 Spring 外层事务，避免运行期异常重新抛出时把已写入的 {@code FAILED} 状态和失败事件
 * 一并回滚；合法状态推进先经过 {@link ShadowRunStateMachine} 校验，再交给 repository 持久化，确保
 * 应用层和 repository 层共用同一状态机语义。
 */
@Service
public class ShadowRunRunnerService implements ShadowRunRunner {

    private static final String POLICY_VERSION = "gate-r-3-shadow-runner-skeleton.v1";
    private static final String SNAPSHOT_SOURCE = "LOCAL_CALLER_SUPPLIED_READONLY_INPUT";
    private static final String SNAPSHOT_SCHEMA_VERSION = "shadow-runner-snapshot.v1";

    private final ShadowRunFactRepository repository;
    private final ObjectMapper objectMapper;
    private final ShadowRunStateMachine stateMachine;
    private final Clock clock;

    /**
     * 生产构造器。
     *
     * @param repository   Shadow Run 本地事实 port；实现不得触发交易所或真实账户副作用
     * @param objectMapper JSON 序列化工具；只用于本地 JSONB payload / metadata 构造和 checksum
     */
    @Autowired
    public ShadowRunRunnerService(ShadowRunFactRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, new ShadowRunStateMachine(), Clock.systemUTC());
    }

    ShadowRunRunnerService(
            ShadowRunFactRepository repository,
            ObjectMapper objectMapper,
            ShadowRunStateMachine stateMachine,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 执行一次本地 Shadow Run skeleton。
     *
     * <p>参数语义：command 中四类 payload 均由调用方提供，本方法不会主动访问行情 provider、
     * strategy engine、risk engine、credential store、private endpoint 或 order gateway。
     *
     * <p>副作用边界：允许的副作用仅限写入 Shadow Run 本地 fact / audit / snapshot 表；所有
     * no-side-effect flags 固定为 true，ORDER_INTENT_PREVIEW 只作为 preview payload 保存。
     *
     * @param command 本地只读输入
     * @return 本地 runner 结果；不表示交易授权或 LIVE readiness
     */
    @Override
    public ShadowRunRunnerResult run(ShadowRunRunnerCommand command) {
        validateCommand(command);
        Optional<ShadowRun> existing = repository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return resultFromExisting(existing.get(), List.of(ShadowRunRunnerStep.IDEMPOTENT_REPLAY));
        }

        RunCursor cursor = null;
        List<ShadowRunRunnerStep> steps = new ArrayList<>();
        try {
            ShadowRun requested = newRun(command);
            ShadowRun created = repository.create(requested);
            if (!created.id().equals(requested.id())) {
                return resultFromExisting(created, List.of(ShadowRunRunnerStep.IDEMPOTENT_REPLAY));
            }
            cursor = new RunCursor(created.id(), created.status(), created.version());
            steps.add(ShadowRunRunnerStep.CREATE_RUN);
            appendCreatedEvent(cursor.id(), command);

            cursor = transition(cursor, ShadowRunStatus.PRECHECKING, "SHADOW_RUN_PRECHECK_STARTED",
                    "No-side-effect precheck started.", command);
            steps.add(ShadowRunRunnerStep.PRECHECKING);

            enforceNoSideEffectPolicy(created);
            steps.add(ShadowRunRunnerStep.NO_SIDE_EFFECT_GUARD);

            cursor = transition(cursor, ShadowRunStatus.READY, "SHADOW_RUN_PRECHECK_PASSED",
                    "No-side-effect policy is satisfied.", command);
            steps.add(ShadowRunRunnerStep.READY);

            appendSnapshot(cursor.id(), ShadowRunSnapshotType.INPUT_MARKETDATA, command.inputMarketdataPayload());
            steps.add(ShadowRunRunnerStep.INPUT_MARKETDATA_SNAPSHOT);

            cursor = transition(cursor, ShadowRunStatus.RUNNING, "SHADOW_RUN_STARTED",
                    "Local skeleton runner started without external IO.", command);
            steps.add(ShadowRunRunnerStep.RUNNING);

            appendSnapshot(cursor.id(), ShadowRunSnapshotType.STRATEGY_DECISION, command.strategyDecisionPayload());
            steps.add(ShadowRunRunnerStep.STRATEGY_DECISION_SNAPSHOT);
            appendSnapshot(cursor.id(), ShadowRunSnapshotType.RISK_PREFLIGHT, command.riskPreflightPayload());
            steps.add(ShadowRunRunnerStep.RISK_PREFLIGHT_SNAPSHOT);
            appendSnapshot(cursor.id(), ShadowRunSnapshotType.ORDER_INTENT_PREVIEW, command.orderIntentPreviewPayload());
            steps.add(ShadowRunRunnerStep.ORDER_INTENT_PREVIEW_SNAPSHOT);

            if (!command.blockers().isEmpty()) {
                cursor = transition(cursor, ShadowRunStatus.BLOCKED, command.blockers().getFirst().code(),
                        "Shadow Run skeleton blocked by caller-supplied local blocker.", command);
                steps.add(ShadowRunRunnerStep.BLOCKED);
                return resultFromCursor(cursor, steps, command.blockers(), null, null);
            }

            cursor = transition(cursor, ShadowRunStatus.COMPLETED, "SHADOW_RUN_COMPLETED",
                    "Local no-side-effect Shadow Run skeleton completed.", command);
            steps.add(ShadowRunRunnerStep.COMPLETED);
            return resultFromCursor(cursor, steps, List.of(), null, null);
        } catch (RuntimeException ex) {
            ShadowRunRunnerResult failureResult = failIfPossible(cursor, steps, command, ex);
            throw new ShadowRunRunnerException("Shadow Run runner skeleton failed", ex, failureResult);
        }
    }

    private void validateCommand(ShadowRunRunnerCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.strategyVersionId(), "strategyVersionId");
        Objects.requireNonNull(command.datasetId(), "datasetId must not be null");
        Objects.requireNonNull(command.windowStart(), "windowStart must not be null");
        Objects.requireNonNull(command.windowEnd(), "windowEnd must not be null");
        if (command.windowStart().isAfter(command.windowEnd())) {
            throw new IllegalArgumentException("windowStart must not be after windowEnd");
        }
        requireText(command.requestId(), "requestId");
        requireText(command.idempotencyKey(), "idempotencyKey");
        requireText(command.traceId(), "traceId");
        validatePayload(ShadowRunSnapshotType.INPUT_MARKETDATA, command.inputMarketdataPayload());
        validatePayload(ShadowRunSnapshotType.STRATEGY_DECISION, command.strategyDecisionPayload());
        validatePayload(ShadowRunSnapshotType.RISK_PREFLIGHT, command.riskPreflightPayload());
        validatePayload(ShadowRunSnapshotType.ORDER_INTENT_PREVIEW, command.orderIntentPreviewPayload());
    }

    private void validatePayload(ShadowRunSnapshotType snapshotType, JsonNode payload) {
        Objects.requireNonNull(payload, snapshotType + " payload must not be null");
        ShadowRunSensitiveDataGuard.validateJson(snapshotType.name(), payload);
        if (!payload.isObject()) {
            throw new IllegalArgumentException(snapshotType + " payload must be a JSON object");
        }
    }

    private ShadowRun newRun(ShadowRunRunnerCommand command) {
        Instant now = Instant.now(clock);
        return new ShadowRun(
                UUID.randomUUID(),
                command.strategyVersionId().trim(),
                command.datasetId(),
                blankToNull(command.evaluationId()),
                blankToNull(command.publishId()),
                blankToNull(command.paperRunId()),
                ShadowRunStatus.CREATED,
                command.windowStart(),
                command.windowEnd(),
                sideEffectPolicy(),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                command.requestId().trim(),
                command.idempotencyKey().trim(),
                command.traceId().trim(),
                issuesArray(command.blockers()),
                baseWarnings(),
                nextSteps(command.blockers()),
                0,
                now,
                now,
                null,
                null,
                null
        );
    }

    private void enforceNoSideEffectPolicy(ShadowRun run) {
        if (!run.noOrderSubmission()
                || !run.noCredentialAccess()
                || !run.noPrivateEndpoint()
                || !run.noLedgerMutation()
                || !run.noAccountMutation()
                || !run.noExternalPrivateIo()) {
            throw new IllegalStateException("Shadow Run runner side-effect policy is not fully enforced");
        }
    }

    private RunCursor transition(
            RunCursor current,
            ShadowRunStatus toStatus,
            String reasonCode,
            String message,
            ShadowRunRunnerCommand command
    ) {
        stateMachine.transition(current.status(), toStatus);
        ShadowRunStatusUpdateResult result = repository.updateStatus(
                current.id(),
                toStatus,
                current.version(),
                reasonCode,
                message,
                command.requestId(),
                command.traceId()
        );
        return new RunCursor(result.shadowRunId(), result.toStatus(), result.newVersion());
    }

    private void appendSnapshot(UUID shadowRunId, ShadowRunSnapshotType snapshotType, JsonNode payload) {
        int sequenceNo = 0;
        ShadowRun run = repository.findById(shadowRunId)
                .orElseThrow(() -> new IllegalStateException("shadow run not found for snapshot trace: " + shadowRunId));
        ShadowRunSnapshot snapshot = new ShadowRunSnapshot(
                UUID.randomUUID(),
                shadowRunId,
                snapshotType,
                sequenceNo,
                SNAPSHOT_SOURCE,
                SNAPSHOT_SCHEMA_VERSION,
                checksum(payload),
                payload,
                Instant.now(clock),
                run.traceId(),
                Instant.now(clock)
        );
        repository.appendSnapshot(snapshot);
        appendSnapshotEvent(run, snapshotType, sequenceNo);
    }

    private void appendSnapshotEvent(
            ShadowRun run,
            ShadowRunSnapshotType snapshotType,
            int sequenceNo
    ) {
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("snapshotType", snapshotType.name())
                .put("sequenceNo", sequenceNo)
                .put("source", SNAPSHOT_SOURCE)
                .put("orderIntentPreviewOnly", snapshotType == ShadowRunSnapshotType.ORDER_INTENT_PREVIEW);
        repository.appendEvent(new ShadowRunEvent(
                UUID.randomUUID(),
                run.id(),
                ShadowRunEventType.SNAPSHOT_CAPTURED,
                null,
                null,
                "SNAPSHOT_CAPTURED",
                snapshotType + " snapshot captured by local no-side-effect runner skeleton.",
                metadata,
                run.requestId(),
                run.traceId(),
                Instant.now(clock)
        ));
    }

    private void appendCreatedEvent(UUID shadowRunId, ShadowRunRunnerCommand command) {
        repository.appendEvent(new ShadowRunEvent(
                UUID.randomUUID(),
                shadowRunId,
                ShadowRunEventType.CREATED,
                null,
                ShadowRunStatus.CREATED,
                "SHADOW_RUN_CREATED",
                "Local no-side-effect Shadow Run fact created.",
                objectMapper.createObjectNode().put("runnerSkeleton", true),
                command.requestId(),
                command.traceId(),
                Instant.now(clock)
        ));
    }

    private ShadowRunRunnerResult failIfPossible(
            RunCursor cursor,
            List<ShadowRunRunnerStep> steps,
            ShadowRunRunnerCommand command,
            RuntimeException cause
    ) {
        if (cursor == null || cursor.status().terminal()) {
            return null;
        }
        try {
            RunCursor failed = transition(cursor, ShadowRunStatus.FAILED, "SHADOW_RUN_RUNNER_EXCEPTION",
                    "Shadow Run runner skeleton failed; see application exception.", command);
            List<ShadowRunRunnerStep> failedSteps = new ArrayList<>(steps);
            failedSteps.add(ShadowRunRunnerStep.FAILED);
            return resultFromCursor(
                    failed,
                    failedSteps,
                    command.blockers(),
                    "SHADOW_RUN_RUNNER_EXCEPTION",
                    cause.getClass().getSimpleName()
            );
        } catch (RuntimeException failEx) {
            cause.addSuppressed(failEx);
            return null;
        }
    }

    private ShadowRunRunnerResult resultFromExisting(ShadowRun run, List<ShadowRunRunnerStep> steps) {
        return result(run, true, steps, issuesFromJson(run.blockers()), null, null);
    }

    private ShadowRunRunnerResult resultFromCursor(
            RunCursor cursor,
            List<ShadowRunRunnerStep> steps,
            List<ShadowRunRunnerIssue> blockers,
            String failureCode,
            String failureMessage
    ) {
        ShadowRun run = repository.findById(cursor.id())
                .orElseThrow(() -> new IllegalStateException("shadow run not found after runner update: " + cursor.id()));
        return result(run, false, steps, blockers, failureCode, failureMessage);
    }

    private ShadowRunRunnerResult result(
            ShadowRun run,
            boolean idempotentReplay,
            List<ShadowRunRunnerStep> steps,
            List<ShadowRunRunnerIssue> blockers,
            String failureCode,
            String failureMessage
    ) {
        return new ShadowRunRunnerResult(
                run.id(),
                run.status(),
                idempotentReplay,
                run.idempotencyKey(),
                run.requestId(),
                run.traceId(),
                run.noOrderSubmission(),
                run.noCredentialAccess(),
                run.noPrivateEndpoint(),
                run.noLedgerMutation(),
                run.noAccountMutation(),
                run.noExternalPrivateIo(),
                true,
                repository.listEvents(run.id()).size(),
                repository.listSnapshots(run.id()).size(),
                steps,
                blockers,
                failureCode,
                failureMessage,
                Instant.now(clock)
        );
    }

    private ObjectNode sideEffectPolicy() {
        return objectMapper.createObjectNode()
                .put("policyVersion", POLICY_VERSION)
                .put("no_order_submission", true)
                .put("no_credential_access", true)
                .put("no_private_endpoint", true)
                .put("no_ledger_mutation", true)
                .put("no_account_mutation", true)
                .put("no_external_private_io", true)
                .put("order_intent_preview_mode", "PREVIEW_ONLY")
                .put("authorization_boundary", "DIAGNOSTIC_ONLY");
    }

    private ArrayNode issuesArray(List<ShadowRunRunnerIssue> issues) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ShadowRunRunnerIssue issue : issues) {
            array.add(objectMapper.createObjectNode()
                    .put("code", issue.code())
                    .put("message", issue.message()));
        }
        return array;
    }

    private ArrayNode baseWarnings() {
        ArrayNode array = objectMapper.createArrayNode();
        array.add(objectMapper.createObjectNode()
                .put("code", "SHADOW_RUNNER_SKELETON_ONLY")
                .put("message", "Runner skeleton only writes local facts and does not start background execution."));
        array.add(objectMapper.createObjectNode()
                .put("code", "ORDER_INTENT_PREVIEW_NOT_ORDER")
                .put("message", "ORDER_INTENT_PREVIEW is preview-only and must not be treated as a real order."));
        return array;
    }

    private ArrayNode nextSteps(List<ShadowRunRunnerIssue> blockers) {
        ArrayNode array = objectMapper.createArrayNode();
        if (blockers.isEmpty()) {
            array.add("Review completed Shadow Run local facts before any later consistency report.");
            return array;
        }
        array.add("Resolve caller-supplied local blockers before retrying with a new Shadow Run.");
        return array;
    }

    private List<ShadowRunRunnerIssue> issuesFromJson(JsonNode blockers) {
        if (blockers == null || !blockers.isArray()) {
            return List.of();
        }
        List<ShadowRunRunnerIssue> issues = new ArrayList<>();
        for (JsonNode item : blockers) {
            String code = item.path("code").asText(null);
            String message = item.path("message").asText(null);
            if (code != null && message != null) {
                issues.add(new ShadowRunRunnerIssue(code, message));
            }
        }
        return issues;
    }

    private String checksum(JsonNode payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("snapshot payload must be serializable JSON", ex);
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RunCursor(UUID id, ShadowRunStatus status, long version) {
    }
}
