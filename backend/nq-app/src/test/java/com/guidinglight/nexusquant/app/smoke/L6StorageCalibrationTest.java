package com.guidinglight.nexusquant.app.smoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 唯一显式入口；默认35分钟，只在明确smoke参数下执行100秒。 */
@EnabledIfSystemProperty(named = "nq.l6.storage.calibration", matches = "true")
class L6StorageCalibrationTest {
    @Test void storageCalibration() throws Exception {
        var contract = new L6StorageCalibrationContract(Boolean.getBoolean("nq.l6.storage.calibration.smoke"));
        L6FormalManifest.start(B0Processes.root().resolve(L6FormalManifest.CANONICAL), contract.timing(),
                (manifest, capacity) -> new L6FormalRuntime(manifest, capacity, contract).run());
    }
}
