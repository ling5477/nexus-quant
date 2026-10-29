package com.guidinglight.nexusquant.research.infra.backtest.jdbc;

import com.guidinglight.nexusquant.research.domain.backtest.SimPosition;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimPositionRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimPositionQueryRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcSimPositionRepository 是 sim_positions 表的 JDBC 实现。
 */
@Repository
public class JdbcSimPositionRepository implements SimPositionRepository, SimPositionQueryRepository {

    private static final RowMapper<SimPosition> ROW_MAPPER = JdbcSimPositionRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSimPositionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(SimPosition simPosition) {
        jdbcTemplate.update(
                """
                        INSERT INTO sim_positions (
                            sim_position_id, backtest_run_id, symbol, quantity, average_entry_price,
                            realized_pnl, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (backtest_run_id, symbol) DO UPDATE
                        SET sim_position_id = EXCLUDED.sim_position_id,
                            quantity = EXCLUDED.quantity,
                            average_entry_price = EXCLUDED.average_entry_price,
                            realized_pnl = EXCLUDED.realized_pnl,
                            updated_at = EXCLUDED.updated_at
                        """,
                simPosition.simPositionId(),
                simPosition.backtestRunId(),
                simPosition.symbol(),
                simPosition.quantity(),
                simPosition.averageEntryPrice(),
                simPosition.realizedPnl(),
                Timestamp.from(simPosition.createdAt()),
                Timestamp.from(simPosition.updatedAt())
        );
    }

    @Override
    public Optional<SimPosition> findByBacktestRunIdAndSymbol(String backtestRunId, String symbol) {
        List<SimPosition> rows = jdbcTemplate.query(
                """
                        SELECT sim_position_id, backtest_run_id, symbol, quantity, average_entry_price,
                               realized_pnl, created_at, updated_at
                        FROM sim_positions
                        WHERE backtest_run_id = ?
                          AND symbol = ?
                        """,
                ROW_MAPPER,
                backtestRunId,
                symbol
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<SimPosition> listByBacktestRunId(String backtestRunId) {
        return jdbcTemplate.query(
                """
                        SELECT sim_position_id, backtest_run_id, symbol, quantity, average_entry_price,
                               realized_pnl, created_at, updated_at
                        FROM sim_positions
                        WHERE backtest_run_id = ?
                        ORDER BY symbol ASC
                        """,
                ROW_MAPPER,
                backtestRunId
        );
    }

    private static SimPosition mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SimPosition(
                resultSet.getString("sim_position_id"),
                resultSet.getString("backtest_run_id"),
                resultSet.getString("symbol"),
                resultSet.getBigDecimal("quantity"),
                resultSet.getBigDecimal("average_entry_price"),
                resultSet.getBigDecimal("realized_pnl"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}


