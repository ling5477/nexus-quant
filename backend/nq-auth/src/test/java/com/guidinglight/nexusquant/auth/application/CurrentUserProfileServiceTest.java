package com.guidinglight.nexusquant.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;

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
        public Optional<AuthUserProfile> findByUsername(String username) {
            return userProfile;
        }

        @Override
        public boolean hasAdminUser() {
            return userProfile.isPresent();
        }

        @Override
        public void upsertSeedUser(com.guidinglight.nexusquant.auth.application.command.SeedUserCommand command) {
            throw new UnsupportedOperationException("not required for CurrentUserProfileServiceTest");
        }
    }
}
