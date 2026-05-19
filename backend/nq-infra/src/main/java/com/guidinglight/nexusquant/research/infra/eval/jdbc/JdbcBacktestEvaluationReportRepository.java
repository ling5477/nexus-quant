package com.guidinglight.nexusquant.research.infra.eval.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationStatus;
import com.guidinglight.nexusquant.research.domain.eval.port.BacktestEvaluationReportRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcBacktestEvaluationReportRepository 是 backtest_eval_reports 表的 JDBC 实现。
 */
@Repository
public class JdbcBacktestEvaluationReportRepository implements BacktestEvaluationReportRepository {

    private static final RowMapper<BacktestEvaluationReport> ROW_MAPPER =
            JdbcBacktestEvaluationReportRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcBacktestEvaluationReportRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(BacktestEvaluationReport backtestEvaluationReport) {
        jdbcTemplate.update(
                """
                        INSERT INTO backtest_eval_reports (
                            eval_report_id, backtest_run_id, evaluation_status, initial_capital, final_cash_balance,
                            final_position_market_value, final_equity, realized_pnl, unrealized_pnl, net_pnl,
                            total_return_rate, total_return, annualized_return, total_fee, total_slippage, order_count, trade_count,
                            winning_trade_count, losing_trade_count, flat_trade_count, win_rate,
                            max_drawdown, max_drawdown_rate, profit_loss_ratio, sharpe_ratio, report_json, metrics_json, failure_code,
                            failure_message, evaluated_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?, ?, ?)
                        ON CONFLICT (backtest_run_id) DO UPDATE
                        SET eval_report_id = EXCLUDED.eval_report_id,
                            evaluation_status = EXCLUDED.evaluation_status,
                            initial_capital = EXCLUDED.initial_capital,
                            final_cash_balance = EXCLUDED.final_cash_balance,
                            final_position_market_value = EXCLUDED.final_position_market_value,
                            final_equity = EXCLUDED.final_equity,
                            realized_pnl = EXCLUDED.realized_pnl,
                            unrealized_pnl = EXCLUDED.unrealized_pnl,
                            net_pnl = EXCLUDED.net_pnl,
                            total_return_rate = EXCLUDED.total_return_rate,
                            total_return = EXCLUDED.total_return,
                            annualized_return = EXCLUDED.annualized_return,
                            total_fee = EXCLUDED.total_fee,
                            total_slippage = EXCLUDED.total_slippage,
                            order_count = EXCLUDED.order_count,
                            trade_count = EXCLUDED.trade_count,
                            winning_trade_count = EXCLUDED.winning_trade_count,
                            losing_trade_count = EXCLUDED.losing_trade_count,
                            flat_trade_count = EXCLUDED.flat_trade_count,
                            win_rate = EXCLUDED.win_rate,
                            max_drawdown = EXCLUDED.max_drawdown,
                            max_drawdown_rate = EXCLUDED.max_drawdown_rate,
                            profit_loss_ratio = EXCLUDED.profit_loss_ratio,
                            sharpe_ratio = EXCLUDED.sharpe_ratio,
                            report_json = EXCLUDED.report_json,
                            metrics_json = EXCLUDED.metrics_json,
                            failure_code = EXCLUDED.failure_code,
                            failure_message = EXCLUDED.failure_message,
                            evaluated_at = EXCLUDED.evaluated_at,
                            updated_at = EXCLUDED.updated_at
                        """,
                backtestEvaluationReport.evalReportId(),
                backtestEvaluationReport.backtestRunId(),
                backtestEvaluationReport.evaluationStatus().name(),
                backtestEvaluationReport.initialCapital(),
                backtestEvaluationReport.finalCashBalance(),
                backtestEvaluationReport.finalPositionMarketValue(),
                backtestEvaluationReport.finalEquity(),
                backtestEvaluationReport.realizedPnl(),
                backtestEvaluationReport.unrealizedPnl(),
                backtestEvaluationReport.netPnl(),
                backtestEvaluationReport.totalReturnRate(),
                backtestEvaluationReport.totalReturn(),
                backtestEvaluationReport.annualizedReturn(),
                backtestEvaluationReport.totalFee(),
                backtestEvaluationReport.totalSlippage(),
                backtestEvaluationReport.orderCount(),
                backtestEvaluationReport.tradeCount(),
                backtestEvaluationReport.winningTradeCount(),
                backtestEvaluationReport.losingTradeCount(),
                backtestEvaluationReport.flatTradeCount(),
                backtestEvaluationReport.winRate(),
                backtestEvaluationReport.maxDrawdown(),
                backtestEvaluationReport.maxDrawdownRate(),
                backtestEvaluationReport.profitLossRatio(),
                backtestEvaluationReport.sharpeRatio(),
                backtestEvaluationReport.reportJson(),
                backtestEvaluationReport.metricsJson(),
                backtestEvaluationReport.failureCode(),
                backtestEvaluationReport.failureMessage(),
                toTimestamp(backtestEvaluationReport.evaluatedAt()),
                Timestamp.from(backtestEvaluationReport.createdAt()),
                Timestamp.from(backtestEvaluationReport.updatedAt())
        );
    }

