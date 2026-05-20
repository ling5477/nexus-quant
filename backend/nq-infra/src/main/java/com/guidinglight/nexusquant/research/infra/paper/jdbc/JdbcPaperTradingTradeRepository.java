package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperTradingTradeRepository implements PaperTradingTradeRepository {

    private static final RowMapper<PaperTradingTrade> ROW_MAPPER = JdbcPaperTradingTradeRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperTradingTradeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
