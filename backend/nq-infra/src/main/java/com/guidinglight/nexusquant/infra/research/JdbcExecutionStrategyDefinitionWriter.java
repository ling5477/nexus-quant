package com.guidinglight.nexusquant.infra.research;

import com.guidinglight.nexusquant.research.model.ExecutionStrategyDefinitionDraft;
import com.guidinglight.nexusquant.research.port.ExecutionStrategyDefinitionWriter;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcExecutionStrategyDefinitionWriter 负责把发布草稿写入执行域 strategy_definitions。
 */
@Repository
public class JdbcExecutionStrategyDefinitionWriter implements ExecutionStrategyDefinitionWriter {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcExecutionStrategyDefinitionWriter(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    JdbcExecutionStrategyDefinitionWriter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public String publish(ExecutionStrategyDefinitionDraft draft) {
        Instant now = Instant.now(clock);
        jdbcTemplate.update(
                """
                        INSERT INTO strategy_definitions (
                            strategy_id, strategy_code, strategy_name, strategy_type, exchange_code, account_id, trade_env,
                            enabled, config_snapshot, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?)
                        """,
                draft.targetStrategyDefinitionId(),
                draft.strategyCode(),
                draft.strategyName(),
                draft.strategyType(),
                draft.exchangeCode(),
                draft.accountId(),
                draft.tradeEnv(),
                false,
                draft.configSnapshotJson(),
                1,
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return draft.targetStrategyDefinitionId();
    }
}
