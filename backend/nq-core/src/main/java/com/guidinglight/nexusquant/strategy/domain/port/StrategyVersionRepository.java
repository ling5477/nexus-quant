package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersion;

import java.util.List;
import java.util.Optional;

/**
 * StrategyVersionRepository 定义策略版本持久化端口。
 *
 * Why:
 * core 只依赖端口，不依赖 JDBC。GateI-1 的 SQL、索引和 upsert 细节由 infra adapter 承载。
 */
public interface StrategyVersionRepository {

    /**
     * 插入新的策略版本。
     *
     * @param strategyVersion 已归一化并通过业务校验的策略版本
     */
    void insert(StrategyVersion strategyVersion);

    /**
     * 按策略编码查询版本列表。
     *
     * @param strategyCode 策略编码
     * @return 按版本号倒序排列的版本列表
     */
    List<StrategyVersion> listByStrategyCode(String strategyCode);

    /**
     * 按策略版本 ID 查询版本详情。
     *
     * @param strategyVersionId 策略版本 ID
     * @return 版本详情；不存在时为空
     */
    Optional<StrategyVersion> findById(String strategyVersionId);

    /**
     * 查询某个策略的当前最大版本号。
     *
     * @param strategyCode 策略编码
     * @return 当前最大版本号；没有版本时返回 0
     */
    int maxVersion(String strategyCode);
}
