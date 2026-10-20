package com.guidinglight.nexusquant.research.infra.eval.jdbc;

import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;
import com.guidinglight.nexusquant.research.domain.publish.port.BacktestEvaluationQueryPort;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** JdbcBacktestEvaluationQueryPort 读取研究域 publish 所需的最小评估投影。 */
@Repository
public class JdbcBacktestEvaluationQueryPort implements BacktestEvaluationQueryPort {

  private static final int MAX_EVALUATION_RUNS_PER_QUERY = 200;

  // 每个来源只返回最新一条，历史报告数量增加也不能放大200运行窗口的结果集。
  private static final String SELECT_PROJECTION =
      """
SELECT DISTINCT ON (backtest_run_id) eval_report_id, backtest_run_id, evaluation_status, evaluated_at, final_equity, net_pnl,
       total_return_rate, max_drawdown_rate, win_rate, sharpe_ratio, trade_count, order_count,
       report_json::text AS report_json, failure_code, failure_message
FROM backtest_eval_reports
""";

  private static final RowMapper<BacktestEvaluationView> ROW_MAPPER =
      JdbcBacktestEvaluationQueryPort::mapRow;

  private final JdbcTemplate jdbcTemplate;
  private ResearchFeeReadScope feeScope =
      new ResearchFeeReadScope(ResearchFeeSourcePort.disabled());

  /** 手工构造默认拒绝私有来源；正式装配在SQL中统一隔离详情及列表。 */
  @Autowired
  private void bindFeeReadScope(ResearchFeeReadScope scope) {
    this.feeScope = scope;
  }

  public JdbcBacktestEvaluationQueryPort(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<BacktestEvaluationView> findByBacktestRunId(String backtestRunId) {
    List<BacktestEvaluationView> rows =
        jdbcTemplate.query(
            SELECT_PROJECTION
                + " WHERE backtest_run_id = ?"
                + " AND "
                + feeScope.backtest("backtest_eval_reports.backtest_run_id")
                + " ORDER BY backtest_run_id, evaluated_at DESC NULLS LAST, created_at DESC,"
                + " eval_report_id DESC",
            ROW_MAPPER,
            backtestRunId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  /** 来源数可与200条运行窗口相同；单次IN读取避免逐来源查询，身份仍由原费用范围端口决定。 */
  @Override
  public Map<String, BacktestEvaluationView> findByBacktestRunIds(
      Collection<String> backtestRunIds) {
    var ids = new LinkedHashSet<String>();
    if (backtestRunIds != null) {
      for (String id : backtestRunIds) {
        if (id == null || id.isBlank()) continue;
        ids.add(id);
        if (ids.size() > MAX_EVALUATION_RUNS_PER_QUERY)
          throw new IllegalArgumentException("EVALUATION_QUERY_WINDOW_TOO_LARGE");
      }
    }
    Map<String, BacktestEvaluationView> result = new LinkedHashMap<>();
    if (ids.isEmpty()) return result;
    String sql =
        SELECT_PROJECTION
            + " WHERE backtest_run_id IN ("
            + String.join(",", Collections.nCopies(ids.size(), "?"))
            + ") AND "
            + feeScope.backtest("backtest_eval_reports.backtest_run_id")
            + " ORDER BY backtest_run_id, evaluated_at DESC NULLS LAST, created_at DESC,"
            + " eval_report_id DESC";
    var rows = jdbcTemplate.query(sql, ROW_MAPPER, ids.toArray());
    for (var row : rows) result.put(row.backtestRunId(), row);
    return result;
  }

  private static BacktestEvaluationView mapRow(ResultSet resultSet, int rowNum)
      throws SQLException {
    return new BacktestEvaluationView(
        resultSet.getString("eval_report_id"),
        resultSet.getString("backtest_run_id"),
        resultSet.getString("evaluation_status"),
        resultSet.getTimestamp("evaluated_at") == null
            ? null
            : resultSet.getTimestamp("evaluated_at").toInstant(),
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
        resultSet.getString("failure_message"));
  }
}
