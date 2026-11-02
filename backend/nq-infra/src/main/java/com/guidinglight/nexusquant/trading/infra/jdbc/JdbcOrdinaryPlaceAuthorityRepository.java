package com.guidinglight.nexusquant.trading.infra.jdbc;

import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrdinaryPlaceAuthorityRepository;
import com.guidinglight.nexusquant.trading.domain.TradingVenue;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 受限数据库函数负责新建/不可逆 CAS；MAY 的读取永远不能恢复发送许可。 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcOrdinaryPlaceAuthorityRepository implements OrdinaryPlaceAuthorityRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrdinaryPlaceAuthorityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void insertOrder(OrderRecord o, Instant now) {
        if (o.canonicalVenue() != TradingVenue.OKX) {
            throw new IllegalArgumentException("ordinary PLACE authority requires OKX");
        }
        jdbc.queryForObject("SELECT nq_create_ordinary_place_order(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", String.class,
                o.orderId(), o.accountId(), o.strategyRunId(), o.canonicalVenue().name(), o.symbol(), o.clientOrderId(),
                o.side(), o.type(), o.price(), o.qty(), o.status().name(), o.reason(), o.traceId(), o.tradeEnv(),
                o.version(), Timestamp.from(now));
    }

    @Override public boolean arm(OrderRecord expected) { return decide(expected, true); }
    @Override public boolean revoke(OrderRecord expected) { return decide(expected, false); }

    private boolean decide(OrderRecord expected, boolean send) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT nq_decide_ordinary_place(?,?,?,?)", Boolean.class,
                expected.orderId(), expected.status().name(), expected.version(), send));
    }
}
