package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.common.currency.ValuationMetadata;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcEquityCurveSnapshotRepository implements EquityCurveSnapshotRepository {

  private static final RowMapper<EquityCurveSnapshot> ROW_MAPPER =
      JdbcEquityCurveSnapshotRepository::mapRow;
  private static final String SELECT_COLUMNS =
      """
SELECT equity_snapshot_id, paper_run_id, snapshot_time, total_equity, cash_balance,
       position_value, unrealized_pnl, realized_pnl, drawdown, source, created_at,
       valuation_currency,valuation_time,valuation_source,valuation_method,valuation_status,valuation_details
FROM equity_curve_snapshots
""";

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Autowired
  public JdbcEquityCurveSnapshotRepository(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, new NamedParameterJdbcTemplate(jdbcTemplate));
  }

  JdbcEquityCurveSnapshotRepository(
      JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
  }

  /** 调用方自行给出VERIFIED或JSON说明不能成为可信来源；通用旧端口继续拒绝写入。 新生产器只能经appendCanonical追加有原运行、账户和估值事件依据的投影。 */
  @Override
  public void insert(EquityCurveSnapshot snapshot) {
    throw new IllegalStateException("LEGACY_PAPER_EQUITY_PRODUCER_UNVERIFIED");
  }

  /** 同一派生身份只追加一次；重复请求只读已冻结行，不覆盖金额或历史UNKNOWN记录。 */
  @Override
  @Transactional(timeout = 10)
  public EquityCurveSnapshot appendCanonical(EquityCurveSnapshot s) {
    if (!"CANONICAL_SIM_PROJECTION".equals(s.source())
        || !"VERIFIED".equals(s.valuation().status())
        || !"USDT".equals(s.valuation().currency()))
      throw new IllegalArgumentException("CANONICAL_EQUITY_PROVENANCE_REQUIRED");
    jdbcTemplate.update(
        """
INSERT INTO equity_curve_snapshots(equity_snapshot_id,paper_run_id,snapshot_time,total_equity,
 cash_balance,position_value,unrealized_pnl,realized_pnl,drawdown,source,created_at,
 valuation_currency,valuation_time,valuation_source,valuation_method,valuation_status,valuation_details)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(equity_snapshot_id) DO NOTHING
""",
        s.equitySnapshotId(),
        s.paperRunId(),
        Timestamp.from(s.snapshotTime()),
        s.totalEquity(),
        s.cashBalance(),
        s.positionValue(),
        s.unrealizedPnl(),
        s.realizedPnl(),
        s.drawdown(),
        s.source(),
        Timestamp.from(s.createdAt()),
        s.valuation().currency(),
        Timestamp.from(s.valuation().at()),
        s.valuation().source(),
        s.valuation().method(),
        s.valuation().status(),
        s.valuation().detailsJson());
    return findById(s.equitySnapshotId()).orElseThrow();
  }

  @Override
  public Optional<EquityCurveSnapshot> findById(String snapshotId) {
    return jdbcTemplate
        .query(SELECT_COLUMNS + " WHERE equity_snapshot_id=?", ROW_MAPPER, snapshotId)
        .stream()
        .findFirst();
  }

  @Override
  public List<EquityCurveSnapshot> listByRunId(String paperRunId) {
    return jdbcTemplate.query(
        SELECT_COLUMNS + " WHERE paper_run_id = ? ORDER BY snapshot_time DESC",
        ROW_MAPPER,
        paperRunId);
  }

  @Override
  public Map<String, List<EquityCurveSnapshot>> listByRunIds(Collection<String> runIds) {
    if (runIds == null || runIds.isEmpty()) {
      return Map.of();
    }
    // 去重后以命名参数绑定 IN 列表（参数化，杜绝 SQL 拼接注入）；按 snapshot_time DESC 与单 run 口径一致。
    MapSqlParameterSource params =
        new MapSqlParameterSource("runIds", new ArrayList<>(new LinkedHashSet<>(runIds)));
    List<EquityCurveSnapshot> rows =
        namedParameterJdbcTemplate.query(
            SELECT_COLUMNS + " WHERE paper_run_id IN (:runIds) ORDER BY snapshot_time DESC",
            params,
            ROW_MAPPER);
    // groupingBy 保留全局有序结果在各分组内的相对顺序，单 run 列表口径仍为 snapshot_time DESC。
    return rows.stream()
        .collect(
            Collectors.groupingBy(
                EquityCurveSnapshot::paperRunId, LinkedHashMap::new, Collectors.toList()));
  }

  private static EquityCurveSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new EquityCurveSnapshot(
        rs.getString("equity_snapshot_id"),
        rs.getString("paper_run_id"),
        rs.getTimestamp("snapshot_time").toInstant(),
        rs.getBigDecimal("total_equity"),
        rs.getBigDecimal("cash_balance"),
        rs.getBigDecimal("position_value"),
        rs.getBigDecimal("unrealized_pnl"),
        rs.getBigDecimal("realized_pnl"),
        rs.getBigDecimal("drawdown"),
        rs.getString("source"),
        rs.getTimestamp("created_at").toInstant(),
        new ValuationMetadata(
            rs.getString("valuation_currency"),
            rs.getTimestamp("valuation_time") == null
                ? null
                : rs.getTimestamp("valuation_time").toInstant(),
            rs.getString("valuation_source"),
            rs.getString("valuation_method"),
            rs.getString("valuation_status"),
            rs.getString("valuation_details")));
  }
}
