package com.guidinglight.nexusquant.strategy.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewDivergenceSeverity;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewReadModel;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshotType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ShadowRunReadOnlyResponseTest {

    private static final UUID RUN_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID DATASET_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Instant NOW = Instant.parse("2026-07-06T13:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeReadOnlyDtosWithoutForbiddenSensitiveFields() throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(List.of(
                ShadowRunListResponse.from(new com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunListResult(
                        List.of(run()),
                        50,
                        0,
                        1
                )),
                ShadowRunDetailResponse.from(run()),
                ShadowRunEventResponse.from(event()),
                ShadowRunSnapshotResponse.from(snapshot()),
                ShadowConsistencyReportResponse.from(report()),
                ShadowRunOverviewResponse.from(overview())
        ));
        String normalized = body.toLowerCase(Locale.ROOT);

        for (String forbidden : List.of(
                "apikey",
                "secret",
                "passphrase",
                "token",
                "privatekey",
                "credentialmaterial",
                "decryptedpayload",
                "encryptedpayload",
                "rawsignature",
                "rawprivaterequest",
                "rawprivateresponse",
                "privateendpointpayload",
                "realorderid",
                "realaccountbalance",
                "realposition",
                "withdrawaddress",
                "transfertarget",
                "tradingready",
                "liveready",
                "authorizedfortrading",
                "tradeapproved",
                "orderexecutioncommand",
                "privateadapterreference"
        )) {
            assertFalse(normalized.contains(forbidden), "response must not contain " + forbidden + ": " + body);
        }
    }

    @Test
    void shouldRejectSensitiveMetadataPayloadAndReportJsonBeforeDtoMapping() {
        for (String forbiddenField : List.of("apiKey", "credentialMaterial", "realOrderId", "authorizedForTrading")) {
            ObjectNode forbiddenObject = JsonNodeFactory.instance.objectNode().put(forbiddenField, "redacted");

            assertThrows(IllegalArgumentException.class, () -> new ShadowRunEvent(
                    UUID.randomUUID(),
                    RUN_ID,
                    ShadowRunEventType.COMPLETED,
                    ShadowRunStatus.RUNNING,
                    ShadowRunStatus.COMPLETED,
                    "COMPLETED",
                    "local shadow run completed",
                    forbiddenObject,
                    "req-shadow",
                    "trace-shadow",
                    NOW
            ));
            assertThrows(IllegalArgumentException.class, () -> new ShadowRunSnapshot(
                    UUID.randomUUID(),
                    RUN_ID,
                    ShadowRunSnapshotType.ORDER_INTENT_PREVIEW,
                    1,
                    "LOCAL_CALLER_SUPPLIED_READONLY_INPUT",
                    "shadow-order-intent-preview.v1",
                    "sha256-demo",
                    forbiddenObject,
                    NOW,
                    "trace-shadow",
                    NOW
            ));
            assertThrows(IllegalArgumentException.class, () -> new ShadowConsistencyReport(
                    UUID.randomUUID(),
                    RUN_ID,
                    "paper-1",
                    ShadowConsistencyComparisonStatus.CONSISTENT,
                    forbiddenObject,
                    JsonNodeFactory.instance.arrayNode(),
                    JsonNodeFactory.instance.arrayNode(),
                    NOW,
                    "trace-shadow",
                    NOW
            ));
        }
    }

    private ShadowRun run() {
        return new ShadowRun(
                RUN_ID,
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "paper-1",
                ShadowRunStatus.COMPLETED,
                NOW.minusSeconds(3600),
                NOW,
                JsonNodeFactory.instance.objectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "req-shadow",
                "idem-shadow",
                "trace-shadow",
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode().add("read-only diagnostic"),
                JsonNodeFactory.instance.arrayNode().add("review replay"),
                2,
                NOW.minusSeconds(3600),
                NOW,
                NOW.minusSeconds(3500),
                null,
                NOW
        );
    }

    private ShadowRunEvent event() {
        return new ShadowRunEvent(
                UUID.randomUUID(),
                RUN_ID,
                ShadowRunEventType.COMPLETED,
                ShadowRunStatus.RUNNING,
                ShadowRunStatus.COMPLETED,
                "COMPLETED",
                "local shadow run completed",
                JsonNodeFactory.instance.objectNode().put("diagnosticOnly", true),
                "req-shadow",
                "trace-shadow",
                NOW
        );
    }

    private ShadowRunSnapshot snapshot() {
        return new ShadowRunSnapshot(
                UUID.randomUUID(),
                RUN_ID,
                ShadowRunSnapshotType.ORDER_INTENT_PREVIEW,
                1,
                "LOCAL_CALLER_SUPPLIED_READONLY_INPUT",
                "shadow-order-intent-preview.v1",
                "sha256-demo",
                JsonNodeFactory.instance.objectNode().put("previewOnly", true),
                NOW,
                "trace-shadow",
                NOW
        );
    }

    private ShadowConsistencyReport report() {
        return new ShadowConsistencyReport(
                UUID.randomUUID(),
                RUN_ID,
                "paper-1",
                ShadowConsistencyComparisonStatus.CONSISTENT,
                JsonNodeFactory.instance.objectNode().put("schemaVersion", "shadow-consistency-report.v1"),
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode().add("diagnostic only"),
                NOW,
                "trace-shadow",
                NOW
        );
    }

    private ShadowRunOverviewReadModel overview() {
        return new ShadowRunOverviewReadModel(
                NOW,
                testEvidenceMetadata("LOCAL_DB_SHADOW_FACTS"),
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                1,
                0,
                0,
                0,
                1,
                0,
                new ShadowRunOverviewReadModel.LatestRun(
                        RUN_ID,
                        "sv-1",
                        DATASET_ID,
                        "paper-1",
                        "COMPLETED",
                        "DIAGNOSTIC_ONLY",
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        NOW.minusSeconds(3600),
                        NOW,
                        NOW.minusSeconds(3500),
                        NOW
                ),
                new ShadowRunOverviewReadModel.LatestConsistency(
                        UUID.randomUUID(),
                        RUN_ID,
                        "paper-1",
                        "CONSISTENT",
                        JsonNodeFactory.instance.objectNode().put("schemaVersion", "shadow-consistency-report.v1"),
                        JsonNodeFactory.instance.arrayNode(),
                        JsonNodeFactory.instance.arrayNode().add("diagnostic only"),
                        NOW,
                        "trace-shadow"
                ),
                ShadowRunOverviewDivergenceSeverity.NONE,
                List.of(new ShadowRunOverviewReadModel.BoundaryMessage("LIVE_DISABLED", "CRITICAL", "LIVE disabled", "SYSTEM_BOUNDARY", null)),
                List.of(new ShadowRunOverviewReadModel.BoundaryMessage("SHADOW_RUN_DIAGNOSTIC_ONLY", "INFO", "diagnostic only", "SYSTEM_BOUNDARY", null)),
                List.of(new ShadowRunOverviewReadModel.NextStep(
                        "REVIEW_SHADOW_OVERVIEW",
                        "backend",
                        "review_shadow_overview",
                        "overview reviewed",
                        true
                )),
                List.of(new ShadowRunOverviewReadModel.EvidenceAnchor(
                        "SHADOW_RUN",
                        RUN_ID.toString(),
                        "2",
                        NOW,
                        null
                )),
                "trace-shadow"
        );
    }

    private ReadModelEvidenceMetadata testEvidenceMetadata(String source) {
        return new ReadModelEvidenceMetadata(
                source,
                ReadModelEvidenceMetadata.Availability.AVAILABLE,
                NOW,
                ReadModelEvidenceMetadata.FreshnessStatus.UNKNOWN,
                0L,
                null,
                "STALE_THRESHOLD_NOT_DEFINED",
                true,
                true,
                true,
                true
        );
    }
}
