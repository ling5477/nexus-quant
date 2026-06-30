package com.guidinglight.nexusquant.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * OperationalReadinessBoundaryTest proves the 6B summary cannot trigger runtime integration calls.
 *
 * <p>Why: this backend MVP must not call adapters, permission probes, external exchanges, DB, files,
 * or HTTP clients. Reflection keeps that boundary explicit by allowing only a Clock instance field
 * and constructors with no collaborator beyond Clock.
 */
class OperationalReadinessBoundaryTest {

    private static final Set<String> FORBIDDEN_COLLABORATOR_MARKERS = Set.of(
            "adapter",
            "probe",
            "exchange",
            "client",
            "http",
            "resttemplate",
            "webclient",
            "datasource",
            "repository"
    );

    @Test
    void serviceOnlyDependsOnClock() {
        List<Field> instanceFields = Arrays.stream(OperationalReadinessService.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertEquals(1, instanceFields.size(), "service must not gain runtime collaborators");
        assertEquals(Clock.class, instanceFields.get(0).getType(), "only Clock is allowed");
        assertAllowedType(instanceFields.get(0).getType().getName());
    }

    @Test
    void constructorsDoNotAcceptAdapterProbeExchangeOrClientDependencies() {
        for (Constructor<?> constructor : OperationalReadinessService.class.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                assertEquals(Clock.class, parameterType, "only Clock constructor dependency is allowed");
                assertAllowedType(parameterType.getName());
            }
        }
    }

    private static void assertAllowedType(String typeName) {
        String lower = typeName.toLowerCase();
        for (String marker : FORBIDDEN_COLLABORATOR_MARKERS) {
            assertTrue(!lower.contains(marker), "forbidden runtime collaborator type: " + typeName);
        }
    }
}
