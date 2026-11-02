package com.guidinglight.nexusquant.trading.infra.jdbc;

import com.guidinglight.nexusquant.trading.domain.OrderCancelFinality;
import com.guidinglight.nexusquant.trading.domain.port.OrderCancelFinalityRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 受限函数按 run→Order 取锁，重新核对版本与 durable fills 后才提交。 */
@Repository
public class JdbcOrderCancelFinalityRepository implements OrderCancelFinalityRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrderCancelFinalityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public boolean finish(OrderCancelFinality finality) {
        var o = finality.observedOrder();
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT nq_finish_strategy_cancel(?,?,?,?,?,?,?,?,?,?,?)",
                Boolean.class, o.strategyRunId(), o.orderId(), o.version(), o.accountId(), o.venue(), o.tradeEnv(),
                o.symbol(), o.clientOrderId(), o.externalOrderId(), o.qty(), finality.executedQuantity()));
    }
}
