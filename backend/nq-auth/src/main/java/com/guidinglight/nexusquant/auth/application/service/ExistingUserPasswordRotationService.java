package com.guidinglight.nexusquant.auth.application.service;

import com.guidinglight.nexusquant.auth.domain.PasswordRotationException;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationException.Reason;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationTarget;
import com.guidinglight.nexusquant.auth.domain.port.ExistingUserPasswordRotationRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** 显式恢复现有用户登录；检查身份快照后交给 repository 原子更新，不经过 seed 路径。 */
public final class ExistingUserPasswordRotationService {
    private final ExistingUserPasswordRotationRepository repository;
    private final BCryptPasswordEncoder encoder;
    private final Clock clock;

    public ExistingUserPasswordRotationService(ExistingUserPasswordRotationRepository repository,
                                               BCryptPasswordEncoder encoder, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.encoder = Objects.requireNonNull(encoder);
        this.clock = Objects.requireNonNull(clock);
    }

    public void rotate(long exactUserId, String expectedUsername, String expectedHashSha256, char[] password) {
        if (exactUserId <= 0 || expectedUsername == null || expectedUsername.isBlank()
                || expectedUsername.length() > 64 || !expectedUsername.equals(expectedUsername.trim())
                || expectedHashSha256 == null || !expectedHashSha256.matches("[0-9a-f]{64}")) {
            throw new PasswordRotationException(Reason.INVALID_ROTATION_REQUEST);
        }
        validatePassword(password);
        PasswordRotationTarget target = repository.findTarget(exactUserId)
                .orElseThrow(() -> new PasswordRotationException(Reason.USER_NOT_FOUND));
        if (target.userId() != exactUserId || !expectedUsername.equals(target.username())) {
            throw new PasswordRotationException(Reason.USERNAME_MISMATCH);
        }
        if (!target.enabled()) {
            throw new PasswordRotationException(Reason.USER_DISABLED);
        }
        if (!expectedHashSha256.equals(fingerprint(target.passwordHash()))) {
            throw new PasswordRotationException(Reason.STALE_AUTH_IDENTITY);
        }
        // 限制异常 hash 的计算成本，避免损坏数据令维护进程无界占用 CPU。
        if (!target.passwordHash().matches("\\$2[aby]\\$(0[4-9]|1[0-6])\\$[./A-Za-z0-9]{53}")) {
            throw new PasswordRotationException(Reason.UNSUPPORTED_PASSWORD_HASH);
        }
        CharBuffer secret = CharBuffer.wrap(password);
        if (encoder.matches(secret, target.passwordHash())) {
            throw new PasswordRotationException(Reason.PASSWORD_UNCHANGED);
        }
        String newHash = encoder.encode(secret);
        repository.updatePasswordHash(exactUserId, expectedUsername, target.passwordHash(), newHash, clock.instant());
    }

    public static String fingerprint(String hash) {
        if (hash == null) {
            throw new PasswordRotationException(Reason.UNSUPPORTED_PASSWORD_HASH);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(hash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", exception);
        }
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 16 || password.length > 72) {
            throw new PasswordRotationException(Reason.INVALID_ROTATION_REQUEST);
        }
        for (char value : password) {
            if (Character.isISOControl(value) || Character.isSurrogate(value)) {
                throw new PasswordRotationException(Reason.INVALID_ROTATION_REQUEST);
            }
        }
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        try {
            // BCrypt 最多消费 72 字节，不允许两个不同输入因截断变成同一密码。
            if (encoded.remaining() > 72) {
                throw new PasswordRotationException(Reason.INVALID_ROTATION_REQUEST);
            }
        } finally {
            Arrays.fill(encoded.array(), (byte) 0);
        }
    }
}
