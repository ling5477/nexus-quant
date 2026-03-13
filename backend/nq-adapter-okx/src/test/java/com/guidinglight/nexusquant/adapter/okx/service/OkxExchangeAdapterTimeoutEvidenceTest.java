package com.guidinglight.nexusquant.adapter.okx.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OkxExchangeAdapterTimeoutEvidenceTest 覆盖 GateD 真实验收补证用的一次性 timeout 注入开关。
 * <p>
 * Why:
 * 该逻辑只应该在显式开关开启时生效，且每个 JVM 进程只消费一次。这里用最小单测固定住
 * “默认关闭 / 打开后一次性消费 / 可重置”的行为，避免补证代码污染默认主链。
 */
class OkxExchangeAdapterTimeoutEvidenceTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("nq.okx.force.place.timeout.once");
        System.clearProperty("nq.okx.force.cancel.timeout.once");
        OkxExchangeAdapter.resetTimeoutEvidenceForTest();
    }

    @Test
    void shouldConsumePlaceTimeoutEvidenceOnlyOncePerReset() {
        System.setProperty("nq.okx.force.place.timeout.once", "true");

        assertTrue(OkxExchangeAdapter.consumePlaceTimeoutEvidenceOnce());
        assertFalse(OkxExchangeAdapter.consumePlaceTimeoutEvidenceOnce());

        OkxExchangeAdapter.resetTimeoutEvidenceForTest();

        assertTrue(OkxExchangeAdapter.consumePlaceTimeoutEvidenceOnce());
    }

    @Test
    void shouldConsumeCancelTimeoutEvidenceOnlyOncePerReset() {
        System.setProperty("nq.okx.force.cancel.timeout.once", "true");

        assertTrue(OkxExchangeAdapter.consumeCancelTimeoutEvidenceOnce());
        assertFalse(OkxExchangeAdapter.consumeCancelTimeoutEvidenceOnce());
    }

    @Test
    void shouldStayDisabledWhenNoEvidenceFlagProvided() {
        assertFalse(OkxExchangeAdapter.consumePlaceTimeoutEvidenceOnce());
        assertFalse(OkxExchangeAdapter.consumeCancelTimeoutEvidenceOnce());
    }
}
