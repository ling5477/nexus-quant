package com.guidinglight.nexusquant.app.smoke;

/** 正式L6使用自身run预算，不继承L5上限；执行队列及协议仍复用原实现。 */
public final class L6FormalVenueProcessMain {
    public static void main(String[] args) throws Exception {
        var parameters = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of("parameters.json")));
        var capacity = "READINESS".equals(parameters.path("mode").asText()) ? QualificationCapacity.formal()
                : L6StorageCalibrationContract.runManifest().capacity(L6StorageCalibrationContract.MODE.equals(parameters.path("mode").asText())
                        ? new L6StorageCalibrationContract(parameters.path("shortSmoke").asBoolean()).timing()
                        : L6FormalManifest.timing(parameters.path("shortSmoke").asBoolean()));
        capacity.validate();
        B0SyntheticVenueMain.boundedWorkload = true;
        B0SyntheticVenueMain.boundedOrderCapacity = capacity.venueLogicalOrderCapacity();
        B0SyntheticVenueMain.main(args);
    }
}
