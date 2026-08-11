package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * {@code strategy-release-manifest-fingerprint.v1} canonical encoder。
 *
 * <p>编码包含固定 field/type tag、presence byte、length-prefixed UTF-8、UUID lowercase canonical、
 * Instant epoch-second+nano，以及按完整 descriptor tuple 排序的 artifact descriptors；不依赖 JSON
 * 字段顺序、空白、locale 或 ISO 时间格式。
 */
@Component
public final class StrategyReleaseManifestFingerprinter {

    public static final String FINGERPRINT_SCHEMA_VERSION = "strategy-release-manifest-fingerprint.v1";
    private static final byte TYPE_UTF8 = 1;
    private static final byte TYPE_INSTANT = 2;
    private static final byte TYPE_LIST = 3;
    private static final byte TYPE_LONG = 4;
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    public String fingerprint(StrategyArtifactManifest manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        validate(manifest);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeString(output, "fingerprintSchema", FINGERPRINT_SCHEMA_VERSION);
                writeString(output, "manifest.schemaVersion", manifest.schemaVersion());
                writeString(output, "manifest.strategyVersionId", manifest.strategyVersionId());
                writeUuid(output, "manifest.datasetId", manifest.datasetId());
                writeString(output, "manifest.evaluationId", manifest.evaluationId());
                writeString(output, "manifest.artifactDigest", manifest.artifactDigest());
                writeInstant(output, "manifest.generatedAt", manifest.generatedAt());
                writeString(output, "manifest.generatorVersion", manifest.generatorVersion());
                writeArtifacts(output, sortedArtifacts(manifest.artifactFiles()));
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("canonical manifest encoding failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void writeArtifacts(DataOutputStream output, List<StrategyArtifactManifest.ArtifactFile> artifacts)
            throws IOException {
        writeTag(output, "manifest.artifactFiles", TYPE_LIST, true);
        output.writeInt(artifacts.size());
        for (StrategyArtifactManifest.ArtifactFile artifact : artifacts) {
            writeString(output, "artifact.logicalName", artifact.logicalName());
            writeString(output, "artifact.relativePath", artifact.relativePath());
            writeString(output, "artifact.sha256", artifact.sha256());
            writeLong(output, "artifact.sizeBytes", artifact.sizeBytes());
            writeString(output, "artifact.mediaType", artifact.mediaType());
        }
    }

    private List<StrategyArtifactManifest.ArtifactFile> sortedArtifacts(
            List<StrategyArtifactManifest.ArtifactFile> artifacts
    ) {
        List<StrategyArtifactManifest.ArtifactFile> sorted = new ArrayList<>(artifacts);
        sorted.sort(Comparator.comparing(StrategyArtifactManifest.ArtifactFile::logicalName)
                .thenComparing(StrategyArtifactManifest.ArtifactFile::relativePath)
                .thenComparing(StrategyArtifactManifest.ArtifactFile::sha256)
                .thenComparingLong(StrategyArtifactManifest.ArtifactFile::sizeBytes)
                .thenComparing(StrategyArtifactManifest.ArtifactFile::mediaType));
        return sorted;
    }

    private void writeString(DataOutputStream output, String tag, String value) throws IOException {
        writeTag(output, tag, TYPE_UTF8, value != null);
        if (value != null) {
            writeUtf8(output, value);
        }
    }

    private void writeUuid(DataOutputStream output, String tag, UUID value) throws IOException {
        writeString(output, tag, value == null ? null : value.toString().toLowerCase(java.util.Locale.ROOT));
    }

    private void writeInstant(DataOutputStream output, String tag, Instant value) throws IOException {
        writeTag(output, tag, TYPE_INSTANT, value != null);
        if (value != null) {
            output.writeLong(value.getEpochSecond());
            output.writeInt(value.getNano());
        }
    }

    private void writeLong(DataOutputStream output, String tag, long value) throws IOException {
        writeTag(output, tag, TYPE_LONG, true);
        output.writeLong(value);
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

    private void validate(StrategyArtifactManifest manifest) {
        requireText(manifest.schemaVersion(), "schemaVersion");
        requireText(manifest.strategyVersionId(), "strategyVersionId");
        Objects.requireNonNull(manifest.datasetId(), "datasetId must not be null");
        requireText(manifest.evaluationId(), "evaluationId");
        if (!SHA_256.matcher(requireText(manifest.artifactDigest(), "artifactDigest")).matches()) {
            throw new IllegalArgumentException("artifactDigest must be lowercase SHA-256");
        }
        Objects.requireNonNull(manifest.generatedAt(), "generatedAt must not be null");
        requireText(manifest.generatorVersion(), "generatorVersion");
        if (manifest.artifactFiles() == null || manifest.artifactFiles().isEmpty()) {
            throw new IllegalArgumentException("artifactFiles must not be empty");
        }
        for (StrategyArtifactManifest.ArtifactFile artifact : manifest.artifactFiles()) {
            Objects.requireNonNull(artifact, "artifact descriptor must not be null");
            requireText(artifact.logicalName(), "artifact.logicalName");
            requireText(artifact.relativePath(), "artifact.relativePath");
            if (!SHA_256.matcher(requireText(artifact.sha256(), "artifact.sha256")).matches()) {
                throw new IllegalArgumentException("artifact.sha256 must be lowercase SHA-256");
            }
            if (artifact.sizeBytes() < 0) {
                throw new IllegalArgumentException("artifact.sizeBytes must not be negative");
            }
            requireText(artifact.mediaType(), "artifact.mediaType");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
