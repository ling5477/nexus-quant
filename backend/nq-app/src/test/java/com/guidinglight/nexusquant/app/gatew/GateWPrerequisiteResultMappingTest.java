package com.guidinglight.nexusquant.app.gatew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GateWPrerequisiteResultMappingTest {

    @Test
    void acceptsOnlyBoundedNonNegativeJdbcIntegerTypes() {
        assertEquals(0L, GateWOkxReadonlySoakCycleTest.strictCountValue(0L));
        assertEquals(1L, GateWOkxReadonlySoakCycleTest.strictCountValue(1));
        assertEquals(2L, GateWOkxReadonlySoakCycleTest.strictCountValue((short) 2));
        assertEquals(2L, GateWOkxReadonlySoakCycleTest.strictCountValue((byte) 2));
    }

    @Test
    void rejectsNullMissingNegativeOverflowAndWrongTypes() {
        assertMappingFailure(null);
        assertMappingFailure(Map.of().get("missing_column"));
        assertMappingFailure(-1L);
        assertMappingFailure(Long.MAX_VALUE);
        assertMappingFailure(new BigDecimal("1.5"));
        assertMappingFailure(new BigDecimal("1"));
        assertMappingFailure(BigInteger.ONE);
        assertMappingFailure("1");
        assertMappingFailure(Boolean.TRUE);
        assertMappingFailure(new Object());
    }

    private static void assertMappingFailure(Object value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> GateWOkxReadonlySoakCycleTest.strictCountValue(value)
        );
    }
}
