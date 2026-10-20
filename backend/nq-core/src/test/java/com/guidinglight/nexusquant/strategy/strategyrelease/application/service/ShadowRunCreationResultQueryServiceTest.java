package com.guidinglight.nexusquant.strategy.strategyrelease.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.exception.ShadowRunCreationEvidenceException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.exception.ShadowRunMaterializationAuthorizationException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunCreationPlan;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunMaterializationActor;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 原创建恢复的兼容、归属和损坏证据边界；这些单测不代替真实 PostgreSQL 与 HTTP 证明。 */
class ShadowRunCreationResultQueryServiceTest {

    private static final String COMMAND = "operator-action-001";
    private static final String KEY =
            "3fe547d9d02ae1eb38d9250e929685f31d9ed378613688beb7cd14d95b390f3a";
    private static final String PUBLISH = "publish-shadow-result";
    private static final UUID RUN = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private ShadowRun storedRun;
    private List<ShadowRunEvent> storedEvents = List.of();
    private final List<String> reads = new ArrayList<>();
    // 仅允许两项读取；任何意外写入或无界查询都立即失败，不为 core 模块新增测试依赖。
    private final ShadowRunFactRepository repository =
            (ShadowRunFactRepository)
                    Proxy.newProxyInstance(
                            ShadowRunFactRepository.class.getClassLoader(),
                            new Class<?>[] {ShadowRunFactRepository.class},
                            (proxy, method, arguments) ->
                                    switch (method.getName()) {
                                        case "findByIdempotencyKey" -> {
                                            reads.add("fact:" + arguments[0]);
                                            yield Optional.ofNullable(storedRun);
                                        }
                                        case "listCreationEvents" -> {
                                            reads.add("created:" + arguments[0]);
                                            yield storedEvents;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected repository call: "
                                                                + method.getName());
                                    });
    private final ShadowRunCreationResultQueryService service =
            new ShadowRunCreationResultQueryService(repository);
    private final ShadowRunMaterializationActor owner =
            new ShadowRunMaterializationActor(41L, List.of("OPERATOR"));

    @Test
    void preservesOriginalLengthPrefixedHashForAsciiAndUnicode() {
        // 固定向量由独立的 Python 长度前缀实现计算，防止创建和查询同时改错仍互相通过。
        assertEquals(KEY, ShadowRunCreationPlan.materializationIdempotencyKey(COMMAND));
        assertEquals(
                KEY, ShadowRunCreationPlan.materializationIdempotencyKey("  " + COMMAND + "  "));
        assertEquals(
                "c261569c848e42229ade4017c327dc8078b21a684ce1c2b4d202b0d773d346b0",
                ShadowRunCreationPlan.materializationIdempotencyKey("创建-😀"));
        assertEquals(
                "d22fdb00e7beb688b6d958f6f497f74406a356511b5be97124030e82813746dc",
                ShadowRunCreationPlan.materializationIdempotencyKey("a".repeat(128)));
        assertEquals(KEY, plan().bindMaterializationCommand(COMMAND).shadowRunIdempotencyKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "bad\nkey", "bad\u0000key", "\uD800", "\uDC00"})
    void rejectsInvalidIdentityWithoutAnyRead(String identity) {
        assertThrows(IllegalArgumentException.class, () -> service.query(PUBLISH, identity, owner));
        assertTrue(reads.isEmpty());
    }

