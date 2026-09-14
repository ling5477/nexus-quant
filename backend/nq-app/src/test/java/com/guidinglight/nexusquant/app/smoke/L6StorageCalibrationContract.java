package com.guidinglight.nexusquant.app.smoke;

import java.util.concurrent.TimeUnit;

/** 模式显式传递；短 smoke 不改变正式合同，也不生成正式校准接受。 */
record L6StorageCalibrationContract(boolean smoke) {
    static final String MODE = "L6_PG_STORAGE_CALIBRATION";
    L6DurationContract timing() {
        return new L6DurationContract(seconds(smoke ? 20 : 300), seconds(smoke ? 50 : 1200), seconds(smoke ? 30 : 600));
    }
    private static long seconds(long value) { return TimeUnit.SECONDS.toNanos(value); }

    static L6FormalManifest runManifest() throws Exception {
        var parameters = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of("parameters.json")));
        if (!MODE.equals(parameters.path("mode").asText())) return L6FormalManifest.fromRunDirectory();
        B0Fixture.require(parameters.path("shortSmoke").isBoolean());
        var manifest = L6FormalManifest.read(B0Processes.root().resolve(L6FormalManifest.CANONICAL));
        B0Fixture.require(manifest.identity().path("sha256").asText().equals(parameters.path("manifestEntry").path("sha256").asText()));
        return manifest;
    }
}
