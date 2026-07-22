package com.guidinglight.nexusquant.strategyrelease.preparation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** PRE-GATEX manifest 强契约原型；只验证 test resource，不进入生产运行路径。 */
class StrategyReleaseManifestPrototypeTest {

    private static final String SCHEMA_RESOURCE = "gatex/strategy-release-manifest.schema.json";
    private static final String GOLDEN_RESOURCE = "gatex/strategy-release-manifest.golden.json";

    @Test
    void shouldParseGoldenSampleAndSatisfyRequiredContract() throws Exception {
        JsonNode schema = ManifestPrototypeContract.readResource(SCHEMA_RESOURCE);
        JsonNode golden = ManifestPrototypeContract.readResource(GOLDEN_RESOURCE);

        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.path("$schema").asText());
        assertFalse(schema.path("additionalProperties").asBoolean(true));
        assertEquals(
                ManifestPrototypeContract.requiredTopLevelFields(),
                ManifestPrototypeContract.stringSet(schema.path("required"))
        );
        assertTrue(
                ManifestPrototypeContract.validate(golden).isEmpty(),
                () -> ManifestPrototypeContract.validate(golden).toString()
        );

        assertDoesNotThrow(() -> UUID.fromString(golden.path("datasetId").asText()));
        assertDoesNotThrow(() -> Instant.parse(golden.path("generatedAt").asText()));
        assertTrue(golden.path("generatedAt").asText().endsWith("Z"));
        assertTrue(ManifestPrototypeContract.isSha256(golden.path("datasetHash").asText()));
        assertTrue(ManifestPrototypeContract.isSha256(golden.path("artifactDigest").asText()));
        golden.path("artifactFiles").forEach(file -> {
            assertTrue(ManifestPrototypeContract.isSafeRelativePath(file.path("relativePath").asText()));
            assertTrue(ManifestPrototypeContract.isSha256(file.path("sha256").asText()));
        });
    }

    @Test
    void shouldRejectTraversalAbsoluteDriveUncAndBackslashPaths() throws Exception {
        List<String> invalidPaths = List.of(
                "../outside.json",
                "artifacts/../outside.json",
                "/var/tmp/outside.json",
                "C:/tmp/outside.json",
                "C:\\tmp\\outside.json",
                "\\\\server\\share\\outside.json"
        );

        for (String invalidPath : invalidPaths) {
            ObjectNode manifest = goldenCopy();
            ((ObjectNode) manifest.path("artifactFiles").path(0)).put("relativePath", invalidPath);

            assertTrue(
                    ManifestPrototypeContract.validate(manifest).stream()
                            .anyMatch(error -> error.startsWith("ARTIFACT_PATH_INVALID")),
                    invalidPath
            );
        }
    }

    @Test
    void shouldMatchCanonicalArtifactDigestAndFailClosedOnMismatch() throws Exception {
        ObjectNode golden = goldenCopy();

        assertEquals(
                golden.path("artifactDigest").asText(),
                ManifestPrototypeContract.computeArtifactDigest((ArrayNode) golden.path("artifactFiles"))
        );
        assertTrue(ManifestPrototypeContract.validate(golden).isEmpty());

        ((ObjectNode) golden.path("artifactFiles").path(0))
                .put("sha256", "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

        assertTrue(ManifestPrototypeContract.validate(golden).contains("ARTIFACT_DIGEST_MISMATCH"));
    }

    @Test
    void shouldRejectUnknownTopLevelFieldAndMissingRequiredField() throws Exception {
        ObjectNode unknownField = goldenCopy();
        unknownField.put("unexpectedField", "synthetic-fixture");
        assertTrue(
                ManifestPrototypeContract.validate(unknownField)
                        .contains("UNKNOWN_TOP_LEVEL_FIELD:unexpectedField")
        );

        ObjectNode missingField = goldenCopy();
        missingField.remove("datasetHash");
        assertTrue(ManifestPrototypeContract.validate(missingField).contains("MISSING_REQUIRED_FIELD:datasetHash"));
    }

    private ObjectNode goldenCopy() throws IOException {
        return (ObjectNode) ManifestPrototypeContract.readResource(GOLDEN_RESOURCE).deepCopy();
    }
}

/**
 * 无额外依赖的 manifest 原型校验器。
 *
 * <p>Why：本任务禁止增加 JSON Schema validator 依赖；这里用 test-only helper 固化最小强契约，
 * 正式 GateX 实现仍需单独选择、评审并接入 production validator。
 */
final class ManifestPrototypeContract {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");
    private static final Set<String> REQUIRED_TOP_LEVEL_FIELDS = Set.of(
            "schemaVersion",
            "strategyVersionId",
            "datasetId",
            "evaluationId",
            "dataWindow",
            "datasetHash",
            "featureDefinitionVersion",
            "parameters",
            "riskBudget",
            "signalOrWeightSummary",
            "artifactFiles",
            "artifactDigest",
            "generatedAt",
            "generatorVersion",
            "boundary"
    );
    private static final Set<String> ARTIFACT_FILE_FIELDS = Set.of(
            "logicalName", "relativePath", "sha256", "sizeBytes", "mediaType"
    );

