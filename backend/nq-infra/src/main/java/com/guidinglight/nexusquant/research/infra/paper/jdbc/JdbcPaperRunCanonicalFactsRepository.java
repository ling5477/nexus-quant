package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperOrderStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunCanonicalFactsRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperRunCanonicalFactsRepository implements PaperRunCanonicalFactsRepository {
    private final JdbcTemplate jdbc;

    public JdbcPaperRunCanonicalFactsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isStrategySim(String paperRunId) {
        // V52 的唯一且不可变 account 绑定是 run 类型证据；NULL 保留历史 Paper 读路径。
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM paper_trading_runs
                    WHERE paper_run_id=? AND canonical_account_id IS NOT NULL)
                """, Boolean.class, paperRunId));
    }

    @Override
    public List<PaperTradingOrder> orders(String paperRunId) {
        return jdbc.query("""
                SELECT o.* FROM orders o
                JOIN paper_trading_runs r ON r.canonical_account_id=o.account_id
                WHERE r.paper_run_id=? ORDER BY o.created_at,o.order_id LIMIT 500
                """, (rs, row) -> new PaperTradingOrder(
                rs.getString("order_id"), paperRunId, rs.getString("symbol"),
                rs.getString("side"), rs.getString("type"), rs.getBigDecimal("qty"),
                rs.getBigDecimal("price"), PaperOrderStatus.valueOf(rs.getString("status")),
                rs.getString("reason"), null, instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at"))), paperRunId);
    }

    @Override
    public List<PaperTradingTrade> trades(String paperRunId) {
        return jdbc.query("""
                SELECT t.*,o.side FROM trades t
                JOIN paper_trading_runs r ON r.canonical_account_id=t.account_id
                JOIN orders o ON o.order_id=t.order_id AND o.account_id=t.account_id
                WHERE r.paper_run_id=? ORDER BY t.ts,t.trade_id LIMIT 500
                """, (rs, row) -> new PaperTradingTrade(
                rs.getString("trade_id"), rs.getString("order_id"), paperRunId,
                rs.getString("symbol"), rs.getString("side"), rs.getBigDecimal("qty"),
                rs.getBigDecimal("price"), rs.getBigDecimal("fee"),
                instant(rs.getTimestamp("ts")), instant(rs.getTimestamp("created_at"))), paperRunId);
    }

    @Override
    public List<PaperTradingPosition> positions(String paperRunId) {
        return jdbc.query("""
                SELECT p.* FROM positions p
                JOIN paper_trading_runs r ON r.canonical_account_id=p.account_id
                WHERE r.paper_run_id=? AND p.qty<>0 ORDER BY p.symbol LIMIT 500
                """, (rs, row) -> new PaperTradingPosition(
                "canonical-position-" + rs.getLong("id"), paperRunId,
                rs.getString("symbol"), rs.getBigDecimal("qty"), rs.getBigDecimal("avg_price"),
                null, null, instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("updated_at"))), paperRunId);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
