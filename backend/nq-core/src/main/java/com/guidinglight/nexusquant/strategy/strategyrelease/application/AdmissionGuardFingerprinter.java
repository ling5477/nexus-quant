package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewFacts.ConsistencyEvidenceIdentity;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewFacts.PaperEvidenceIdentity;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewFacts.ShadowEvidenceIdentity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** {@code strategy-release-admission-guard.v1} fixed-order canonical binary encoder。 */
@Component
public final class AdmissionGuardFingerprinter {

    private static final byte TYPE_UTF8 = 1;
    private static final byte TYPE_INTEGER = 2;
    private static final byte TYPE_LONG = 3;
    private static final byte TYPE_INSTANT = 4;
    private static final byte TYPE_BOOLEAN = 5;
    private static final byte TYPE_STRUCTURE = 6;

    public String fingerprint(
            StrategyReleaseAdmissionState state,
            StrategyReleaseAdmissionPreviewFacts facts,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(facts, "facts must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        LatestDecisionFact validation = facts.validationFact();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeString(output, "fingerprintSchema", AdmissionGuard.FINGERPRINT_SCHEMA_VERSION);
                writeInteger(output, "guardSchemaVersion", state.guardSchemaVersion());
                writeString(output, "publishRecordId", state.publishRecordId());
                writeLong(output, "admissionRevision", state.admissionRevision());
                writeString(output, "releaseArtifactDigest", state.releaseArtifactDigest());
                writeString(output, "manifestFingerprint", state.manifestFingerprint());
                writeString(output, "manifestSchemaVersion", state.manifestSchemaVersion());
                writeString(output, "backtestRunId", facts.backtestRunId());
                writeString(output, "strategyVersionId", validation == null ? null : validation.strategyVersionId());
                writeUuid(output, "datasetId", validation == null ? null : validation.datasetId());
                writeString(output, "evaluationId", validation == null ? null : validation.evaluationReportId());
                writeInstant(output, "windowStart", facts.windowStart());
                writeInstant(output, "windowEnd", facts.windowEnd());
                writeEnum(output, "strategyVersionStatus", validation == null ? null : validation.strategyVersionStatus());
                writeEnum(output, "evaluationStatus", validation == null ? null : validation.evaluationStatus());
                writeEnum(output, "publishStatus", validation == null ? null : validation.publishStatus());
                writePaper(output, facts.latestPaperIdentity());
                writeShadow(output, facts.latestShadowEvidenceIdentity());
                writeConsistency(output, facts.latestConsistencyIdentity());
                writeEnum(output, "authorizationBoundary", facts.authorizationBoundary() == null
                        ? null
                        : facts.authorizationBoundary().name());
                writeString(output, "sideEffectPolicyVersion", AdmissionGuard.SIDE_EFFECT_POLICY_VERSION);
                writePolicy(output, facts.sideEffectPolicy());
                writeInstant(output, "evaluatedAt", evaluatedAt);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("canonical admission guard encoding failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public AdmissionGuard issue(
            StrategyReleaseAdmissionState state,
            StrategyReleaseAdmissionPreviewFacts facts,
            Instant evaluatedAt
    ) {
        LatestDecisionFact validation = Objects.requireNonNull(
                facts.validationFact(),
                "eligible admission facts must contain validation identity"
        );
        return new AdmissionGuard(
                state.guardSchemaVersion(),
                state.publishRecordId(),
                state.admissionRevision(),
                state.releaseArtifactDigest(),
                state.manifestFingerprint(),
                state.manifestSchemaVersion(),
                facts.backtestRunId(),
                validation.strategyVersionId(),
                validation.datasetId(),
                validation.evaluationReportId(),
                facts.windowStart(),
                facts.windowEnd(),
                validation.strategyVersionStatus(),
                validation.evaluationStatus(),
                validation.publishStatus(),
                facts.latestPaperIdentity(),
                facts.latestShadowEvidenceIdentity(),
                facts.latestConsistencyIdentity(),
                facts.authorizationBoundary(),
                AdmissionGuard.SIDE_EFFECT_POLICY_VERSION,
                facts.sideEffectPolicy(),
                fingerprint(state, facts, evaluatedAt),
                evaluatedAt
        );
    }

    private void writePaper(DataOutputStream output, PaperEvidenceIdentity identity) throws IOException {
        writeStructure(output, "latestPaperIdentity", identity != null);
        if (identity != null) {
            writeString(output, "latestPaperIdentity.paperRunId", identity.paperRunId());
            writeEnum(output, "latestPaperIdentity.status", identity.status());
            writeEnum(output, "latestPaperIdentity.tradeEnvironment", identity.tradeEnvironment());
            writeInstant(output, "latestPaperIdentity.updatedAt", identity.updatedAt());
        }
    }

    private void writeShadow(DataOutputStream output, ShadowEvidenceIdentity identity) throws IOException {
        writeStructure(output, "latestShadowEvidenceIdentity", identity != null);
        if (identity != null) {
            writeUuid(output, "latestShadowEvidenceIdentity.shadowRunId", identity.shadowRunId());
            writeEnum(output, "latestShadowEvidenceIdentity.status", identity.status());
            writeInstant(output, "latestShadowEvidenceIdentity.updatedAt", identity.updatedAt());
        }
    }

    private void writeConsistency(DataOutputStream output, ConsistencyEvidenceIdentity identity) throws IOException {
        writeStructure(output, "latestConsistencyIdentity", identity != null);
        if (identity != null) {
            writeUuid(output, "latestConsistencyIdentity.consistencyReportId", identity.consistencyReportId());
            writeUuid(output, "latestConsistencyIdentity.shadowRunId", identity.shadowRunId());
            writeEnum(output, "latestConsistencyIdentity.status", identity.status());
            writeInstant(output, "latestConsistencyIdentity.generatedAt", identity.generatedAt());
        }
    }

    private void writePolicy(DataOutputStream output, ShadowRunCreationPlan.SideEffectPolicy policy) throws IOException {
        writeStructure(output, "sideEffectPolicy", policy != null);
        if (policy != null) {
            writeBoolean(output, "sideEffectPolicy.noOrderSubmission", policy.noOrderSubmission());
            writeBoolean(output, "sideEffectPolicy.noCredentialAccess", policy.noCredentialAccess());
            writeBoolean(output, "sideEffectPolicy.noPrivateEndpoint", policy.noPrivateEndpoint());
            writeBoolean(output, "sideEffectPolicy.noLedgerMutation", policy.noLedgerMutation());
            writeBoolean(output, "sideEffectPolicy.noAccountMutation", policy.noAccountMutation());
            writeBoolean(output, "sideEffectPolicy.noExternalPrivateIo", policy.noExternalPrivateIo());
        }
    }

    private void writeEnum(DataOutputStream output, String tag, String value) throws IOException {
        writeString(output, tag, value == null ? null : value.toUpperCase(Locale.ROOT));
    }

    private void writeString(DataOutputStream output, String tag, String value) throws IOException {
        writeTag(output, tag, TYPE_UTF8, value != null);
        if (value != null) {
            writeUtf8(output, value);
        }
    }

    private void writeUuid(DataOutputStream output, String tag, UUID value) throws IOException {
        writeString(output, tag, value == null ? null : value.toString().toLowerCase(Locale.ROOT));
    }

    private void writeInteger(DataOutputStream output, String tag, int value) throws IOException {
        writeTag(output, tag, TYPE_INTEGER, true);
        output.writeInt(value);
    }

    private void writeLong(DataOutputStream output, String tag, long value) throws IOException {
        writeTag(output, tag, TYPE_LONG, true);
        output.writeLong(value);
    }

    private void writeInstant(DataOutputStream output, String tag, Instant value) throws IOException {
        writeTag(output, tag, TYPE_INSTANT, value != null);
        if (value != null) {
            output.writeLong(value.getEpochSecond());
            output.writeInt(value.getNano());
        }
    }

    private void writeBoolean(DataOutputStream output, String tag, boolean value) throws IOException {
        writeTag(output, tag, TYPE_BOOLEAN, true);
        output.writeByte(value ? 1 : 0);
    }

    private void writeStructure(DataOutputStream output, String tag, boolean present) throws IOException {
        writeTag(output, tag, TYPE_STRUCTURE, present);
    }

    private void writeTag(DataOutputStream output, String tag, byte type, boolean present) throws IOException {
        writeUtf8(output, tag);
        output.writeByte(type);
        output.writeByte(present ? 1 : 0);
    }

    private void writeUtf8(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
