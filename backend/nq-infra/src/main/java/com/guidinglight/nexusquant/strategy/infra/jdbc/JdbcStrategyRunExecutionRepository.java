package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.trading.domain.port.StrategyOrderBindingRepository;
import com.guidinglight.nexusquant.trading.domain.EffectiveOrderParameters;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** run 锁拥有本地下一步；扫描 cursor 只保证检查公平性，不能授予发送权。 */
@Repository
public class JdbcStrategyRunExecutionRepository implements StrategyRunExecutionRepository, StrategyOrderBindingRepository {
    private final JdbcTemplate jdbc;
    private final StrategyRunRepository runs;

    public JdbcStrategyRunExecutionRepository(JdbcTemplate jdbc, StrategyRunRepository runs) {
        this.jdbc = jdbc;
        this.runs = runs;
    }

    @Override
    public Optional<StrategyDispatchWork> findWork(String runId) {
        var rows = jdbc.query("SELECT * FROM strategy_run_dispatch_work WHERE strategy_run_id=?", (rs, n) ->
                new StrategyDispatchWork(rs.getString("strategy_run_id"), rs.getInt("work_schema_version"),
                        rs.getInt("definition_version"), rs.getLong("account_id"), rs.getString("client_order_id"),
                        rs.getString("symbol"), OrderSide.valueOf(rs.getString("side")), OrderType.valueOf(rs.getString("order_type")),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("price"), rs.getString("time_in_force")), runId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<EffectiveOrderParameters> findEffective(String runId) {
        return jdbc.query("SELECT effective_quantity,effective_price,normalization_rejection FROM strategy_run_dispatch_work "
                + "WHERE strategy_run_id=? AND (effective_quantity IS NOT NULL OR normalization_rejection IS NOT NULL)",
                (rs, n) -> new EffectiveOrderParameters(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getString(3)), runId)
                .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bindEffective(String runId, EffectiveOrderParameters parameters) {
        jdbc.queryForObject("SELECT nq_bind_strategy_effective(?,?,?,?)", Boolean.class, runId,
                parameters.quantity(), parameters.price(), parameters.rejectionCode());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public StrategyRun lockRun(String runId) {
        // run 身份不可改；此锁仍串行化 lifecycle writer，并允许 Order/Trade 的外键 KEY SHARE 检查。
        jdbc.queryForObject("SELECT strategy_run_id FROM strategy_runs WHERE strategy_run_id=? FOR NO KEY UPDATE", String.class, runId);
        return runs.findByStrategyRunId(runId).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean beginDispatch(String runId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT nq_begin_strategy_dispatch(?)", Boolean.class, runId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public boolean project(String runId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT nq_project_strategy_run(?)", Boolean.class, runId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public List<String> reserveCandidates(int limit) {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be 1..50");
        ScanKey cursor = jdbc.queryForObject("SELECT last_started_at,last_run_id FROM strategy_run_recovery_scan_cursor "
                + "WHERE cursor_id=1 FOR UPDATE", (rs, n) -> new ScanKey(
                        rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), rs.getString(2)));
        List<ScanKey> selected = new ArrayList<>();
        String base = "SELECT started_at,strategy_run_id FROM strategy_runs WHERE status IN ('CREATED','DISPATCHING','RUNNING')";
        String tail = " ORDER BY started_at,strategy_run_id LIMIT ?";
        if (cursor.startedAt() == null) {
            selected.addAll(jdbc.query(base + tail, (rs, n) -> new ScanKey(rs.getTimestamp(1).toInstant(), rs.getString(2)), limit));
        } else {
            selected.addAll(jdbc.query(base + " AND (started_at,strategy_run_id)>(?,?)" + tail,
                    (rs, n) -> new ScanKey(rs.getTimestamp(1).toInstant(), rs.getString(2)),
                    Timestamp.from(cursor.startedAt()), cursor.runId(), limit));
            if (selected.size() < limit) selected.addAll(jdbc.query(base + " AND (started_at,strategy_run_id)<=(?,?)" + tail,
                    (rs, n) -> new ScanKey(rs.getTimestamp(1).toInstant(), rs.getString(2)),
                    Timestamp.from(cursor.startedAt()), cursor.runId(), limit - selected.size()));
        }
        if (!selected.isEmpty()) {
            ScanKey last = selected.getLast();
            if (jdbc.update("UPDATE strategy_run_recovery_scan_cursor SET last_started_at=?,last_run_id=? WHERE cursor_id=1",
                    Timestamp.from(last.startedAt()), last.runId()) != 1) throw new IllegalStateException("recovery cursor lost");
        }
        return selected.stream().map(ScanKey::runId).toList();
    }

    private record ScanKey(Instant startedAt, String runId) { }

    @Override
    public List<String> findCandidates(String strategyId, int limit) {
        if (strategyId == null || strategyId.isBlank() || limit < 1 || limit > 50) throw new IllegalArgumentException("invalid recovery scope/limit");
        return jdbc.queryForList("SELECT strategy_run_id FROM strategy_runs WHERE strategy_id=? "
                + "AND status IN ('CREATED','DISPATCHING','RUNNING') ORDER BY started_at,strategy_run_id LIMIT ?",
                String.class, strategyId, limit);
    }
}
