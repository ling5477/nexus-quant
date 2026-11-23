package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.StrategyVersionSnapshotView;

import java.util.Optional;

/**
 * StrategyVersionSnapshotQueryPort 定义发布链路读取策略版本快照的端口。
 *
 * Why:
 * 发布服务只需要确认版本存在并固化快照，不应该依赖 `nq-core` 的完整策略版本管理服务，
 * 也不应该在 application 层直接写 SQL。
 */
public interface StrategyVersionSnapshotQueryPort {

    /**
     * 按策略版本 ID 查询可发布快照。
     *
     * @param strategyVersionId 策略版本 ID
     * @return 策略版本快照；不存在时为空
     */
    Optional<StrategyVersionSnapshotView> findById(String strategyVersionId);
}