    @Test
    void rejectsMissingOrOversizedIdentityAndPublishWithoutAnyRead() {
        assertThrows(IllegalArgumentException.class, () -> service.query(PUBLISH, null, owner));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.query(PUBLISH, "x".repeat(129), owner));
        assertThrows(IllegalArgumentException.class, () -> service.query(null, COMMAND, owner));
        assertThrows(IllegalArgumentException.class, () -> service.query(" ", COMMAND, owner));
        assertTrue(reads.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "ROLE_VIEWER", "operator", "UNKNOWN"})
    void requiresCurrentOperatorOrAdminRoleBeforeReading(String role) {
        assertThrows(
                ShadowRunMaterializationAuthorizationException.class,
                () ->
                        service.query(
                                PUBLISH,
                                COMMAND,
                                new ShadowRunMaterializationActor(41L, List.of(role))));
        assertTrue(reads.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ShadowRunStatus.class)
    void keepsOriginalCreationSeparateFromEveryCurrentLifecycleStatus(ShadowRunStatus status) {
        seed(run(status, KEY, KEY), event(metadata()));
        var result = service.query(PUBLISH, COMMAND, owner).orElseThrow();
        assertEquals(RUN, result.shadowRunId());
        assertEquals(ShadowRunStatus.CREATED, result.creationStatus());
        assertEquals(status, result.runStatus());
        assertEquals(NOW, result.createdAt());
        // 验证实际查询仅走两项读取，不生成新的事实、审计或状态写入。
        assertEquals(List.of("fact:" + KEY, "created:" + RUN), reads);
    }

    @Test
    void absentSnapshotAndWrongPublishDoNotReadEventsOrClaimFailure() {
        assertTrue(service.query(PUBLISH, COMMAND, owner).isEmpty());
        storedRun = run(ShadowRunStatus.CREATED, KEY, KEY);
        assertTrue(service.query("another-publish", COMMAND, owner).isEmpty());
        assertEquals(List.of("fact:" + KEY, "fact:" + KEY), reads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATOR", "ROLE_ADMIN", "ROLE_OPERATOR"})
    void permitsOriginalActorWithSupportedRoleButNeverAnotherActor(String role) {
        seed(run(ShadowRunStatus.CREATED, KEY, KEY), event(metadata()));
        assertTrue(
                service.query(
                                PUBLISH,
                                COMMAND,
                                new ShadowRunMaterializationActor(41L, List.of(role)))
                        .isPresent());
        assertFalse(
                service.query(
                                PUBLISH,
                                COMMAND,
                                new ShadowRunMaterializationActor(42L, List.of(role)))
                        .isPresent());
    }

    @Test
    void missingOrDuplicateCreationAuditStaysUnavailable() {
        storedRun = run(ShadowRunStatus.CREATED, KEY, KEY);
        storedEvents = List.of();
        assertThrows(
                ShadowRunCreationEvidenceException.class,
                () -> service.query(PUBLISH, COMMAND, owner));
        storedEvents = List.of(event(metadata()), event(metadata()));
        assertThrows(
                ShadowRunCreationEvidenceException.class,
                () -> service.query(PUBLISH, COMMAND, owner));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "publishRecordId",
                "releaseBindingMode",
                "idempotencyIdentity",
                "manifestSchemaVersion",
                "inputReference",
                "provenanceReference",
                "actorId",
                "materializationOnly",
                "shadowRunStarted",
                "tradingAuthorized"
            })
    void inconsistentOrWronglyTypedAuditFieldsStayUnavailable(String field) {
        ObjectNode metadata = metadata();
        metadata.put(field, "untrusted-value");
        seed(run(ShadowRunStatus.CREATED, KEY, KEY), event(metadata));
        assertThrows(
                ShadowRunCreationEvidenceException.class,
                () -> service.query(PUBLISH, COMMAND, owner));
    }

    @Test
    void mismatchedMainRequestAndKeyCannotBecomeConfirmedCreation() {
        seed(run(ShadowRunStatus.CREATED, "another-key", KEY), event(metadata()));
        assertThrows(
                ShadowRunCreationEvidenceException.class,
                () -> service.query(PUBLISH, COMMAND, owner));
        seed(run(ShadowRunStatus.CREATED, KEY, "another-request"), event(metadata()));
        assertThrows(
                ShadowRunCreationEvidenceException.class,
                () -> service.query(PUBLISH, COMMAND, owner));
    }

    @Test
    void auditForDifferentRunOrOriginalTimeCannotConfirmResult() {
        ShadowRunEvent original = event(metadata());
        seed(
                run(ShadowRunStatus.CREATED, KEY, KEY),
                new ShadowRunEvent(
                        original.id(),
                        UUID.randomUUID(),
                        original.eventType(),
                        original.fromStatus(),
                        original.toStatus(),
                        original.reasonCode(),
                        original.message(),
                        original.metadata(),
                        original.requestId(),
                        original.traceId(),
                        NOW));
        assertThrows(
                ShadowRunCreationEvidenceException.class,
                () -> service.query(PUBLISH, COMMAND, owner));
        seed(
                run(ShadowRunStatus.CREATED, KEY, KEY),
                new ShadowRunEvent(
                        original.id(),
                        RUN,
                        original.eventType(),
                        original.fromStatus(),
                        original.toStatus(),
                        original.reasonCode(),
                        original.message(),
                        original.metadata(),
                        original.requestId(),
                        original.traceId(),
                        NOW.plusSeconds(1)));
        assertThrows(
                ShadowRunCreationEvidenceException.class,
                () -> service.query(PUBLISH, COMMAND, owner));
    }

    private void seed(ShadowRun run, ShadowRunEvent event) {
        storedRun = run;
        storedEvents = List.of(event);
    }

    private static ObjectNode metadata() {
        return MAPPER.createObjectNode()
                .put("actorId", 41L)
                .put("publishRecordId", PUBLISH)
                .put("releaseBindingMode", "RELEASE_BOUND")
                .put("idempotencyIdentity", KEY)
                .put("manifestSchemaVersion", StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION)
                .put("inputReference", "input-reference")
                .put("provenanceReference", "provenance-reference")
                .put("materializationOnly", true)
                .put("shadowRunStarted", false)
                .put("tradingAuthorized", false);
    }

    private static ShadowRunEvent event(ObjectNode metadata) {
        return new ShadowRunEvent(
                UUID.randomUUID(),
                RUN,
                ShadowRunEventType.CREATED,
                null,
                ShadowRunStatus.CREATED,
                "RELEASE_TO_SHADOW_MATERIALIZED",
                "created",
                metadata,
                KEY,
                "result-query-trace",
                NOW);
    }

    private static ShadowRun run(ShadowRunStatus status, String key, String request) {
        ObjectNode policy =
                MAPPER.createObjectNode()
                        .put(
                                "manifestSchemaVersion",
                                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION)
                        .put("inputReference", "input-reference")
                        .put("provenanceReference", "provenance-reference")
                        .put("noOrderSubmission", true)
                        .put("noCredentialAccess", true)
                        .put("noPrivateEndpoint", true)
                        .put("noLedgerMutation", true)
                        .put("noAccountMutation", true)
                        .put("noExternalPrivateIo", true);
        return new ShadowRun(
                RUN,
                "strategy-version",
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "evaluation",
                PUBLISH,
                "a".repeat(64),
                null,
                status,
                NOW.minusSeconds(3600),
                NOW,
                policy,
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                request,
                key,
                "result-query-trace",
                MAPPER.createArrayNode(),
                MAPPER.createArrayNode(),
                MAPPER.createArrayNode(),
                0,
                NOW,
                NOW,
                null,
                null,
                null);
    }

    private static ShadowRunCreationPlan plan() {
        return new ShadowRunCreationPlan(
                PUBLISH,
                PUBLISH,
                "a".repeat(64),
                "strategy-version",
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "evaluation",
                NOW.minusSeconds(3600),
                NOW,
                "input-reference",
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, true, true),
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                "provenance-reference",
                "result-query-trace",
                "b".repeat(64));
    }
}
