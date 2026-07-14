package com.guidinglight.nexusquant.risk.infra.jdbc;

import com.guidinglight.nexusquant.risk.service.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchState;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchVersionConflictException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kill switch current state 与 append-only event 的 PostgreSQL adapter。
 *
 * <p>读取只访问两张 kill-switch 表；engage 在短事务内执行 row lock、optimistic version update
 * 和 event append，不访问 credential、交易所、订单、账户、资金、position 或 ledger。</p>
 */
@Repository
public class JdbcKillSwitchStateRepository implements KillSwitchStateRepository {

    private static final String SELECT_STATE = """
            SELECT scope, status, version, reason_code, source, updated_at, updated_by, trace_id
            FROM kill_switch_states
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKillSwitchStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return first(jdbcTemplate.query(
                SELECT_STATE + " WHERE scope = ?",
                stateRowMapper(),
                scope.name()
        ));
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public KillSwitchState engage(KillSwitchEngageCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        KillSwitchState current = first(jdbcTemplate.query(
                SELECT_STATE + " WHERE scope = ? FOR UPDATE",
                stateRowMapper(),
                command.scope().name()
        )).orElseThrow(() -> new IllegalStateException("kill switch state is missing"));

        if (current.version() != command.expectedVersion()) {
            throw conflict(command);
        }
        if (current.status() == KillSwitchStatus.ENGAGED) {
            return current;
        }

        long nextVersion = current.version() + 1;
        int updated = jdbcTemplate.update(
                """
                        UPDATE kill_switch_states
                        SET status = 'ENGAGED', version = ?, reason_code = ?, source = ?,
                            updated_at = ?, updated_by = ?, trace_id = ?
                        WHERE scope = ? AND version = ? AND status = 'DISENGAGED'
                        """,
                nextVersion,
                command.reasonCode(),
                command.source(),
                Timestamp.from(command.occurredAt()),
                command.updatedBy(),
                command.traceId(),
                command.scope().name(),
                current.version()
        );
        if (updated != 1) {
            throw conflict(command);
        }

        jdbcTemplate.update(
                """
                        INSERT INTO kill_switch_events (
                            id, scope, from_status, to_status, state_version, reason_code,
                            source, actor_id, trace_id, occurred_at
                        ) VALUES (?, ?, ?, 'ENGAGED', ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                command.scope().name(),
                current.status().name(),
                nextVersion,
                command.reasonCode(),
                command.source(),
                command.updatedBy(),
                command.traceId(),
                Timestamp.from(command.occurredAt())
        );

        return new KillSwitchState(
                command.scope(),
                KillSwitchStatus.ENGAGED,
                nextVersion,
                command.reasonCode(),
                command.source(),
                command.occurredAt(),
                command.updatedBy(),
                command.traceId()
        );
    }

    private static KillSwitchVersionConflictException conflict(KillSwitchEngageCommand command) {
        return new KillSwitchVersionConflictException(
                "kill switch version does not match expectedVersion for scope " + command.scope()
        );
    }

    private static RowMapper<KillSwitchState> stateRowMapper() {
        return JdbcKillSwitchStateRepository::mapState;
    }

    private static KillSwitchState mapState(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new KillSwitchState(
                KillSwitchScope.valueOf(resultSet.getString("scope")),
                KillSwitchStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getString("reason_code"),
                resultSet.getString("source"),
                updatedAt == null ? null : updatedAt.toInstant(),
                resultSet.getString("updated_by"),
                resultSet.getString("trace_id")
        );
    }

    private static <T> Optional<T> first(List<T> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }
}
