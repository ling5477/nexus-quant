package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingPositionRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperTradingPositionRepository implements PaperTradingPositionRepository {

    private static final RowMapper<PaperTradingPosition> ROW_MAPPER = JdbcPaperTradingPositionRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperTradingPositionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PaperTradingPosition position) {
        jdbcTemplate.update(
                """
                        INSERT INTO paper_trading_positions (
                            paper_position_id, paper_run_id, symbol, quantity, avg_price,
                            unrealized_pnl, realized_pnl, updated_at, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                position.paperPositionId(),
                position.paperRunId(),
                position.symbol(),
                position.quantity(),
                position.avgPrice(),
                position.unrealizedPnl(),
                position.realizedPnl(),
                Timestamp.from(position.updatedAt()),
                Timestamp.from(position.createdAt())
        );
    }

    @Override
    public void upsert(PaperTradingPosition position) {
        jdbcTemplate.update(
                """
                        INSERT INTO paper_trading_positions (
                            paper_position_id, paper_run_id, symbol, quantity, avg_price,
                            unrealized_pnl, realized_pnl, updated_at, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (paper_run_id, symbol) DO UPDATE
                        SET quantity = EXCLUDED.quantity,
                            avg_price = EXCLUDED.avg_price,
                            unrealized_pnl = EXCLUDED.unrealized_pnl,
                            realized_pnl = EXCLUDED.realized_pnl,
                            updated_at = EXCLUDED.updated_at
                        """,
                position.paperPositionId(),
                position.paperRunId(),
                position.symbol(),
                position.quantity(),
                position.avgPrice(),
                position.unrealizedPnl(),
                position.realizedPnl(),
                Timestamp.from(position.updatedAt()),
                Timestamp.from(position.createdAt())
        );
    }

    @Override
    public List<PaperTradingPosition> listByRunId(String paperRunId) {
        return jdbcTemplate.query(
                """
                        SELECT paper_position_id, paper_run_id, symbol, quantity, avg_price,
                               unrealized_pnl, realized_pnl, updated_at, created_at
                        FROM paper_trading_positions
                        WHERE paper_run_id = ?
                        ORDER BY symbol ASC
                        """,
                ROW_MAPPER,
                paperRunId
        );
    }

    private static PaperTradingPosition mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new PaperTradingPosition(
                resultSet.getString("paper_position_id"),
                resultSet.getString("paper_run_id"),
                resultSet.getString("symbol"),
                resultSet.getBigDecimal("quantity"),
                resultSet.getBigDecimal("avg_price"),
                resultSet.getBigDecimal("unrealized_pnl"),
                resultSet.getBigDecimal("realized_pnl"),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
