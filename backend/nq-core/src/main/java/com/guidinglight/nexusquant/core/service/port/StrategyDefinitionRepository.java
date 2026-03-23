package com.guidinglight.nexusquant.core.service.port;

import com.guidinglight.nexusquant.core.model.StrategyDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * StrategyDefinitionRepository 定义策略定义持久化端口。
 */
public interface StrategyDefinitionRepository {

    void insert(StrategyDefinition definition);

    Optional<StrategyDefinition> findByStrategyId(String strategyId);

    Optional<StrategyDefinition> findByStrategyCode(String strategyCode);

    List<StrategyDefinition> listAll();

    boolean updateEnabled(String strategyId, boolean enabled, Instant updatedAt);
}
