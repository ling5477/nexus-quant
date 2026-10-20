package com.guidinglight.nexusquant.trading.infra.jdbc;

import com.guidinglight.nexusquant.trading.application.port.StrategySimReplayClockPort;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 订单准入复用不可变决策的执行事件时间，缺失或歧义时拒绝使用墙钟兜底。 */
@Repository
public class JdbcStrategySimReplayClockRepository implements StrategySimReplayClockPort {
    private final JdbcTemplate jdbc;

    public JdbcStrategySimReplayClockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Instant executionTime(long accountId, String clientOrderId) {
        if (clientOrderId == null || !clientOrderId.matches("coid-sim-[0-9a-f]{60}")) {
            throw new IllegalArgumentException("SIM_REPLAY_CLOCK_IDENTITY_INVALID");
        }
        List<Instant> times = jdbc.query("""
                SELECT execution_open_time FROM strategy_sim_decisions
                WHERE canonical_account_id=? AND left(decision_id,60)=?
                  AND status='NOT_TRADABLE' AND reason='DECIDING'
                  AND execution_open_time>signal_available_at
                """, (rs, row) -> rs.getTimestamp(1).toInstant(),
                accountId, clientOrderId.substring("coid-sim-".length()));
        if (times.size() != 1) throw new IllegalStateException("SIM_REPLAY_CLOCK_UNAVAILABLE");
        return times.getFirst();
    }
}
