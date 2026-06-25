package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReportStatus;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckSeverity;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Paper 组合看板批量读取端口的 JDBC SQL 形状单测。
 *
 * <p>Why：用 RecordingNamedParameterJdbcTemplate 校验批量读取的 SQL 形状与安全约束，
 * 不依赖真实 PostgreSQL（与 {@code JdbcOrderRepositoryTest} 同思路）。重点验证：
 * 1) 空 runIds 短路返回空结果，不触发任何查询；
 * 2) 非空 runIds 使用参数化 {@code IN (:runIds)} 绑定（杜绝 SQL 拼接注入）；
 * 3) 列表端口按 runId 正确分组，计数端口按 runId 正确归集。
 */
class JdbcPaperPortfolioBatchReadTest {

    private static final JdbcTemplate UNUSED_JDBC = new JdbcTemplate();

    @Test
    void equityListByRunIdsShouldShortCircuitEmptyAndGroupMultiRunIds() {
        RecordingNamedParameterJdbcTemplate named = new RecordingNamedParameterJdbcTemplate();
        JdbcEquityCurveSnapshotRepository repo = new JdbcEquityCurveSnapshotRepository(UNUSED_JDBC, named);

        assertTrue(repo.listByRunIds(List.of()).isEmpty());
        assertEquals(0, named.queryCallCount, "空 runIds 不应触发查询");

        named.queryResults = List.of(
                equity("eq-a1", "run-a"), equity("eq-a2", "run-a"), equity("eq-b1", "run-b"));
        Map<String, List<EquityCurveSnapshot>> byRun = repo.listByRunIds(List.of("run-a", "run-b"));

        assertEquals(1, named.queryCallCount, "应只批量查询一次");
        assertTrue(named.lastSql.contains("IN (:runIds)"), "应使用参数化 IN 绑定");
        assertBoundRunIds(named, "run-a", "run-b");
        assertEquals(2, byRun.size());
        assertEquals(2, byRun.get("run-a").size());
        assertEquals(1, byRun.get("run-b").size());
    }

    @Test
    void riskListByRunIdsShouldShortCircuitEmptyAndGroupMultiRunIds() {
        RecordingNamedParameterJdbcTemplate named = new RecordingNamedParameterJdbcTemplate();
        JdbcPaperRiskCheckResultRepository repo = new JdbcPaperRiskCheckResultRepository(UNUSED_JDBC, named);

        assertTrue(repo.listByRunIds(List.of()).isEmpty());
        assertEquals(0, named.queryCallCount);

        named.queryResults = List.of(risk("rk-a1", "run-a"), risk("rk-b1", "run-b"), risk("rk-b2", "run-b"));
        Map<String, List<PaperRiskCheckResult>> byRun = repo.listByRunIds(List.of("run-a", "run-b"));

        assertTrue(named.lastSql.contains("IN (:runIds)"));
        assertBoundRunIds(named, "run-a", "run-b");
        assertEquals(1, byRun.get("run-a").size());
        assertEquals(2, byRun.get("run-b").size());
    }

    @Test
    void dailyReportListByRunIdsShouldShortCircuitEmptyAndGroupMultiRunIds() {
        RecordingNamedParameterJdbcTemplate named = new RecordingNamedParameterJdbcTemplate();
        JdbcPaperRunDailyReportRepository repo = new JdbcPaperRunDailyReportRepository(UNUSED_JDBC, named);

        assertTrue(repo.listByRunIds(List.of()).isEmpty());
        assertEquals(0, named.queryCallCount);

        named.queryResults = List.of(report("rep-a1", "run-a"), report("rep-a2", "run-a"), report("rep-b1", "run-b"));
        Map<String, List<PaperRunDailyReport>> byRun = repo.listByRunIds(List.of("run-a", "run-b"));

        assertTrue(named.lastSql.contains("IN (:runIds)"));
        assertBoundRunIds(named, "run-a", "run-b");
        assertEquals(2, byRun.get("run-a").size());
        assertEquals(1, byRun.get("run-b").size());
    }

    @Test
    void alertCountOpenByRunIdsShouldShortCircuitEmptyAndAggregateGroupBy() {
        RecordingNamedParameterJdbcTemplate named = new RecordingNamedParameterJdbcTemplate();
        JdbcPaperRunAlertRepository repo = new JdbcPaperRunAlertRepository(UNUSED_JDBC, named);

        assertTrue(repo.countOpenByRunIds(List.of()).isEmpty());
        assertEquals(0, named.queryCallCount);

        named.queryResults = List.of(Map.entry("run-a", 2L), Map.entry("run-b", 1L));
        Map<String, Long> counts = repo.countOpenByRunIds(List.of("run-a", "run-b", "run-c"));

        assertTrue(named.lastSql.contains("IN (:runIds)"));
        assertTrue(named.lastSql.contains("status = 'OPEN'"), "只统计 OPEN 告警");
        assertTrue(named.lastSql.contains("GROUP BY paper_run_id"));
        assertBoundRunIds(named, "run-a", "run-b", "run-c");
        assertEquals(2L, counts.get("run-a"));
        assertEquals(1L, counts.get("run-b"));
        // run-c 无 OPEN 告警 → 不出现在结果中（调用方缺省 0）。
        assertTrue(!counts.containsKey("run-c"));
    }

