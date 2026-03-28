package com.guidinglight.nexusquant.infra.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.core.model.StrategySchedule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcStrategyScheduleRepositoryTest {

    @Test
    void shouldFindListAndUpdateSchedule() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        StrategySchedule schedule = new StrategySchedule(
                "sch-1",
                "str-1",
                "CRON",
                "* * * * *",
                "UTC",
                true,
                "{}",
                "SCHEDULE_WINDOW",
                "BINANCE",
                1001L,
                "SIM",
                null,
                Instant.parse("2026-03-24T09:00:00Z"),
                Instant.parse("2026-03-24T09:00:00Z")
        );
        jdbcTemplate.queryResults = List.of(schedule);
        JdbcStrategyScheduleRepository repository = new JdbcStrategyScheduleRepository(jdbcTemplate);

        assertTrue(repository.findByScheduleJobId("sch-1").isPresent());
        assertEquals(1, repository.listByStrategyId("str-1").size());
        assertEquals(1, repository.listEnabledSchedules().size());
        assertTrue(repository.updateEnabled("sch-1", false, Instant.now()));
        assertTrue(repository.updateLastTriggeredAt("sch-1", Instant.now(), Instant.now()));
    }

    @Test
    void shouldReturnFalseWhenUpdateTargetMissing() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCount = 0;
        JdbcStrategyScheduleRepository repository = new JdbcStrategyScheduleRepository(jdbcTemplate);

        assertFalse(repository.updateEnabled("missing", false, Instant.now()));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private List<StrategySchedule> queryResults = new ArrayList<>();
        private int updateCount = 1;

        @Override
        public int update(String sql, Object... args) {
            return updateCount;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }
    }
}
