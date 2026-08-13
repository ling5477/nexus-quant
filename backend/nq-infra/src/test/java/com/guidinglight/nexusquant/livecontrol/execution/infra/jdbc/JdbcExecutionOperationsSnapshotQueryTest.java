package com.guidinglight.nexusquant.livecontrol.execution.infra.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcExecutionOperationsSnapshotQueryTest {

    @Test
    void mapsOnlySanitizedOperationalFacts() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("observed_at", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.of(2026, 8, 13, 12, 0, 0, 0, ZoneOffset.UTC));
        when(row.getString("kill_state")).thenReturn("ENGAGED");
        when(row.getString("session_id")).thenReturn("session-1");
        when(row.getString("session_state")).thenReturn("RECONCILIATION_BLOCKED");
        when(row.getString("approval_state")).thenReturn("APPROVED");
        when(row.getString("risk_digest")).thenReturn("c".repeat(64));
        when(row.getString("worker_health")).thenReturn("OBSERVED_FROM_INTENT");
        when(row.getString("worker_identity")).thenReturn("worker-a");
        when(row.getString("release_identity")).thenReturn("NOT_RECORDED");
        when(row.getString("release_digest")).thenReturn("NOT_RECORDED");
        when(row.getString("intent_id")).thenReturn("intent-1");
        when(row.getString("intent_state")).thenReturn("UNKNOWN");
        when(row.getString("receipt_state")).thenReturn("TRANSPORT_ERROR");
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenAnswer(invocation -> invocation.<org.springframework.jdbc.core.RowMapper<?>>getArgument(1)
                        .mapRow(row, 0));

        var snapshot = new JdbcExecutionOperationsSnapshotQuery(jdbc).currentSnapshot();

        assertEquals("ENGAGED", snapshot.killState());
        assertEquals("RECONCILIATION_BLOCKED", snapshot.sessionState());
        assertEquals("UNKNOWN", snapshot.intentState());
        assertEquals("NOT_RECORDED", snapshot.releaseIdentity());
    }
}
