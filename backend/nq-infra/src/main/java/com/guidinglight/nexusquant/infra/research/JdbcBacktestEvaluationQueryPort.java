package com.guidinglight.nexusquant.infra.research;

import com.guidinglight.nexusquant.research.model.BacktestEvaluationView;
import com.guidinglight.nexusquant.research.port.BacktestEvaluationQueryPort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcBacktestEvaluationQueryPort 读取研究域 publish 所需的最小评估投影。
 */
@Repository
public class JdbcBacktestEvaluationQueryPort implements BacktestEvaluationQueryPort {

    private static final RowMapper<BacktestEvaluationView> ROW_MAPPER = JdbcBacktestEvaluationQueryPort::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcBacktestEvaluationQueryPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<BacktestEvaluationView> findByBacktestRunId(String backtestRunId) {
        List<BacktestEvaluationView> rows = jdbcTemplate.query(
                """
                        SELECT eval_report_id, backtest_run_id, evaluation_status, evaluated_at, final_equity, net_pnl,
                               total_return_rate, max_drawdown_rate, win_rate, sharpe_ratio, trade_count, order_count,
                               report_json::text AS report_json, failure_code, failure_message
                        FROM backtest_eval_reports
                        WHERE backtest_run_id = ?
                        """,
                ROW_MAPPER,
                backtestRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private static BacktestEvaluationView mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new BacktestEvaluationView(
                resultSet.getString("eval_report_id"),
                resultSet.getString("backtest_run_id"),
                resultSet.getString("evaluation_status"),
                resultSet.getTimestamp("evaluated_at") == null ? null : resultSet.getTimestamp("evaluated_at").toInstant(),
                resultSet.getBigDecimal("final_equity"),
                resultSet.getBigDecimal("net_pnl"),
                resultSet.getBigDecimal("total_return_rate"),
                resultSet.getBigDecimal("max_drawdown_rate"),
                resultSet.getBigDecimal("win_rate"),
                resultSet.getBigDecimal("sharpe_ratio"),
                resultSet.getObject("trade_count", Integer.class),
                resultSet.getObject("order_count", Integer.class),
                resultSet.getString("report_json"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message")
        );
    }
}
