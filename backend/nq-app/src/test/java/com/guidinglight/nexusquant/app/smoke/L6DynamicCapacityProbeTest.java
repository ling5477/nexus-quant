package com.guidinglight.nexusquant.app.smoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 显式启动短验证，不缩短或授予正式10/40/10 qualification。 */
@EnabledIfSystemProperty(named = "nq.l6.capacity.probe", matches = "true")
class L6DynamicCapacityProbeTest {
    @Test void realBaselineFrozenCapacityForwardProgressAndCleanup() throws Exception {
        L6FormalRuntime.capacityProbe(L6FormalManifest.read(B0Processes.root().resolve(L6FormalManifest.CANONICAL))).run();
    }
}
