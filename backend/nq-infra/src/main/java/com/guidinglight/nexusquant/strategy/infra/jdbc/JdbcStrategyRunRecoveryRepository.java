package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRecoveryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** V49 的不可逆撤销与原订单终态是资格事实，进程年龄和内存状态不是。 */
@Repository
public class JdbcStrategyRunRecoveryRepository implements StrategyRunRecoveryRepository {
    private final JdbcTemplate jdbc;

    public JdbcStrategyRunRecoveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public int recoverNoSendDispatches(String strategyId, int limit) {
        if (strategyId == null || strategyId.isBlank() || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("strategyId and limit 1..50 required");
        }
        // 先筛资格再限量，避免未决前缀饿死后面的可恢复 run；同一 run 只由一个事务推进。
        // 当前 gateway 一次 dispatch 只创建一个 Order，多单或缺少绑定不能推断完整执行结果。
        var candidates = jdbc.queryForList("""
                    SELECT r.strategy_run_id
                    FROM strategy_runs r
                    JOIN orders o ON o.strategy_run_id = r.strategy_run_id
                    JOIN ordinary_place_authorities a ON a.order_id = o.order_id
                    WHERE r.strategy_id = ? AND r.status = 'DISPATCHING'
                      AND o.account_id = r.account_id AND o.trade_env = r.trade_env
                      AND upper(r.exchange_code) = 'OKX' AND o.venue = 'OKX'
                      AND a.state = 'REVOKED_BEFORE_SEND'
                      AND o.status = 'CANCELLED' AND o.reason = 'ORDER_NOT_FOUND/OKX_51603'
                      AND NOT EXISTS (SELECT 1 FROM orders other
                          WHERE other.strategy_run_id = r.strategy_run_id AND other.order_id <> o.order_id)
                      AND NOT EXISTS (SELECT 1 FROM trades t WHERE t.order_id = o.order_id)
                    ORDER BY r.strategy_run_id
                    LIMIT ? FOR UPDATE OF r SKIP LOCKED
                """, String.class, strategyId, limit);
        int changed = 0;
        for (String runId : candidates) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT nq_project_strategy_run(?)", Boolean.class, runId))) changed++;
        }
        return changed;
    }
}
