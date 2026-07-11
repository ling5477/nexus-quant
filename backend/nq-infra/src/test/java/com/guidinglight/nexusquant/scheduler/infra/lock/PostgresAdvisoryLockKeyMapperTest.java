package com.guidinglight.nexusquant.scheduler.infra.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;

import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

/** 固定 PostgreSQL advisory lock key 的跨 JVM 确定性协议与输入边界。 */
class PostgresAdvisoryLockKeyMapperTest {

    private final PostgresAdvisoryLockKeyMapper mapper = new PostgresAdvisoryLockKeyMapper();

    @Test
    void shouldMatchStableUtf8BigEndianGoldenVector() {
        SchedulerLockKey key = new SchedulerLockKey("validation-operations", "runtime-evidence");

        PostgresAdvisoryLockKey mapped = mapper.map(key);

        assertEquals(1_343_903_246, mapped.namespaceKey());
        assertEquals(-1_735_239_354, mapped.lockKey());
        assertEquals(mapped, mapper.map(key));
    }

    @Test
    void shouldIsolateNamespaceAndName() {
        PostgresAdvisoryLockKey baseline = mapper.map(new SchedulerLockKey("validation", "evidence"));

        assertNotEquals(baseline, mapper.map(new SchedulerLockKey("review", "evidence")));
        assertNotEquals(baseline, mapper.map(new SchedulerLockKey("validation", "review")));
    }

    @Test
    void shouldNotDependOnDefaultLocaleOrTimeZone() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        SchedulerLockKey key = new SchedulerLockKey("验证-operation", "证据-évidence");
        PostgresAdvisoryLockKey baseline = mapper.map(key);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertEquals(baseline, mapper.map(key));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void shouldFailFastForInvalidComponents() {
        assertThrows(NullPointerException.class, () -> new SchedulerLockKey(null, "job"));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerLockKey(" ", "job"));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerLockKey("namespace", ""));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerLockKey("namespace", "a\njob"));
        assertThrows(IllegalArgumentException.class, () -> new SchedulerLockKey(
                "namespace",
                "x".repeat(SchedulerLockKey.MAX_COMPONENT_LENGTH + 1)
        ));
    }
}
