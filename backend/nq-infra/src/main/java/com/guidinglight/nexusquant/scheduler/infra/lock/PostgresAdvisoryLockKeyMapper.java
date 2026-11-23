package com.guidinglight.nexusquant.scheduler.infra.lock;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 将通用 SchedulerLockKey 映射为 PostgreSQL {@code int,int} advisory lock key。
 *
 * <p>映射协议固定使用 UTF-8、SHA-256、domain separator 与 big-endian 前四字节，
 * 因此不受 JVM、机器、locale、时区或平台默认 charset 影响。不得替换为
 * {@link String#hashCode()}，否则会把跨进程协议退化为碰撞更高且语义不明确的实现。
 */
public final class PostgresAdvisoryLockKeyMapper {

    private static final byte[] NAMESPACE_DOMAIN = "nq.scheduler.namespace.v1\0"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] LOCK_DOMAIN = "nq.scheduler.lock.v1\0"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] SEPARATOR = new byte[] {0};

    /** 将相同 logical key 稳定映射为相同的两个 PostgreSQL int32。 */
    public PostgresAdvisoryLockKey map(SchedulerLockKey key) {
        Objects.requireNonNull(key, "key must not be null");
        int namespaceKey = firstInt(digest(NAMESPACE_DOMAIN, utf8(key.namespace())));
        int lockKey = firstInt(digest(LOCK_DOMAIN, utf8(key.namespace()), SEPARATOR, utf8(key.name())));
        return new PostgresAdvisoryLockKey(namespaceKey, lockKey);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] digest(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 must be available in every supported JVM", ex);
        }
    }

    private static int firstInt(byte[] digest) {
        return ByteBuffer.wrap(digest, 0, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();
    }
}
