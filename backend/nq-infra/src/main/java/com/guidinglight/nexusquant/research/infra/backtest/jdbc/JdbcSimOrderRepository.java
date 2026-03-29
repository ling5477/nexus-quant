package com.guidinglight.nexusquant.research.infra.backtest.jdbc;

import com.guidinglight.nexusquant.research.domain.backtest.SimOrder;
import com.guidinglight.nexusquant.research.domain.backtest.SimOrderStatus;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimOrderRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimOrderQueryRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcSimOrderRepository 是 sim_orders 表的 JDBC 实现。
 */
@Repository
public class JdbcSimOrderRepository implements SimOrderRepository, SimOrderQueryRepository {

    private static final RowMapper<SimOrder> ROW_MAPPER = JdbcSimOrderRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSimOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(SimOrder simOrder) {
        jdbcTemplate.update(
                """
                        INSERT INTO sim_orders (
                            sim_order_id, backtest_run_id, symbol, side, order_type, requested_quantity,
                            requested_price, status, created_at, filled_at, reject_reason, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                simOrder.simOrderId(),
                simOrder.backtestRunId(),
                simOrder.symbol(),
                simOrder.side(),
                simOrder.orderType(),
                simOrder.requestedQuantity(),
                simOrder.requestedPrice(),
                simOrder.status().name(),
                Timestamp.from(simOrder.createdAt()),
                toTimestamp(simOrder.filledAt()),
                simOrder.rejectReason(),
                Timestamp.from(simOrder.updatedAt())
        );
    }

    @Override
    public List<SimOrder> listByBacktestRunId(String backtestRunId) {
        return jdbcTemplate.query(
                """
                        SELECT sim_order_id, backtest_run_id, symbol, side, order_type, requested_quantity,
                               requested_price, status, created_at, filled_at, reject_reason, updated_at
                        FROM sim_orders
                        WHERE backtest_run_id = ?
                        ORDER BY created_at ASC, sim_order_id ASC
                        """,
                ROW_MAPPER,
                backtestRunId
        );
    }

    private static SimOrder mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp filledAt = resultSet.getTimestamp("filled_at");
        return new SimOrder(
                resultSet.getString("sim_order_id"),
                resultSet.getString("backtest_run_id"),
                resultSet.getString("symbol"),
                resultSet.getString("side"),
                resultSet.getString("order_type"),
                resultSet.getBigDecimal("requested_quantity"),
                resultSet.getBigDecimal("requested_price"),
                SimOrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                filledAt == null ? null : filledAt.toInstant(),
                resultSet.getString("reject_reason"),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Timestamp toTimestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}


