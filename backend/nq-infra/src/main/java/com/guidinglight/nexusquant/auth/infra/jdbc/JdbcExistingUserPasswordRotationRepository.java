package com.guidinglight.nexusquant.auth.infra.jdbc;

import com.guidinglight.nexusquant.auth.domain.PasswordRotationException;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationException.Reason;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationTarget;
import com.guidinglight.nexusquant.auth.domain.port.ExistingUserPasswordRotationRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 使用独立短事务维护已有用户密码，不触发用户 seed 或关联表写入。 */
public final class JdbcExistingUserPasswordRotationRepository implements ExistingUserPasswordRotationRepository {
    private final DataSource dataSource;

    public JdbcExistingUserPasswordRotationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<PasswordRotationTarget> findTarget(long exactUserId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, username, enabled, password_hash FROM users WHERE id = ?")) {
            statement.setQueryTimeout(15);
            statement.setMaxRows(2);
            statement.setLong(1, exactUserId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                PasswordRotationTarget target = new PasswordRotationTarget(rows.getLong("id"),
                        rows.getString("username"), rows.getBoolean("enabled"), rows.getString("password_hash"));
                if (rows.next()) {
                    throw new PasswordRotationException(Reason.UNEXPECTED_AFFECTED_ROWS);
                }
                return Optional.of(target);
            }
        } catch (SQLException exception) {
            throw new PasswordRotationException(Reason.PASSWORD_STORAGE_FAILURE, exception);
        }
    }

    @Override
    public void updatePasswordHash(long exactUserId, String expectedUsername, String expectedCurrentHash,
                                   String newPasswordHash, Instant updatedAt) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE users
                    SET password_hash = ?, updated_at = ?
                    WHERE id = ? AND username = ? AND enabled = TRUE AND password_hash = ?
                    """)) {
                statement.setQueryTimeout(15);
                statement.setString(1, newPasswordHash);
                statement.setTimestamp(2, Timestamp.from(updatedAt));
                statement.setLong(3, exactUserId);
                statement.setString(4, expectedUsername);
                statement.setString(5, expectedCurrentHash);
                int affected = statement.executeUpdate();
                if (affected != 1) {
                    throw new PasswordRotationException(affected == 0
                            ? Reason.STALE_AUTH_IDENTITY : Reason.UNEXPECTED_AFFECTED_ROWS);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PasswordRotationException(Reason.PASSWORD_STORAGE_FAILURE, exception);
        }
    }
}
