package com.guidinglight.nexusquant.auth.infra.jdbc;

import com.guidinglight.nexusquant.auth.domain.PasswordRotationException;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcExistingUserPasswordRotationRepositoryTest {
    @Test
    void unexpectedAffectedRowsRollbackAndNeverCommit() throws Exception {
        for (int affected : new int[]{0, -1, 2}) {
            DataSource source = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeUpdate()).thenReturn(affected);
            PasswordRotationException error = assertThrows(PasswordRotationException.class,
                    () -> new JdbcExistingUserPasswordRotationRepository(source).updatePasswordHash(
                            2, "synthetic-owner", "synthetic-old-hash", "synthetic-new-hash", Instant.EPOCH));
            assertEquals(affected == 0 ? PasswordRotationException.Reason.STALE_AUTH_IDENTITY
                    : PasswordRotationException.Reason.UNEXPECTED_AFFECTED_ROWS, error.reason());
            verify(connection).rollback();
            verify(connection, never()).commit();
            verify(connection).close();
            verify(statement).close();
        }
    }

    @Test
    void exactUpdateBindsBothIdentityAndOldHashAndCommitsOnce() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        new JdbcExistingUserPasswordRotationRepository(source).updatePasswordHash(
                2, "synthetic-owner", "synthetic-old-hash", "synthetic-new-hash", Instant.EPOCH);
        verify(connection).prepareStatement("""
                UPDATE users
                SET password_hash = ?, updated_at = ?
                WHERE id = ? AND username = ? AND enabled = TRUE AND password_hash = ?
                """);
        verify(statement).setString(1, "synthetic-new-hash");
        verify(statement).setTimestamp(2, Timestamp.from(Instant.EPOCH));
        verify(statement).setLong(3, 2);
        verify(statement).setString(4, "synthetic-owner");
        verify(statement).setString(5, "synthetic-old-hash");
        verify(statement).setQueryTimeout(15);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void sqlFailureRollsBackAndPreservesCauseWithoutReplacingBusinessIdentity() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenThrow(new SQLException("synthetic failure"));
        PasswordRotationException error = assertThrows(PasswordRotationException.class,
                () -> new JdbcExistingUserPasswordRotationRepository(source).updatePasswordHash(
                        2, "synthetic-owner", "synthetic-old-hash", "synthetic-new-hash", Instant.EPOCH));
        assertEquals(PasswordRotationException.Reason.PASSWORD_STORAGE_FAILURE, error.reason());
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
