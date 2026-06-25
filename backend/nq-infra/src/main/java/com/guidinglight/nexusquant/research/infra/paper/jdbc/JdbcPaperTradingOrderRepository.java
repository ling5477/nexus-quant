package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperOrderStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingOrderRepository;

import java.math.BigDecimal;
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
public class JdbcPaperTradingOrderRepository implements PaperTradingOrderRepository {

    private static final RowMapper<PaperTradingOrder> ROW_MAPPER = JdbcPaperTradingOrderRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public JdbcPaperTradingOrderRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    JdbcPaperTradingOrderRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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

    @Override
    public Map<String, Long> countByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        // 去重后以命名参数绑定 IN 列表（参数化，杜绝 SQL 拼接注入）；单次 GROUP BY 聚合订单计数，
        // 只回传计数而非完整订单行，无订单的 run 不出现在结果中（调用方缺省 0）。
        MapSqlParameterSource params = new MapSqlParameterSource(
                "runIds", new ArrayList<>(new LinkedHashSet<>(runIds)));
        List<Map.Entry<String, Long>> rows = namedParameterJdbcTemplate.query("""
                SELECT paper_run_id, COUNT(*) AS order_count
                FROM paper_trading_orders
                WHERE paper_run_id IN (:runIds)
                GROUP BY paper_run_id
                """, params, (rs, rowNum) -> Map.entry(rs.getString("paper_run_id"), rs.getLong("order_count")));
        return rows.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