    @Test
    void tradeCountByRunIdsShouldShortCircuitEmptyAndAggregateGroupBy() {
        RecordingNamedParameterJdbcTemplate named = new RecordingNamedParameterJdbcTemplate();
        JdbcPaperTradingTradeRepository repo = new JdbcPaperTradingTradeRepository(UNUSED_JDBC, named);

        assertTrue(repo.countByRunIds(List.of()).isEmpty());
        assertEquals(0, named.queryCallCount);

        named.queryResults = List.of(Map.entry("run-a", 3L), Map.entry("run-b", 5L));
        Map<String, Long> counts = repo.countByRunIds(List.of("run-a", "run-b"));

        assertTrue(named.lastSql.contains("IN (:runIds)"));
        assertTrue(named.lastSql.contains("GROUP BY paper_run_id"));
        assertBoundRunIds(named, "run-a", "run-b");
        assertEquals(3L, counts.get("run-a"));
        assertEquals(5L, counts.get("run-b"));
    }

    @Test
    void orderCountByRunIdsShouldShortCircuitEmptyAndAggregateGroupBy() {
        RecordingNamedParameterJdbcTemplate named = new RecordingNamedParameterJdbcTemplate();
        JdbcPaperTradingOrderRepository repo = new JdbcPaperTradingOrderRepository(UNUSED_JDBC, named);

        assertTrue(repo.countByRunIds(List.of()).isEmpty());
        assertEquals(0, named.queryCallCount);

        named.queryResults = List.of(Map.entry("run-a", 4L), Map.entry("run-b", 2L));
        Map<String, Long> counts = repo.countByRunIds(List.of("run-a", "run-b", "run-c"));

        assertEquals(1, named.queryCallCount, "应只批量查询一次");
        assertTrue(named.lastSql.contains("FROM paper_trading_orders"), "应查询订单表");
        assertTrue(named.lastSql.contains("IN (:runIds)"), "应使用参数化 IN 绑定");
        assertTrue(named.lastSql.contains("GROUP BY paper_run_id"));
        assertBoundRunIds(named, "run-a", "run-b", "run-c");
        assertEquals(4L, counts.get("run-a"));
        assertEquals(2L, counts.get("run-b"));
        // run-c 无订单 → 不出现在结果中（调用方缺省 0）。
        assertTrue(!counts.containsKey("run-c"));
    }

    // ---- helpers ----

    private static void assertBoundRunIds(RecordingNamedParameterJdbcTemplate named, String... expected) {
        Object bound = ((MapSqlParameterSource) named.lastParams).getValue("runIds");
        assertTrue(bound instanceof Collection, "runIds 必须作为绑定参数（Collection），不得字符串拼接");
        Collection<?> values = (Collection<?>) bound;
        assertEquals(expected.length, values.size());
        for (String runId : expected) {
            assertTrue(values.contains(runId), "缺少绑定 runId: " + runId);
        }
    }

    private static EquityCurveSnapshot equity(String id, String runId) {
        Instant t = Instant.parse("2026-06-01T00:00:00Z");
        return new EquityCurveSnapshot(id, runId, t, new BigDecimal("100"), new BigDecimal("100"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "TEST", t);
    }

    private static PaperRiskCheckResult risk(String id, String runId) {
        return new PaperRiskCheckResult(id, runId, "CHECK", RiskCheckStatus.PASSED, RiskCheckSeverity.LOW,
                "msg", "{}", "{}", Instant.parse("2026-06-01T00:00:00Z"));
    }

    private static PaperRunDailyReport report(String id, String runId) {
        Instant t = Instant.parse("2026-06-01T00:00:00Z");
        return new PaperRunDailyReport(id, runId, LocalDate.parse("2026-06-01"), PaperRunDailyReportStatus.GENERATED,
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0, "{}", t, t);
    }

    /** 记录 SQL / 绑定参数 / 调用次数并回放预置结果（不触发真实 ResultSet）。 */
    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {
        private List<?> queryResults = new ArrayList<>();
        private String lastSql;
        private SqlParameterSource lastParams;
        private int queryCallCount;

        private RecordingNamedParameterJdbcTemplate() {
            super(UNUSED_JDBC);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.queryCallCount++;
            this.lastSql = sql;
            this.lastParams = paramSource;
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }
    }
}
