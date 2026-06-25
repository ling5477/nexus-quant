package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReportStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperRunDailyReportRepository implements PaperRunDailyReportRepository {

    private static final RowMapper<PaperRunDailyReport> ROW_MAPPER = JdbcPaperRunDailyReportRepository::mapRow;
    private static final String BASE_SELECT = """
            SELECT report_id, paper_run_id, report_date, status, total_equity, daily_pnl,
                   daily_return, max_drawdown, order_count, trade_count, alert_count,
                   risk_reject_count, report_json::text AS report_json, generated_at, created_at
            FROM paper_run_daily_reports
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public JdbcPaperRunDailyReportRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    JdbcPaperRunDailyReportRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public void upsert(PaperRunDailyReport report) {
        jdbcTemplate.update("""
                INSERT INTO paper_run_daily_reports (
                    report_id, paper_run_id, report_date, status, total_equity, daily_pnl,
                    daily_return, max_drawdown, order_count, trade_count, alert_count,
                    risk_reject_count, report_json, generated_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?)
                ON CONFLICT (paper_run_id, report_date) DO UPDATE SET
                    status = EXCLUDED.status,
                    total_equity = EXCLUDED.total_equity,
                    daily_pnl = EXCLUDED.daily_pnl,
                    daily_return = EXCLUDED.daily_return,
                    max_drawdown = EXCLUDED.max_drawdown,
                    order_count = EXCLUDED.order_count,
                    trade_count = EXCLUDED.trade_count,
                    alert_count = EXCLUDED.alert_count,
                    risk_reject_count = EXCLUDED.risk_reject_count,
                    report_json = EXCLUDED.report_json,
                    generated_at = EXCLUDED.generated_at
                """,
                report.reportId(), report.paperRunId(), Date.valueOf(report.reportDate()),
                report.status().name(), report.totalEquity(), report.dailyPnl(),
                report.dailyReturn(), report.maxDrawdown(), report.orderCount(),
                report.tradeCount(), report.alertCount(), report.riskRejectCount(),
                report.reportJson(), Timestamp.from(report.generatedAt()),
                Timestamp.from(report.createdAt()));
    }

    @Override
    public Optional<PaperRunDailyReport> findById(String reportId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    BASE_SELECT + " WHERE report_id = ?", ROW_MAPPER, reportId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaperRunDailyReport> findByRunIdAndDate(String paperRunId, LocalDate reportDate) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    BASE_SELECT + " WHERE paper_run_id = ? AND report_date = ?",
                    ROW_MAPPER, paperRunId, Date.valueOf(reportDate)));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<PaperRunDailyReport> listByRunId(String paperRunId) {
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE paper_run_id = ? ORDER BY report_date DESC",
                ROW_MAPPER, paperRunId);
    }

    @Override
    public Map<String, List<PaperRunDailyReport>> listByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        // 去重后以命名参数绑定 IN 列表（参数化，杜绝 SQL 拼接注入）；按 report_date DESC 与单 run 口径一致。
        MapSqlParameterSource params = new MapSqlParameterSource(
                "runIds", new ArrayList<>(new LinkedHashSet<>(runIds)));
        List<PaperRunDailyReport> rows = namedParameterJdbcTemplate.query(
                BASE_SELECT + " WHERE paper_run_id IN (:runIds) ORDER BY report_date DESC",
                params, ROW_MAPPER);
        // groupingBy 保留全局有序结果在各分组内的相对顺序，单 run 列表口径仍为 report_date DESC。
        return rows.stream().collect(Collectors.groupingBy(
                PaperRunDailyReport::paperRunId, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    public int countByRunIdAndDateRange(String paperRunId, java.time.Instant start, java.time.Instant end) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM paper_run_daily_reports
                WHERE paper_run_id = ? AND created_at >= ? AND created_at < ?
                """, Integer.class, paperRunId, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end));
        return count != null ? count : 0;
    }

    private static PaperRunDailyReport mapRow(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal totalEquity = rs.getBigDecimal("total_equity");
        BigDecimal dailyPnl = rs.getBigDecimal("daily_pnl");
        BigDecimal dailyReturn = rs.getBigDecimal("daily_return");
        BigDecimal maxDrawdown = rs.getBigDecimal("max_drawdown");
        return new PaperRunDailyReport(
                rs.getString("report_id"),
                rs.getString("paper_run_id"),
                rs.getDate("report_date").toLocalDate(),
                PaperRunDailyReportStatus.valueOf(rs.getString("status")),
                totalEquity, dailyPnl, dailyReturn, maxDrawdown,
                rs.getInt("order_count"), rs.getInt("trade_count"),
                rs.getInt("alert_count"), rs.getInt("risk_reject_count"),
                rs.getString("report_json"),
                rs.getTimestamp("generated_at").toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
