package com.guidinglight.nexusquant.infra.auth.jdbc;

import com.guidinglight.nexusquant.auth.application.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

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
                        resultSet.getBoolean("enabled")
                ),
                username.trim()
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public boolean hasAdminUser() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM users u
                        JOIN user_roles ur ON ur.user_id = u.id
                        JOIN roles r ON r.id = ur.role_id
                        WHERE u.enabled = TRUE AND r.role_code = 'ADMIN'
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
                    now,
                    now
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
                    now,
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
                    now
            );
        }
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
