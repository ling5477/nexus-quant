package com.guidinglight.nexusquant.auth.application;

import com.guidinglight.nexusquant.auth.application.service.TenantIdentityResolver;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.domain.tenant.Tenant;
import com.guidinglight.nexusquant.auth.domain.tenant.TenantMembership;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIdentityTest {
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    @AfterEach
    void clear() { TenantContext.clear(); }

    @Test
    void rejectsMalformedZeroAndMismatchedIdentities() {
        for (String invalid : List.of("", "1", "1-1-1-1-1", "00000000-0000-0000-0000-000000000000"))
            assertThrows(IllegalArgumentException.class, () -> TenantId.parse(invalid));
        assertThrows(IllegalArgumentException.class, () -> new TenantIdentity(TenantId.LOCAL, "OTHER", TenantKind.LOCAL, 1));
        assertThrows(IllegalArgumentException.class, () -> new TenantIdentity(TenantId.LOCAL, "NQ_LOCAL", TenantKind.DEMO, 1));
        assertThrows(IllegalArgumentException.class, () -> new TenantIdentity(TenantId.LOCAL, "NQ_LOCAL", TenantKind.LOCAL, 0));
    }

    @Test
    void localMembershipPreservesAllExistingRolesWithoutDerivingTenantFromUser() {
        var user = user(true, "ADMIN", "OPERATOR", "VIEWER");
        var resolved = resolver(List.of(member(false, false, false))).resolve(user);
        assertEquals(TenantId.LOCAL, resolved.tenant().tenantId());
        assertEquals(7, resolved.tenant().userId());
        assertEquals(user.roles(), resolved.roles());
        assertTrue(resolver(List.of(member(false, false, false))).resolve(user(true)).roles().isEmpty());
    }

    @Test
    void absentDisabledAndAmbiguousMembershipsNeverFallBackToLocal() {
        var user = user(true, "VIEWER");
        for (var members : List.of(List.<TenantMembership>of(), List.of(member(false, true, false)),
                List.of(member(false, false, true)), List.of(member(false, false, false), member(false, false, false))))
            assertThrows(BadCredentialsException.class, () -> resolver(members).resolve(user));
        assertThrows(BadCredentialsException.class, () -> resolver(List.of(member(false, false, false))).resolve(user(false, "VIEWER")));
    }

    @Test
    void demoAuthorityComesFromMembershipAndCannotCombineWithLocalPrivileges() {
        var resolver = resolver(List.of(member(true, false, false)));
        assertEquals(List.of("DEMO_USER"), resolver.resolve(user(true)).roles());
        assertEquals(List.of("DEMO_USER"), resolver.resolve(user(true, "DEMO_USER")).roles());
        for (String legacy : List.of("ADMIN", "OPERATOR", "VIEWER", "PLATFORM_ADMIN"))
            assertThrows(BadCredentialsException.class, () -> resolver.resolve(user(true, "DEMO_USER", legacy)));
        assertThrows(BadCredentialsException.class, () -> resolver(List.of(member(false, false, false))).resolve(user(true, "DEMO_USER")));
    }

    @Test
    void platformIdentityHasNoBusinessTenantAndCannotBorrowLocalMembership() {
        var platform = resolver(List.of()).resolve(user(true, "PLATFORM_ADMIN"));
        assertNull(platform.tenant());
        assertEquals(List.of("PLATFORM_ADMIN"), platform.roles());
        assertThrows(BadCredentialsException.class, () -> resolver(List.of()).resolve(user(true, "PLATFORM_ADMIN", "ADMIN")));
        assertThrows(BadCredentialsException.class, () -> resolver(List.of(member(false, false, false))).resolve(user(true, "PLATFORM_ADMIN")));
    }

    @Test
    void membershipRoleAndTenantKindMustAgree() {
        assertThrows(IllegalArgumentException.class, () -> new TenantMembership(member(true, false, false).tenant(),
                7, TenantMembership.Role.LOCAL_MEMBER, TenantMembership.Status.ACTIVE, NOW, NOW));
    }

    @Test
    void scopesCleanExceptionsNestAndDoNotInheritToWorkers() throws Exception {
        var local = new TenantIdentity(TenantId.LOCAL, "NQ_LOCAL", TenantKind.LOCAL, 7);
        var demo = new TenantIdentity(new TenantId(UUID.randomUUID()), "DEMO_A", TenantKind.DEMO, 8);
        assertThrows(IllegalStateException.class, TenantContext::requireCurrent);
        try (var scope = TenantContext.open(local); var worker = Executors.newSingleThreadExecutor()) {
            assertFalse(worker.submit(() -> TenantContext.current().isPresent()).get());
            assertThrows(IllegalArgumentException.class, () -> {
                try (var nested = TenantContext.open(demo)) {
                    assertEquals(demo, TenantContext.requireCurrent());
                    throw new IllegalArgumentException("synthetic failure");
                }
            });
            assertEquals(local, TenantContext.requireCurrent());
            var failure = worker.submit(() -> {
                try (var nested = TenantContext.open(demo)) { throw new IllegalArgumentException("worker failure"); }
            });
            assertTrue(assertThrows(ExecutionException.class, failure::get).getCause() instanceof IllegalArgumentException);
            assertTrue(worker.submit(() -> TenantContext.current().isEmpty()).get());
        }
        assertTrue(TenantContext.current().isEmpty());
    }

    private static AuthUserProfile user(boolean enabled, String... roles) {
        return new AuthUserProfile(7L, "fixture-user", "synthetic-hash", List.of(roles), enabled);
    }

    private static TenantMembership member(boolean demo, boolean tenantDisabled, boolean memberDisabled) {
        var tenant = new Tenant(demo ? new TenantId(UUID.randomUUID()) : TenantId.LOCAL,
                demo ? "DEMO_A" : "NQ_LOCAL", "租户", demo ? TenantKind.DEMO : TenantKind.LOCAL,
                tenantDisabled ? Tenant.Status.DISABLED : Tenant.Status.ACTIVE, NOW, NOW);
        return new TenantMembership(tenant, 7, demo ? TenantMembership.Role.DEMO_USER : TenantMembership.Role.LOCAL_MEMBER,
                memberDisabled ? TenantMembership.Status.DISABLED : TenantMembership.Status.ACTIVE, NOW, NOW);
    }

    private static TenantIdentityResolver resolver(List<TenantMembership> members) {
        var repository = (AuthUserRepository) Proxy.newProxyInstance(AuthUserRepository.class.getClassLoader(),
                new Class<?>[]{AuthUserRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("findTenantMemberships")) return members;
                    throw new UnsupportedOperationException(method.getName());
                });
        return new TenantIdentityResolver(repository);
    }
}
