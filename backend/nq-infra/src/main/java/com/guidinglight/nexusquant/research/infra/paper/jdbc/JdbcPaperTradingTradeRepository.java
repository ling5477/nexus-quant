package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperTradingTradeRepository implements PaperTradingTradeRepository {

    private static final RowMapper<PaperTradingTrade> ROW_MAPPER = JdbcPaperTradingTradeRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public JdbcPaperTradingTradeRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    JdbcPaperTradingTradeRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public void insert(PaperTradingTrade trade) {
        jdbcTemplate.update(
                """
                        INSERT INTO paper_trading_trades (
                            paper_trade_id, paper_order_id, paper_run_id, symbol, side,
                            quantity, price, fee, traded_at, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                trade.paperTradeId(),
                trade.paperOrderId(),
                trade.paperRunId(),
                trade.symbol(),
                trade.side(),
                trade.quantity(),
                trade.price(),
                trade.fee(),
                Timestamp.from(trade.tradedAt()),
                Timestamp.from(trade.createdAt())
        );
    }

    @Override
    public List<PaperTradingTrade> listByRunId(String paperRunId) {
        return jdbcTemplate.query(
                """
                        SELECT paper_trade_id, paper_order_id, paper_run_id, symbol, side,
                               quantity, price, fee, traded_at, created_at
                        FROM paper_trading_trades
                        WHERE paper_run_id = ?
                        ORDER BY traded_at DESC, paper_trade_id DESC
                        """,
                ROW_MAPPER,
                paperRunId
        );
    }

    @Override
    public Map<String, Long> countByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        // 去重后以命名参数绑定 IN 列表（参数化，杜绝 SQL 拼接注入）；单次 GROUP BY 聚合成交计数，
        // 只回传计数而非完整成交行，无成交的 run 不出现在结果中（调用方缺省 0）。
        MapSqlParameterSource params = new MapSqlParameterSource(
                "runIds", new ArrayList<>(new LinkedHashSet<>(runIds)));
        List<Map.Entry<String, Long>> rows = namedParameterJdbcTemplate.query("""
                SELECT paper_run_id, COUNT(*) AS trade_count
                FROM paper_trading_trades
                WHERE paper_run_id IN (:runIds)
                GROUP BY paper_run_id
                """, params, (rs, rowNum) -> Map.entry(rs.getString("paper_run_id"), rs.getLong("trade_count")));
        return rows.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static PaperTradingTrade mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new PaperTradingTrade(
                resultSet.getString("paper_trade_id"),
                resultSet.getString("paper_order_id"),
                resultSet.getString("paper_run_id"),
                resultSet.getString("symbol"),
                resultSet.getString("side"),
                resultSet.getBigDecimal("quantity"),
                resultSet.getBigDecimal("price"),
                resultSet.getBigDecimal("fee"),
                resultSet.getTimestamp("traded_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
