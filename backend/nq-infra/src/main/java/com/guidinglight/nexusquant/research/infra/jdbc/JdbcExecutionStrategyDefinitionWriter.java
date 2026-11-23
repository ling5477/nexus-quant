package com.guidinglight.nexusquant.research.infra.jdbc;

import com.guidinglight.nexusquant.research.domain.ExecutionStrategyDefinitionDraft;
import com.guidinglight.nexusquant.research.domain.port.ExecutionStrategyDefinitionWriter;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcExecutionStrategyDefinitionWriter 负责把发布草稿写入执行域 strategy_definitions。
 */
@Repository
public class JdbcExecutionStrategyDefinitionWriter implements ExecutionStrategyDefinitionWriter {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * 显式指定运行时构造器，避免测试专用构造器干扰 Spring 自动装配。
     * Why:
     * 该类保留了一个可注入固定 Clock 的包级构造器给测试使用，
     * 如果不固定运行时入口，容器可能退回默认实例化路径并报“缺少无参构造器”。
     */
    @Autowired
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


