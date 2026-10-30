package com.guidinglight.nexusquant.app.research;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.domain.port.ResearchDatasetVersionPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 同一 Java 冻结样本及 Python 真 CLI 产物，验证双语言规范摘要与严格诊断边界。 */
class FactorResearchServiceTest {
    static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void pythonArtifactMatchesJavaCanonicalBytesAndExactFrozenIdentity() throws Exception {
        var fixture = fixtures();
        var export = JSON.readTree(Files.readString(fixture.resolve("java-frozen-v2.json")));
        var artifact = JSON.readTree(Files.readString(fixture.resolve("java-factor-artifact.json")));
        UUID id = UUID.fromString(export.path("dataset").path("datasetId").asText());
        var port = mock(ResearchDatasetVersionPort.class);
        when(port.export(id)).thenReturn(export);
        var service = new FactorResearchService(port, JSON);
        assertEquals(artifact, service.preview(id, artifact));
        assertThrows(IllegalArgumentException.class, () -> service.preview(id, null));
        for (String mutation : java.util.List.of("checksum", "version", "dataset", "live", "count", "time", "field", "stats", "invalidTime")) {
            ObjectNode bad = artifact.deepCopy();
            switch (mutation) {
                case "checksum" -> bad.put("checksum", "0".repeat(64));
                case "version" -> bad.put("schemaVersion", "python-evaluation-artifact.v1");
                case "dataset" -> ((ObjectNode) bad.path("dataset")).put("sourceSha256", "0".repeat(64));
                case "live" -> bad.put("liveExecutionReady", true);
                case "count" -> bad.put("barCount", 720);
                case "time" -> ((ObjectNode) bad.path("factors").get(0).path("points").get(30)).put("featureAvailableAt", "2026-04-01T00:00:00Z");
                case "field" -> bad.put("strategyVersion", "fabricated");
                case "stats" -> ((ObjectNode) bad.path("factors").get(0).path("statistics").path("windows").get(0)).put("lastLabelAvailableAt", "2026-12-01T00:00:00Z");
                case "invalidTime" -> bad.put("generatedAt", "not-a-time");
                default -> throw new AssertionError();
            }
            if (!mutation.equals("checksum")) rehash(bad);
            assertThrows(RuntimeException.class, () -> service.preview(id, bad), mutation);
        }
    }

    static void rehash(ObjectNode artifact) {
        artifact.remove(java.util.List.of("artifactId", "checksum"));
        artifact.put("artifactId", "factor-" + FactorResearchService.sha256(FactorResearchService.canonical(artifact)));
        artifact.put("checksum", FactorResearchService.sha256(FactorResearchService.canonical(artifact)));
    }

    static Path fixtures() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path found = directory.resolve("research/py/fixtures");
            if (Files.exists(found.resolve("java-frozen-v2.json"))) return found;
            directory = directory.getParent();
        }
        throw new IllegalStateException("shared Java/Python fixture required");
    }
}
