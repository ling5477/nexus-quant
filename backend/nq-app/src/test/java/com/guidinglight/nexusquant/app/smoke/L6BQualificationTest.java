package com.guidinglight.nexusquant.app.smoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 明确区分短能力probe与正式180min；正式入口没有时长覆盖属性。 */
class L6BQualificationTest {
    @Test
    @EnabledIfSystemProperty(named="nq.l6b.probe",matches="true")
    void shortRestartProbe() throws Exception { new L6BRuntime(true).run(); }

    @Test
    @EnabledIfSystemProperty(named="nq.l6b.formal",matches="true")
    void formal180Minutes() throws Exception {
        L6BContract.require(!Boolean.getBoolean("nq.l6b.probe"));
        new L6BRuntime(false).run();
    }
}
