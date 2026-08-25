package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority;
import com.guidinglight.nexusquant.livecontrol.domain.port.OperatorPilotAuthorityRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL operator pilot authority adapter；不读取 credential material。
 */
@Repository
public class JdbcOperatorPilotAuthorityRepository implements OperatorPilotAuthorityRepository {

    private static final String SELECT = """
            SELECT authority_id,owner_user_id,exchange_account_id,credential_reference_id,
                   instrument,side,order_type,max_notional,max_place_count,max_cancel_count,
                   transfer_allowed,withdraw_allowed,valid_from,expires_at,status,created_by,
                   created_at,canonical_digest
            FROM operator_pilot_authorities
            """;

    private final JdbcTemplate jdbc;

    public JdbcOperatorPilotAuthorityRepository(JdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public OperatorPilotAuthority createOrGet(OperatorPilotAuthority authority) {
        Optional<OperatorPilotAuthority> replay = lock(authority.id());
        if (replay.isPresent()) {
            if (replay.get().equals(authority)) {
                return replay.get();
            }
            throw rejected("OPERATOR_PILOT_AUTHORITY_IDEMPOTENCY_CONFLICT");
        }
        jdbc.update("""
                        INSERT INTO operator_pilot_authorities(
                            authority_id,owner_user_id,exchange_account_id,credential_reference_id,
                            instrument,side,order_type,max_notional,max_place_count,max_cancel_count,
                            transfer_allowed,withdraw_allowed,valid_from,expires_at,status,created_by,
                            created_at,canonical_digest,updated_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, authority.id(), authority.ownerUserId(), authority.exchangeAccountId(),
                authority.credentialReferenceId(), authority.instrument(), authority.side().name(),
                authority.orderType().name(), authority.maxNotional(), authority.maxPlaceCount(),
                authority.maxCancelCount(), authority.transferAllowed(), authority.withdrawAllowed(),
                Timestamp.from(authority.validFrom()), Timestamp.from(authority.expiresAt()),
                authority.status().name(), authority.createdBy(), Timestamp.from(authority.createdAt()),
                authority.canonicalDigest(), Timestamp.from(authority.createdAt()));
        return lock(authority.id()).orElseThrow();
    }

    @Override
    public Optional<OperatorPilotAuthority> find(UUID authorityId) {
        return first(jdbc.query(SELECT + " WHERE authority_id=?", JdbcOperatorPilotAuthorityRepository::map,
                authorityId));
    }

    @Override
    public Optional<OperatorPilotAuthority> lock(UUID authorityId) {
        return first(jdbc.query(SELECT + " WHERE authority_id=? FOR UPDATE",
                JdbcOperatorPilotAuthorityRepository::map, authorityId));
    }

    @Override
    public OperatorPilotAuthority close(
            UUID authorityId,
            OperatorPilotAuthority.Status status,
            Instant occurredAt
    ) {
        OperatorPilotAuthority current = lock(authorityId)
                .orElseThrow(() -> rejected("OPERATOR_PILOT_AUTHORITY_NOT_FOUND"));
        if (current.status() == status) {
            return current;
        }
        if (current.status() != OperatorPilotAuthority.Status.ACTIVE) {
            throw rejected("OPERATOR_PILOT_AUTHORITY_ALREADY_TERMINAL");
        }
        int updated = jdbc.update("""
                        UPDATE operator_pilot_authorities
                        SET status=?,closed_at=?,updated_at=?,version=version+1
                        WHERE authority_id=? AND status='ACTIVE'
                        """, status.name(), Timestamp.from(occurredAt), Timestamp.from(occurredAt),
                authorityId);
        if (updated != 1) {
            throw rejected("OPERATOR_PILOT_AUTHORITY_VERSION_CONFLICT");
        }
        return lock(authorityId).orElseThrow();
    }

    private static OperatorPilotAuthority map(ResultSet row, int ignored) throws SQLException {
        return new OperatorPilotAuthority(
                row.getObject("authority_id", UUID.class), row.getLong("owner_user_id"),
                row.getLong("exchange_account_id"), row.getLong("credential_reference_id"),
                row.getString("instrument"), OperatorPilotAuthority.Side.valueOf(row.getString("side")),
                OperatorPilotAuthority.OrderType.valueOf(row.getString("order_type")),
                row.getBigDecimal("max_notional"), row.getInt("max_place_count"),
                row.getInt("max_cancel_count"), row.getBoolean("transfer_allowed"),
                row.getBoolean("withdraw_allowed"), row.getTimestamp("valid_from").toInstant(),
                row.getTimestamp("expires_at").toInstant(),
                OperatorPilotAuthority.Status.valueOf(row.getString("status")),
                row.getLong("created_by"), row.getTimestamp("created_at").toInstant(),
                row.getString("canonical_digest"));
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private static LiveControlException rejected(String code) {
        return new LiveControlException(code, "operator pilot authority persistence rejected");
    }
}
