package com.guidinglight.nexusquant.trading.infra.reconciliation;

import com.guidinglight.nexusquant.trading.application.reconciliation.LocalOrderSnapshot;
import com.guidinglight.nexusquant.trading.application.reconciliation.LocalOrderSnapshotReadPort;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationRequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * GateW-3 本地订单快照窄适配器。
 *
 * <p>SQL 仅含 SELECT，按 account/exchange/environment/server-allowlisted symbols/time window 限定，
 * 并以 bounded LIMIT 收口；不注入混合读写 OrderRepository，也不更新 order/trade/ledger/audit/event。</p>
 */
public final class JdbcLocalOrderSnapshotReadAdapter implements LocalOrderSnapshotReadPort {
    private static final String SELECT = """
            SELECT o.order_id, o.client_order_id, o.exchange_order_id, o.symbol, o.side, o.type,
                   o.price, o.qty, COALESCE(SUM(t.qty), 0) AS filled_qty, o.status, o.updated_at
            FROM orders o
            LEFT JOIN trades t
              ON t.order_id = o.order_id
             AND t.account_id = o.account_id
             AND t.exchange_code = o.exchange_code
             AND t.trade_env = o.trade_env
            WHERE o.account_id = :accountId
              AND o.exchange_code = :exchangeCode
              AND o.trade_env = :tradeEnvironment
              AND o.symbol IN (:symbols)
              AND o.updated_at >= :windowStart
              AND o.updated_at <= :windowEnd
            GROUP BY o.order_id, o.client_order_id, o.exchange_order_id, o.symbol, o.side, o.type,
                     o.price, o.qty, o.status, o.updated_at
            ORDER BY o.updated_at DESC, o.order_id ASC
            LIMIT :boundedLimit
            """;
    private static final RowMapper<LocalOrderSnapshot> ROW_MAPPER = JdbcLocalOrderSnapshotReadAdapter::map;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLocalOrderSnapshotReadAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public List<LocalOrderSnapshot> read(ReconciliationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        int boundedLimit = Math.multiplyExact(request.symbols().size(), request.recordLimit());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("accountId", request.accountId())
                .addValue("exchangeCode", request.exchange())
                .addValue("tradeEnvironment", request.tradeEnvironment())
                .addValue("symbols", request.symbols())
                .addValue("windowStart", request.windowStart())
                .addValue("windowEnd", request.windowEnd())
                .addValue("boundedLimit", boundedLimit);
        return List.copyOf(jdbcTemplate.query(SELECT, parameters, ROW_MAPPER));
    }

    private static LocalOrderSnapshot map(ResultSet rs, int rowNumber) throws SQLException {
        return new LocalOrderSnapshot(
                rs.getString("order_id"),
                rs.getString("client_order_id"),
                rs.getString("exchange_order_id"),
                rs.getString("symbol"),
                rs.getString("side"),
                rs.getString("type"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("qty"),
                rs.getBigDecimal("filled_qty"),
                rs.getString("status"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
