package com.guidinglight.nexusquant.app.smoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 在常规 Maven 目标测试中执行共享导出器的关系与泄漏回归。 */
class SyntheticEvidenceExportTest {
    @Test void preservesIdentityGraphAndSecretFields() throws Exception {
        SyntheticEvidenceExport.execute("test_synthetic_evidence.py");
    }

    @Test void writesSeparateCanonicalFileWithoutChangingRuntimeEvidence(@TempDir Path directory) throws Exception {
        String identity = java.util.UUID.randomUUID().toString();
        String original = "{\"order_id\":\"" + identity + "\",\"status\":\"FILLED\",\"version\":6}";
        Path raw = directory.resolve("raw-proof.json");
        Files.writeString(raw, original);
        SyntheticEvidenceExport.write(raw, "B2", 1);
        assertEquals(original, Files.readString(raw));
        var exported = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(directory.resolve("proof.json").toFile());
        assertEquals("SYNTH-L4:B2:R01:ORDER:001", exported.path("order_id").asText());
        assertEquals("FILLED", exported.path("status").asText());
        assertEquals(6, exported.path("version").asInt());
    }
}
