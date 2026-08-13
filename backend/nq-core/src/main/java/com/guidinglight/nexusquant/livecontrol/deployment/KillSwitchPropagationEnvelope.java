package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 从唯一 durable kill-switch snapshot 派生的不可变 worker propagation envelope。
 *
 * <p>digest 绑定 scope/status/version/stateUpdatedAt/observedAt/source；它用于发现传输损坏和冲突，
 * 不是签名，也不能替代 send-time 对 durable kill source 的重新读取。</p>
 */
public record KillSwitchPropagationEnvelope(
        String schemaVersion,
        String scope,
        KillSwitchStatus status,
        long version,
        Instant stateUpdatedAt,
        Instant observedAt,
        String source,
        String digest
) {
    public static final String SCHEMA_VERSION = "nq-kill-propagation-v1";

    public KillSwitchPropagationEnvelope {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        scope = requireText(scope, "scope");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        source = requireText(source, "source");
        digest = requireText(digest, "digest");
    }

    public static KillSwitchPropagationEnvelope fromSnapshot(KillSwitchSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        String digest = digest(
                SCHEMA_VERSION,
                snapshot.scope().name(),
                snapshot.status(),
                snapshot.version(),
                snapshot.updatedAt(),
                snapshot.observedAt(),
                snapshot.source()
        );
        return new KillSwitchPropagationEnvelope(
                SCHEMA_VERSION,
                snapshot.scope().name(),
                snapshot.status(),
                snapshot.version(),
                snapshot.updatedAt(),
                snapshot.observedAt(),
                snapshot.source(),
                digest
        );
    }

    public boolean hasValidDigest() {
        String expected = digest(schemaVersion, scope, status, version, stateUpdatedAt, observedAt, source);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                digest.getBytes(StandardCharsets.US_ASCII));
    }

    private static String digest(
            String schema,
            String scope,
            KillSwitchStatus status,
            long version,
            Instant stateUpdatedAt,
            Instant observedAt,
            String source
    ) {
        String canonical = String.join("\u001f",
                schema,
                scope,
                status.name(),
                Long.toString(version),
                stateUpdatedAt == null ? "-" : stateUpdatedAt.toString(),
                observedAt.toString(),
                source
        );
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