    private ManifestPrototypeContract() {
    }

    static JsonNode readResource(String resourceName) throws IOException {
        ClassLoader classLoader = ManifestPrototypeContract.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("missing test resource: " + resourceName);
            }
            return MAPPER.readTree(input);
        }
    }

    static Set<String> requiredTopLevelFields() {
        return REQUIRED_TOP_LEVEL_FIELDS;
    }

    static Set<String> stringSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    static List<String> validate(JsonNode manifest) {
        List<String> errors = new ArrayList<>();
        if (manifest == null || !manifest.isObject()) {
            return List.of("MANIFEST_NOT_OBJECT");
        }

        for (String path : SensitiveFieldPolicy.findForbiddenFieldPaths(manifest)) {
            errors.add("SENSITIVE_FIELD_FORBIDDEN:" + path);
        }
        Iterator<String> fieldNames = manifest.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!REQUIRED_TOP_LEVEL_FIELDS.contains(fieldName)) {
                errors.add("UNKNOWN_TOP_LEVEL_FIELD:" + fieldName);
            }
        }
        for (String requiredField : REQUIRED_TOP_LEVEL_FIELDS) {
            if (!manifest.hasNonNull(requiredField)) {
                errors.add("MISSING_REQUIRED_FIELD:" + requiredField);
            }
        }

        if (!"strategy-release-manifest.v1".equals(manifest.path("schemaVersion").asText())) {
            errors.add("SCHEMA_VERSION_UNSUPPORTED");
        }
        validateOpaqueId(manifest, "strategyVersionId", errors);
        validateUuid(manifest, "datasetId", errors);
        validateOpaqueId(manifest, "evaluationId", errors);
        validateUtcWindow(manifest.path("dataWindow"), errors);
        validateUtcInstant(manifest.path("generatedAt").asText(), "GENERATED_AT_INVALID", errors);
        if (!isSha256(manifest.path("datasetHash").asText())) {
            errors.add("DATASET_HASH_INVALID");
        }
        if (!isSha256(manifest.path("artifactDigest").asText())) {
            errors.add("ARTIFACT_DIGEST_INVALID");
        }

        JsonNode files = manifest.path("artifactFiles");
        if (!files.isArray() || files.isEmpty()) {
            errors.add("ARTIFACT_FILES_EMPTY");
        } else {
            validateArtifactFiles((ArrayNode) files, errors);
            if (errors.stream().noneMatch(error -> error.startsWith("ARTIFACT_FILE_"))) {
                String computedDigest = computeArtifactDigest((ArrayNode) files);
                if (!computedDigest.equals(manifest.path("artifactDigest").asText())) {
                    errors.add("ARTIFACT_DIGEST_MISMATCH");
                }
            }
        }

        validateBoundary(manifest.path("boundary"), errors);
        return List.copyOf(errors);
    }

    static boolean isSha256(String value) {
        return value != null && SHA_256.matcher(value).matches();
    }

    static boolean isSafeRelativePath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.startsWith("\\")) {
            return false;
        }
        if (value.contains("\\") || DRIVE_PREFIX.matcher(value).matches() || value.contains("//")) {
            return false;
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    static String computeArtifactDigest(ArrayNode artifactFiles) {
        List<JsonNode> sortedFiles = new ArrayList<>();
        artifactFiles.forEach(sortedFiles::add);
        sortedFiles.sort(Comparator
                .comparing((JsonNode file) -> file.path("logicalName").asText())
                .thenComparing(file -> file.path("relativePath").asText()));

        String canonical = sortedFiles.stream()
                .map(file -> String.join(
                        "\u001f",
                        file.path("logicalName").asText(),
                        file.path("relativePath").asText(),
                        file.path("sha256").asText().toLowerCase(Locale.ROOT),
                        file.path("sizeBytes").asText(),
                        file.path("mediaType").asText()
                ))
                .collect(Collectors.joining("\n"));
        return sha256(canonical);
    }

    private static void validateOpaqueId(JsonNode manifest, String fieldName, List<String> errors) {
        String value = manifest.path(fieldName).asText();
        if (value.isBlank() || value.length() > 128 || !value.matches("^[A-Za-z0-9][A-Za-z0-9._:-]*$")) {
            errors.add(fieldName.toUpperCase(Locale.ROOT) + "_INVALID");
        }
    }

    private static void validateUuid(JsonNode manifest, String fieldName, List<String> errors) {
        try {
            UUID.fromString(manifest.path(fieldName).asText());
        } catch (IllegalArgumentException exception) {
            errors.add(fieldName.toUpperCase(Locale.ROOT) + "_INVALID_UUID");
        }
    }

    private static void validateUtcWindow(JsonNode dataWindow, List<String> errors) {
        if (!dataWindow.isObject()) {
            errors.add("DATA_WINDOW_INVALID");
            return;
        }
        Set<String> actualFields = new LinkedHashSet<>();
        dataWindow.fieldNames().forEachRemaining(actualFields::add);
        if (!actualFields.equals(Set.of("start", "end"))) {
            errors.add("DATA_WINDOW_FIELDS_INVALID");
        }
        Instant start = parseUtcInstant(dataWindow.path("start").asText(), "DATA_WINDOW_START_INVALID", errors);
        Instant end = parseUtcInstant(dataWindow.path("end").asText(), "DATA_WINDOW_END_INVALID", errors);
        if (start != null && end != null && end.isBefore(start)) {
            errors.add("DATA_WINDOW_REVERSED");
        }
    }

    private static void validateUtcInstant(String value, String error, List<String> errors) {
        parseUtcInstant(value, error, errors);
    }

    private static Instant parseUtcInstant(String value, String error, List<String> errors) {
        if (value == null || !value.endsWith("Z")) {
            errors.add(error);
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            errors.add(error);
            return null;
        }
    }

    private static void validateArtifactFiles(ArrayNode files, List<String> errors) {
        for (int index = 0; index < files.size(); index++) {
            JsonNode file = files.get(index);
            if (!file.isObject()) {
                errors.add("ARTIFACT_FILE_NOT_OBJECT:" + index);
                continue;
            }
            Set<String> fields = new HashSet<>();
            file.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(ARTIFACT_FILE_FIELDS)) {
                errors.add("ARTIFACT_FILE_FIELDS_INVALID:" + index);
            }
            if (!isSafeRelativePath(file.path("relativePath").asText())) {
                errors.add("ARTIFACT_PATH_INVALID:" + index);
            }
            if (!isSha256(file.path("sha256").asText())) {
                errors.add("ARTIFACT_FILE_SHA256_INVALID:" + index);
            }
            if (!file.path("sizeBytes").canConvertToLong() || file.path("sizeBytes").asLong() <= 0) {
                errors.add("ARTIFACT_FILE_SIZE_INVALID:" + index);
            }
            if (file.path("logicalName").asText().isBlank() || file.path("mediaType").asText().isBlank()) {
                errors.add("ARTIFACT_FILE_METADATA_INVALID:" + index);
            }
        }
    }

    private static void validateBoundary(JsonNode boundary, List<String> errors) {
        Set<String> expectedFields = Set.of(
                "noCredentialAccess", "noPrivateEndpoint", "diagnosticOnly", "notTradingAuthorization"
        );
        if (!boundary.isObject()) {
            errors.add("BOUNDARY_INVALID");
            return;
        }
        Set<String> actualFields = new HashSet<>();
        boundary.fieldNames().forEachRemaining(actualFields::add);
        if (!actualFields.equals(expectedFields)) {
            errors.add("BOUNDARY_FIELDS_INVALID");
        }
        for (String field : expectedFields) {
            if (!boundary.path(field).isBoolean() || !boundary.path(field).asBoolean()) {
                errors.add("BOUNDARY_MUST_BE_TRUE:" + field);
            }
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

/** Manifest 所有开放 JSON 节点共用的敏感字段 fail-closed policy。 */
final class SensitiveFieldPolicy {

    private static final Set<String> FORBIDDEN_MARKERS = Set.of(
            "apikey",
            "secret",
            "passphrase",
            "token",
            "accesstoken",
            "privatekey",
            "credentialmaterial",
            "decryptedpayload",
            "rawprivaterequest",
            "rawprivateresponse",
            "cookie",
            "authorization"
    );
    private static final Set<String> ALLOWED_BOUNDARY_FIELDS = Set.of(
            "nocredentialaccess",
            "noprivateendpoint",
            "diagnosticonly",
            "nottradingauthorization"
    );

    private SensitiveFieldPolicy() {
    }

    static List<String> findForbiddenFieldPaths(JsonNode payload) {
        List<String> paths = new ArrayList<>();
        inspect(payload, "$", paths);
        return List.copyOf(paths);
    }

    private static void inspect(JsonNode node, String path, List<String> paths) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldPath = path + "." + field.getKey();
                if (isForbidden(field.getKey())) {
                    paths.add(fieldPath);
                }
                inspect(field.getValue(), fieldPath, paths);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                inspect(node.get(index), path + "[" + index + "]", paths);
            }
        }
    }

    private static boolean isForbidden(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ALLOWED_BOUNDARY_FIELDS.contains(normalized)) {
            return false;
        }
        return FORBIDDEN_MARKERS.stream().anyMatch(normalized::contains);
    }
}
