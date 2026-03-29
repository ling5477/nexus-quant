package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.SourceStrategySnapshot;

import java.util.Optional;

/**
 * SourceStrategySnapshotRepository 负责读取研究域允许消费的策略定义快照。
 */
public interface SourceStrategySnapshotRepository {

    Optional<SourceStrategySnapshot> findByStrategyId(String strategyId);
}


