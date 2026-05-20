package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperOrderStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingOrderRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperTradingOrderRepository implements PaperTradingOrderRepository {

    private static final RowMapper<PaperTradingOrder> ROW_MAPPER = JdbcPaperTradingOrderRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperTradingOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PaperTradingOrder order) {
        jdbcTemplate.update(
                """
                        INSERT INTO paper_trading_orders (
                            paper_order_id, paper_run_id, symbol, side, order_type, quantity, price,
                            status, reason, raw_signal_json, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?)
                        """,
                order.paperOrderId(),
                order.paperRunId(),
                order.symbol(),
                order.side(),
                order.orderType(),
                order.quantity(),
                order.price(),
                order.status().name(),
                order.reason(),
                order.rawSignalJson(),
                Timestamp.from(order.createdAt()),
                Timestamp.from(order.updatedAt())
        );
    }

    @Override
    public List<PaperTradingOrder> listByRunId(String paperRunId) {
        return jdbcTemplate.query(
                """
                        SELECT paper_order_id, paper_run_id, symbol, side, order_type, quantity, price,
                               status, reason, raw_signal_json::text AS raw_signal_json,
                               created_at, updated_at
                        FROM paper_trading_orders
                        WHERE paper_run_id = ?
                        ORDER BY created_at DESC, paper_order_id DESC
                        """,
                ROW_MAPPER,
                paperRunId
        );
    }

    private static PaperTradingOrder mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        BigDecimal price = resultSet.getBigDecimal("price");
        return new PaperTradingOrder(
                resultSet.getString("paper_order_id"),
                resultSet.getString("paper_run_id"),
                resultSet.getString("symbol"),
                resultSet.getString("side"),
                resultSet.getString("order_type"),
                resultSet.getBigDecimal("quantity"),
                price,
                PaperOrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("reason"),
                resultSet.getString("raw_signal_json"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