    @Override
    public Optional<BacktestEvaluationReport> findByBacktestRunId(String backtestRunId) {
        List<BacktestEvaluationReport> rows = jdbcTemplate.query(
                """
                        SELECT eval_report_id, backtest_run_id, evaluation_status, initial_capital, final_cash_balance,
                               final_position_market_value, final_equity, realized_pnl, unrealized_pnl, net_pnl,
                               total_return_rate, total_return, annualized_return, total_fee, total_slippage, order_count, trade_count,
                               winning_trade_count, losing_trade_count, flat_trade_count, win_rate,
                               max_drawdown, max_drawdown_rate, profit_loss_ratio, sharpe_ratio,
                               report_json::text AS report_json, metrics_json::text AS metrics_json,
                               failure_code, failure_message, evaluated_at, created_at, updated_at
                        FROM backtest_eval_reports
                        WHERE backtest_run_id = ?
                        """,
                ROW_MAPPER,
                backtestRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<BacktestEvaluationReport> findByEvalReportId(String evalReportId) {
        List<BacktestEvaluationReport> rows = jdbcTemplate.query(
                """
                        SELECT eval_report_id, backtest_run_id, evaluation_status, initial_capital, final_cash_balance,
                               final_position_market_value, final_equity, realized_pnl, unrealized_pnl, net_pnl,
                               total_return_rate, total_return, annualized_return, total_fee, total_slippage, order_count, trade_count,
                               winning_trade_count, losing_trade_count, flat_trade_count, win_rate,
                               max_drawdown, max_drawdown_rate, profit_loss_ratio, sharpe_ratio,
                               report_json::text AS report_json, metrics_json::text AS metrics_json,
                               failure_code, failure_message, evaluated_at, created_at, updated_at
                        FROM backtest_eval_reports
                        WHERE eval_report_id = ?
                        """,
                ROW_MAPPER,
                evalReportId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<BacktestEvaluationReport> listAll() {
        return jdbcTemplate.query(
                """
                        SELECT eval_report_id, backtest_run_id, evaluation_status, initial_capital, final_cash_balance,
                               final_position_market_value, final_equity, realized_pnl, unrealized_pnl, net_pnl,
                               total_return_rate, total_return, annualized_return, total_fee, total_slippage, order_count, trade_count,
                               winning_trade_count, losing_trade_count, flat_trade_count, win_rate,
                               max_drawdown, max_drawdown_rate, profit_loss_ratio, sharpe_ratio,
                               report_json::text AS report_json, metrics_json::text AS metrics_json,
                               failure_code, failure_message, evaluated_at, created_at, updated_at
                        FROM backtest_eval_reports
                        ORDER BY evaluated_at DESC NULLS LAST, created_at DESC, eval_report_id DESC
                        """,
                ROW_MAPPER
        );
    }

    private static BacktestEvaluationReport mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp evaluatedAt = resultSet.getTimestamp("evaluated_at");
        return new BacktestEvaluationReport(
                resultSet.getString("eval_report_id"),
                resultSet.getString("backtest_run_id"),
                EvaluationStatus.valueOf(resultSet.getString("evaluation_status")),
                resultSet.getBigDecimal("initial_capital"),
                resultSet.getBigDecimal("final_cash_balance"),
                resultSet.getBigDecimal("final_position_market_value"),
                resultSet.getBigDecimal("final_equity"),
                resultSet.getBigDecimal("realized_pnl"),
                resultSet.getBigDecimal("unrealized_pnl"),
                resultSet.getBigDecimal("net_pnl"),
                resultSet.getBigDecimal("total_return_rate"),
                resultSet.getBigDecimal("total_return"),
                resultSet.getBigDecimal("annualized_return"),
                resultSet.getBigDecimal("total_fee"),
                resultSet.getBigDecimal("total_slippage"),
                resultSet.getObject("order_count", Integer.class),
                resultSet.getObject("trade_count", Integer.class),
                resultSet.getObject("winning_trade_count", Integer.class),
                resultSet.getObject("losing_trade_count", Integer.class),
                resultSet.getObject("flat_trade_count", Integer.class),
                resultSet.getBigDecimal("win_rate"),
                resultSet.getBigDecimal("max_drawdown"),
                resultSet.getBigDecimal("max_drawdown_rate"),
                resultSet.getBigDecimal("profit_loss_ratio"),
                resultSet.getBigDecimal("sharpe_ratio"),
                resultSet.getString("report_json"),
                resultSet.getString("metrics_json"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                evaluatedAt == null ? null : evaluatedAt.toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Timestamp toTimestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}


