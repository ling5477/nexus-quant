package com.guidinglight.nexusquant.auth.application.service;

import com.guidinglight.nexusquant.auth.domain.PasswordRotationException;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationException.Reason;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationTarget;
import com.guidinglight.nexusquant.auth.domain.port.ExistingUserPasswordRotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExistingUserPasswordRotationServiceTest {
    private static final String OLD_PASSWORD = "synthetic-old-owner-password";
    private static final String NEW_PASSWORD = "synthetic-new-owner-password";
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private FakeRepository repository;
    private ExistingUserPasswordRotationService service;
    private String originalHash;

    @BeforeEach
    void setUp() {
        originalHash = encoder.encode(OLD_PASSWORD);
        repository = new FakeRepository(new PasswordRotationTarget(2, "synthetic-owner", true, originalHash));
        service = new ExistingUserPasswordRotationService(repository, encoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rotatesOnlyExistingEnabledIdentityWithBcryptAndRejectsRepeat() {
        rotate(2, "synthetic-owner", fingerprint());
        assertEquals(1, repository.writes);
        assertEquals(2, repository.target.userId());
        assertEquals("synthetic-owner", repository.target.username());
        assertTrue(repository.target.enabled());
        assertNotEquals(originalHash, repository.target.passwordHash());
        assertTrue(encoder.matches(NEW_PASSWORD, repository.target.passwordHash()));
        assertFalse(encoder.matches(OLD_PASSWORD, repository.target.passwordHash()));
        assertEquals(NOW, repository.updatedAt);
        assertEquals(Reason.STALE_AUTH_IDENTITY, assertThrows(PasswordRotationException.class,
                () -> rotate(2, "synthetic-owner", fingerprint())).reason());
        assertEquals(1, repository.writes);
    }

    @Test
    void unknownUserCannotBeInserted() {
        reject(99, "synthetic-owner", fingerprint(), Reason.USER_NOT_FOUND);
    }

    @Test
    void usernameMismatchFailsClosed() {
        reject(2, "other-owner", fingerprint(), Reason.USERNAME_MISMATCH);
    }

    @Test
    void disabledUserCannotBeEnabledOrRotated() {
        repository.target = new PasswordRotationTarget(2, "synthetic-owner", false, originalHash);
        reject(2, "synthetic-owner", fingerprint(), Reason.USER_DISABLED);
        assertFalse(repository.target.enabled());
    }

    @Test
    void staleFingerprintCannotOverwritePassword() {
        reject(2, "synthetic-owner", "0".repeat(64), Reason.STALE_AUTH_IDENTITY);
    }

    @Test
    void mismatchInReturnedUserIdFailsClosed() {
        repository.returnUnexpectedIdentity = true;
        reject(2, "synthetic-owner", fingerprint(), Reason.USERNAME_MISMATCH);
    }

    @Test
    void unchangedPasswordIsRejected() {
        assertEquals(Reason.PASSWORD_UNCHANGED, assertThrows(PasswordRotationException.class,
                () -> service.rotate(2, "synthetic-owner", fingerprint(), OLD_PASSWORD.toCharArray())).reason());
        assertEquals(0, repository.writes);
    }

    @Test
    void malformedAndOverlongInputsFailBeforeWriting() {
        for (String password : new String[]{"short", "a".repeat(73), "界".repeat(25), "synthetic\npassword-value"}) {
            assertEquals(Reason.INVALID_ROTATION_REQUEST, assertThrows(PasswordRotationException.class,
                    () -> service.rotate(2, "synthetic-owner", fingerprint(), password.toCharArray())).reason());
        }
        reject(0, "synthetic-owner", fingerprint(), Reason.INVALID_ROTATION_REQUEST);
        reject(2, "synthetic-owner ", fingerprint(), Reason.INVALID_ROTATION_REQUEST);
        reject(2, "synthetic-owner", "invalid-fingerprint", Reason.INVALID_ROTATION_REQUEST);
    }

    @Test
    void unsupportedHashIsRejectedWithoutLoggingItsValue() {
        repository.target = new PasswordRotationTarget(2, "synthetic-owner", true, "synthetic-invalid-hash");
        reject(2, "synthetic-owner", ExistingUserPasswordRotationService.fingerprint(repository.target.passwordHash()),
                Reason.UNSUPPORTED_PASSWORD_HASH);
        assertFalse(repository.target.toString().contains(repository.target.passwordHash()));
        assertFalse(repository.target.toString().contains(repository.target.username()));
    }

    private String fingerprint() {
        return ExistingUserPasswordRotationService.fingerprint(originalHash);
    }

    private void rotate(long id, String username, String digest) {
        service.rotate(id, username, digest, NEW_PASSWORD.toCharArray());
    }

    private void reject(long id, String username, String digest, Reason reason) {
        assertEquals(reason, assertThrows(PasswordRotationException.class, () -> rotate(id, username, digest)).reason());
        assertEquals(0, repository.writes);
    }

    private static final class FakeRepository implements ExistingUserPasswordRotationRepository {
        private PasswordRotationTarget target;
        private int writes;
        private Instant updatedAt;
        private boolean returnUnexpectedIdentity;

        private FakeRepository(PasswordRotationTarget target) {
            this.target = target;
        }

        @Override
        public Optional<PasswordRotationTarget> findTarget(long exactUserId) {
            if (returnUnexpectedIdentity) {
                return Optional.of(new PasswordRotationTarget(3, target.username(), true, target.passwordHash()));
            }
            return exactUserId == target.userId() ? Optional.of(target) : Optional.empty();
        }

        @Override
        public void updatePasswordHash(long id, String username, String expectedHash, String newHash, Instant now) {
            assertEquals(target.userId(), id);
            assertEquals(target.username(), username);
            assertEquals(target.passwordHash(), expectedHash);
            assertTrue(target.enabled());
            target = new PasswordRotationTarget(id, username, true, newHash);
            updatedAt = now;
            writes++;
        }
    }
}
