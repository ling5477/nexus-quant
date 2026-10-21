package com.guidinglight.nexusquant.auth.application;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.domain.tenant.TenantMembership;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CurrentUserProfileServiceTest {

    @Test
    void shouldReturnEmptyWhenUsernameBlank() {
        CurrentUserProfileService service = new CurrentUserProfileService(new StubAuthUserRepository(Optional.empty()));

        assertTrue(service.findByUsername(" ").isEmpty());
    }

    @Test
    void shouldDelegateToRepositoryWhenUsernamePresent() {
        AuthUserProfile expected = new AuthUserProfile(1L, "admin", "hash", List.of("ADMIN"), true);
        CurrentUserProfileService service = new CurrentUserProfileService(new StubAuthUserRepository(Optional.of(expected)));

        assertEquals(Optional.of(expected), service.findByUsername("admin"));
    }

    private record StubAuthUserRepository(Optional<AuthUserProfile> userProfile) implements AuthUserRepository {

        @Override
        public List<TenantMembership> findTenantMemberships(long userId) { return List.of(); }

        @Override
        public Optional<AuthUserProfile> findByUsername(String username) {
            return userProfile;
        }

        @Override
        public boolean hasAdminUser() {
            return userProfile.isPresent();
        }

        @Override
        public boolean createInitialAdminIfAbsent(SeedUserCommand command) { throw new UnsupportedOperationException(); }
        @Override
        public Optional<AuthUserProfile> findByUsernameForUpdate(String username) { throw new UnsupportedOperationException(); }
        @Override
        public boolean changePassword(Long id, long version, String hash) { throw new UnsupportedOperationException(); }

        @Override public void createSession(com.guidinglight.nexusquant.auth.domain.AuthSession s, java.time.Instant cutoff) { throw new UnsupportedOperationException(); }
        @Override public boolean isSessionActive(String id, String name, long version, java.time.Instant now, java.time.Instant cutoff) { throw new UnsupportedOperationException(); }
        @Override public Optional<com.guidinglight.nexusquant.auth.domain.AuthSession> renewSession(String id, long userId, long version, java.time.Instant now, java.time.Instant cutoff) { throw new UnsupportedOperationException(); }
        @Override public void revokeSession(String id, long userId) { throw new UnsupportedOperationException(); }
        @Override public void revokeUserTokens(long userId, long version) { throw new UnsupportedOperationException(); }

        @Override
        public void upsertSeedUser(SeedUserCommand command) {
            throw new UnsupportedOperationException("not required for CurrentUserProfileServiceTest");
        }
    }
}
