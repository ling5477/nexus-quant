package com.guidinglight.nexusquant.scheduler.validationevidence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** 验证 scheduler 配置默认关闭，并在启动前 fail-fast 拒绝越界 duration 与 lock key。 */
class ValidationEvidenceSchedulerPropertiesTest {

    @Test
    void shouldKeepSafeDefaultsDisabled() {
        ValidationEvidenceSchedulerProperties properties = new ValidationEvidenceSchedulerProperties();

        assertDoesNotThrow(properties::afterPropertiesSet);
        org.junit.jupiter.api.Assertions.assertFalse(properties.isEnabled());
    }

    @Test
    void shouldRejectInvalidDelayTimeoutAndKey() {
        ValidationEvidenceSchedulerProperties shortDelay = new ValidationEvidenceSchedulerProperties();
        shortDelay.setFixedDelay(Duration.ofMillis(999));
        assertThrows(IllegalArgumentException.class, shortDelay::afterPropertiesSet);

        ValidationEvidenceSchedulerProperties longTimeout = new ValidationEvidenceSchedulerProperties();
        longTimeout.setExecutionTimeout(Duration.ofMinutes(5).plusMillis(1));
        assertThrows(IllegalArgumentException.class, longTimeout::afterPropertiesSet);

        ValidationEvidenceSchedulerProperties blankKey = new ValidationEvidenceSchedulerProperties();
        blankKey.setLockName(" ");
        assertThrows(IllegalArgumentException.class, blankKey::afterPropertiesSet);
    }
}
