package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;

import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 使用现有 users/roles/user_roles 事实执行 fail-closed 实时 RBAC。 */
@Repository
public class JdbcLiveControlAuthorization implements LiveControlAuthorizationPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLiveControlAuthorization(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public boolean lockAndCheckRole(long actorId, String requiredRole) {
        if (actorId <= 0 || requiredRole == null || requiredRole.isBlank()) {
            return false;
        }
        List<Integer> matches = jdbcTemplate.query("""
                SELECT 1
                FROM users actor
                JOIN user_roles grant_fact ON grant_fact.user_id = actor.id
                JOIN roles role_fact ON role_fact.id = grant_fact.role_id
                WHERE actor.id = ?
                  AND actor.enabled = TRUE
                  AND role_fact.role_code = ?
                FOR SHARE OF actor, grant_fact, role_fact
                """, (resultSet, rowNumber) -> resultSet.getInt(1), actorId, requiredRole);
        return matches.size() == 1;
    }
}
