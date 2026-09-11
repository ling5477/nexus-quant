package com.guidinglight.nexusquant.app.smoke;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 单次有界负载只改变 Kill 状态，不叠加进程、网络或数据库故障。 */
@EnabledIfSystemProperty(named = "nq.l5.kill", matches = "true")
class L5KillUnderLoadTest {
    @Test void existingWorkConvergesWhileNewAdmissionsAreRejected() throws Exception {
        assertEquals("NONE", System.getProperty("nq.l5.fault", "NONE"));
        assertEquals("K1", System.getProperty("nq.l5.level"));
        var dir = B0Processes.root().resolve("backend/nq-app/target/l5-kill-closure/run-" + UUID.randomUUID());
        System.out.println("L5_KILL_ROOT " + dir);
        new L5BoundedWorkloadTest().execute(dir, 1);
    }
}
