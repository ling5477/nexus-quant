package com.guidinglight.nexusquant.auth.infra.jdbc;

import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.AuthSession;
import com.guidinglight.nexusquant.auth.domain.tenant.Tenant;
import com.guidinglight.nexusquant.auth.domain.tenant.TenantMembership;
import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.common.tenant.TenantKind;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcAuthUserRepository 提供 users/roles/user_roles 的最小 JDBC 实现。
 */
public class JdbcAuthUserRepository implements AuthUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AuthUserProfile> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        List<AuthUserProfile> rows = jdbcTemplate.query(
                """
                        SELECT u.id,
                               u.username,
                               u.password_hash,
                               u.enabled,
                               u.must_change_password, u.password_changed_at, u.auth_version,
                               ARRAY_REMOVE(ARRAY_AGG(r.role_code ORDER BY r.role_code), NULL) AS role_codes
                        FROM users u
                        LEFT JOIN user_roles ur ON ur.user_id = u.id
                        LEFT JOIN roles r ON r.id = ur.role_id
                        WHERE u.username = ?
                        GROUP BY u.id, u.username, u.password_hash, u.enabled
                        """,
                (resultSet, rowNum) -> new AuthUserProfile(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        readRoles(resultSet.getArray("role_codes")),
                        resultSet.getBoolean("enabled"),
                        resultSet.getBoolean("must_change_password"),
                        resultSet.getTimestamp("password_changed_at") == null ? null
                                : resultSet.getTimestamp("password_changed_at").toInstant(),
                        resultSet.getLong("auth_version")
                ),
                username.trim()
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<TenantMembership> findTenantMemberships(long userId) {
        if (userId <= 0) return List.of();
        return jdbcTemplate.query("""
                SELECT t.tenant_id,t.tenant_code,t.tenant_name,t.tenant_kind,t.status AS tenant_status,
                       t.created_at AS tenant_created,t.updated_at AS tenant_updated,
                       m.user_id,m.membership_role,m.status,m.created_at,m.updated_at
                FROM tenant_memberships m JOIN tenants t ON t.tenant_id=m.tenant_id
                WHERE m.user_id=? ORDER BY t.tenant_id LIMIT 2
                """, (rs, row) -> new TenantMembership(
                        new Tenant(new TenantId(rs.getObject("tenant_id", UUID.class)),
                                rs.getString("tenant_code"), rs.getString("tenant_name"),
                                TenantKind.valueOf(rs.getString("tenant_kind")),
                                Tenant.Status.valueOf(rs.getString("tenant_status")),
                                rs.getTimestamp("tenant_created").toInstant(), rs.getTimestamp("tenant_updated").toInstant()),
                        rs.getLong("user_id"), TenantMembership.Role.valueOf(rs.getString("membership_role")),
                        TenantMembership.Status.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), userId);
    }

    @Override
    public boolean hasAdminUser() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM users u
                        JOIN user_roles ur ON ur.user_id = u.id
                        JOIN roles r ON r.id = ur.role_id
                        WHERE u.enabled = TRUE AND r.role_code IN ('ADMIN','PLATFORM_ADMIN')
                        """,
                Integer.class
        );
        return count != null && count > 0;
    }

    @Override
    public void upsertSeedUser(SeedUserCommand command) {
        Long userId = jdbcTemplate.query(
                "SELECT id FROM users WHERE username = ?",
                (resultSet, rowNum) -> resultSet.getLong("id"),
                command.username()
        ).stream().findFirst().orElse(null);
        Instant now = Instant.now();
        if (userId == null) {
            jdbcTemplate.update(
                    """
                            INSERT INTO users (username, password_hash, enabled, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    command.username(),
                    command.passwordHash(),
                    command.enabled(),
                    Timestamp.from(now),
                    Timestamp.from(now)
            );
            userId = jdbcTemplate.query(
                    "SELECT id FROM users WHERE username = ?",
                    (resultSet, rowNum) -> resultSet.getLong("id"),
                    command.username()
            ).stream().findFirst().orElseThrow();
        } else {
            jdbcTemplate.update(
                    """
                            UPDATE users
                            SET password_hash = ?, enabled = ?, updated_at = ?
                            WHERE id = ?
                    """,
                    command.passwordHash(),
                    command.enabled(),
                    Timestamp.from(now),
                    userId
            );
        }
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        for (String roleCode : command.roles()) {
            Long roleId = jdbcTemplate.query(
                    "SELECT id FROM roles WHERE role_code = ?",
                    (resultSet, rowNum) -> resultSet.getLong("id"),
                    roleCode
            ).stream().findFirst().orElseThrow();
            jdbcTemplate.update(
                    """
                            INSERT INTO user_roles (user_id, role_id, granted_at)
                            VALUES (?, ?, ?)
                            ON CONFLICT (user_id, role_id) DO NOTHING
                    """,
                    userId,
                    roleId,
                    Timestamp.from(now)
            );
        }
    }

    @Override
    @Transactional
    public boolean createInitialAdminIfAbsent(SeedUserCommand command) {
        // 启动时串行化空库检查与创建；已有任意正式用户都不能被默认凭据覆盖。
        jdbcTemplate.execute("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE");
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM users)", Boolean.class))) {
            return false;
        }
        upsertSeedUser(command);
        jdbcTemplate.update("UPDATE users SET must_change_password = TRUE WHERE username = ?", command.username());
        return true;
    }

    @Override
    public Optional<AuthUserProfile> findByUsernameForUpdate(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        // 锁定与读取必须使用同一规范化身份，避免带空格登录绕过会话串行化。
        String normalized = username.trim();
        jdbcTemplate.queryForList("SELECT id FROM users WHERE username = ? FOR UPDATE", Long.class, normalized);
        return findByUsername(normalized);
    }

    @Override
    public boolean changePassword(Long userId, long authVersion, String passwordHash) {
        return jdbcTemplate.update("""
                UPDATE users SET password_hash = ?, must_change_password = FALSE,
                    password_changed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP,
                    auth_version = auth_version + 1
                WHERE id = ? AND auth_version = ? AND enabled = TRUE
                """, passwordHash, userId, authVersion) == 1;
    }

    @Override
    public void createSession(AuthSession session, Instant idleCutoff) {
        // 调用方持有用户行锁；清理仅限该用户，保留九条后新增，避免会话记录无界增长。
        jdbcTemplate.update("DELETE FROM auth_sessions WHERE user_id = ? AND (revoked_at IS NOT NULL OR expires_at <= ? OR last_active_at <= ?)",
                session.userId(), Timestamp.from(session.createdAt()), Timestamp.from(idleCutoff));
        jdbcTemplate.update("""
                DELETE FROM auth_sessions WHERE session_id IN (
                    SELECT session_id FROM auth_sessions WHERE user_id = ?
                    ORDER BY created_at DESC, session_id OFFSET 9)
                """, session.userId());
        jdbcTemplate.update("""
                INSERT INTO auth_sessions(session_id, user_id, auth_version, created_at, last_active_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, session.sessionId(), session.userId(), session.authVersion(), Timestamp.from(session.createdAt()),
                Timestamp.from(session.lastActiveAt()), Timestamp.from(session.expiresAt()));
    }

    @Override
    public boolean isSessionActive(String id, String username, long version, Instant now, Instant idleCutoff) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM auth_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.session_id = ? AND u.username = ? AND s.auth_version = ?
                AND u.auth_version = s.auth_version AND u.enabled = TRUE
                AND s.revoked_at IS NULL AND s.expires_at > ? AND s.last_active_at > ?)
                """, Boolean.class, id, username, version, Timestamp.from(now), Timestamp.from(idleCutoff)));
    }

    @Override
    public Optional<AuthSession> renewSession(String id, long userId, long version, Instant now, Instant idleCutoff) {
        return jdbcTemplate.query("""
                UPDATE auth_sessions SET last_active_at = GREATEST(last_active_at, ?)
                WHERE session_id = ? AND user_id = ? AND auth_version = ?
                AND revoked_at IS NULL AND expires_at > ? AND last_active_at > ?
                RETURNING session_id, user_id, auth_version, created_at, last_active_at, expires_at
                """, (rs, row) -> new AuthSession(rs.getString("session_id"), rs.getLong("user_id"),
                rs.getLong("auth_version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_active_at").toInstant(), rs.getTimestamp("expires_at").toInstant()),
                Timestamp.from(now), id, userId, version, Timestamp.from(now), Timestamp.from(idleCutoff))
                .stream().findFirst();
    }

    @Override
    public void revokeSession(String id, long userId) {
        jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE session_id = ? AND user_id = ? AND revoked_at IS NULL", id, userId);
    }

    @Override
    public void revokeUserTokens(long userId, long version) {
        jdbcTemplate.update("UPDATE users SET auth_version = auth_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND auth_version = ?", userId, version);
    }

    private List<String> readRoles(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object arrayValue = sqlArray.getArray();
        if (!(arrayValue instanceof Object[] rawValues)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (Object value : rawValues) {
            if (value != null) {
                roles.add(String.valueOf(value));
            }
        }
        return List.copyOf(roles);
    }
}


