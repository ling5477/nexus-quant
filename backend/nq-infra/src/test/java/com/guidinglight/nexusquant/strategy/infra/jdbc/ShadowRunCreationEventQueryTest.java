package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.port.AdmissionMutationCoordinator;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;

/** 验证创建恢复采用有界 SQL 而不扫描全事件；真实 SQL 结果另由隔离 PostgreSQL 验证。 */
class ShadowRunCreationEventQueryTest {

    @Test
    void limitsOriginalCreatedAuditToTwoAndDoesNotWrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID runId = UUID.randomUUID();
        repository(jdbc).listCreationEvents(runId);
        verify(jdbc)
                .query(
                        argThat(
                                sql ->
                                        sql.contains("event_type = 'CREATED'")
                                                && sql.contains("WHERE shadow_run_id = ?")
                                                && sql.endsWith("LIMIT 2")),
                        any(RowMapper.class),
                        eq(runId));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void requiresRunIdentityBeforeReading() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertThrows(NullPointerException.class, () -> repository(jdbc).listCreationEvents(null));
        verifyNoInteractions(jdbc);
    }

    private static JdbcShadowRunFactRepository repository(JdbcTemplate jdbc) {
        return new JdbcShadowRunFactRepository(
                jdbc,
                new ObjectMapper(),
                mock(JdbcShadowRunIllegalTransitionAuditWriter.class),
                mock(AdmissionMutationCoordinator.class));
    }
}
