package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

/** 验证驱动舍入边界、负纪元和空窗口不会改变冻结身份。 */
class ShadowRunWindowPrecisionTest {
    @Test
    void roundTripRetainsEveryRemainderOnBothSidesOfEpoch() {
        for (String second : new String[] {"2026-10-14T23:59:33Z", "1969-12-31T23:59:59Z"}) {
            for (int remainder = 0; remainder < 1_000; remainder++) {
                Instant value = Instant.parse(second).plusNanos(999_999_000L + remainder);
                Timestamp base = ShadowRunWindowPrecision.timestamp(value);
                assertEquals(0, base.toInstant().getNano() % 1_000);
                assertEquals(remainder, ShadowRunWindowPrecision.remainder(value));
                assertEquals(value, ShadowRunWindowPrecision.restore(base, remainder));
            }
        }
    }

    @Test
    void missingWindowHasNoFabricatedTimeAndInvalidRemainderFailsClosed() {
        assertNull(ShadowRunWindowPrecision.timestamp(null));
        assertEquals(0, ShadowRunWindowPrecision.remainder(null));
        assertNull(ShadowRunWindowPrecision.restore(null, 0));
        assertThrows(IllegalStateException.class, () -> ShadowRunWindowPrecision.restore(null, 1));
        Timestamp base = Timestamp.from(Instant.EPOCH);
        assertThrows(IllegalStateException.class, () -> ShadowRunWindowPrecision.restore(base, -1));
        assertThrows(
                IllegalStateException.class, () -> ShadowRunWindowPrecision.restore(base, 1_000));
    }
}
