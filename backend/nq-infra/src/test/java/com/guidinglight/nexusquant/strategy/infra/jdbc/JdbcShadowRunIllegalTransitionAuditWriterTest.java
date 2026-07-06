package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateTransitionException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class JdbcShadowRunIllegalTransitionAuditWriterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");

    @Test
    void shouldUseRequiresNewTransactionAndWriteSafeIllegalTransitionEvent() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        JdbcShadowRunIllegalTransitionAuditWriter writer = writer(jdbcTemplate, transactionManager);
        ShadowRun run = run(ShadowRunStatus.COMPLETED);
        ShadowRunStateTransitionException transitionException = new ShadowRunStateTransitionException(
                ShadowRunStatus.COMPLETED,
                ShadowRunStatus.RUNNING,
                "SHADOW_RUN_TERMINAL_STATE_LOCKED"
        );

        writer.writeIllegalTransitionAttempt(
                run,
                ShadowRunStatus.RUNNING,
                transitionException,
                "completed run cannot restart",
                "req-audit-1",
                "trace-audit-1"
        );

        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, writer.propagationBehavior());
        assertEquals(List.of(TransactionDefinition.PROPAGATION_REQUIRES_NEW),
                transactionManager.propagationBehaviors);
        assertEquals(1, transactionManager.commits);
        assertEquals(0, transactionManager.rollbacks);
        assertTrue(jdbcTemplate.updateSql.contains("INSERT INTO shadow_run_events"));
        assertEquals(ShadowRunEventType.ILLEGAL_STATE_TRANSITION_ATTEMPT.name(), jdbcTemplate.updateArgs[2]);
        assertEquals(ShadowRunStatus.COMPLETED.name(), jdbcTemplate.updateArgs[3]);
        assertEquals(ShadowRunStatus.RUNNING.name(), jdbcTemplate.updateArgs[4]);
        assertEquals("SHADOW_RUN_TERMINAL_STATE_LOCKED", jdbcTemplate.updateArgs[5]);
        assertEquals("req-audit-1", jdbcTemplate.updateArgs[8]);
        assertEquals("trace-audit-1", jdbcTemplate.updateArgs[9]);

        String metadata = (String) jdbcTemplate.updateArgs[7];
        assertTrue(metadata.contains("rejectedTransition"));
        assertFalse(metadata.contains("api" + "Key"));
        assertFalse(metadata.contains("credential" + "Material"));
        assertFalse(metadata.contains("raw" + "Private" + "Request"));
        assertFalse(metadata.contains("real" + "Order" + "Id"));
        assertFalse(metadata.contains("real" + "Account" + "Balance"));
    }

    @Test
    void shouldPropagateAuditWriteFailureAndRollbackRequiresNewTransaction() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("audit insert failed");
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.failure = failure;
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        JdbcShadowRunIllegalTransitionAuditWriter writer = writer(jdbcTemplate, transactionManager);
        ShadowRun run = run(ShadowRunStatus.COMPLETED);
        ShadowRunStateTransitionException transitionException = new ShadowRunStateTransitionException(
                ShadowRunStatus.COMPLETED,
                ShadowRunStatus.RUNNING,
                "SHADOW_RUN_TERMINAL_STATE_LOCKED"
        );

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> writer.writeIllegalTransitionAttempt(
                        run,
                        ShadowRunStatus.RUNNING,
                        transitionException,
                        "completed run cannot restart",
                        "req-audit-2",
                        "trace-audit-2"
                )
        );

        assertSame(failure, thrown);
        assertEquals(List.of(TransactionDefinition.PROPAGATION_REQUIRES_NEW),
                transactionManager.propagationBehaviors);
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    private JdbcShadowRunIllegalTransitionAuditWriter writer(
            RecordingJdbcTemplate jdbcTemplate,
            RecordingTransactionManager transactionManager
    ) {
        return new JdbcShadowRunIllegalTransitionAuditWriter(
                jdbcTemplate,
                OBJECT_MAPPER,
                transactionManager
        );
    }

    private ShadowRun run(ShadowRunStatus status) {
        UUID runId = UUID.randomUUID();
        return new ShadowRun(
                runId,
                "sv-audit-1",
                UUID.randomUUID(),
                null,
                null,
                null,
                status,
                NOW,
                NOW.plusSeconds(60),
                JsonNodeFactory.instance.objectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "req-audit",
                "shadow-audit-idem-" + runId,
                "trace-audit",
                JsonNodeFactory.instance.arrayNode().add("manual-review"),
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                0,
                NOW,
                NOW,
                null,
                null,
                null
        );
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private String updateSql;
        private Object[] updateArgs;
        private RuntimeException failure;

        @Override
        public int update(String sql, Object... args) {
            updateSql = sql;
            updateArgs = args;
            if (failure != null) {
                throw failure;
            }
            return 1;
        }
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {

        private final List<Integer> propagationBehaviors = new ArrayList<>();
        private int commits;
        private int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            propagationBehaviors.add(definition.getPropagationBehavior());
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }
}
