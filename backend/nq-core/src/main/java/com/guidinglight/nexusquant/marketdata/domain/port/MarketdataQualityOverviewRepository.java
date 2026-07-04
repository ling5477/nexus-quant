package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityBarScopeFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIngestionFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewQuery;

import java.util.List;

/**
 * MarketdataQualityOverviewRepository 暴露 Data Quality Center 所需的本地只读事实。
 * <p>
 * Why:
 * GateP Batch 2 overview 需要读取 bars、dataset coverage 与 ingestion runs，但 core 不能依赖 JDBC。
 * 该 port 不提供写方法、不提供 adapter/provider 方法，也不允许外部网络能力。
 */
public interface MarketdataQualityOverviewRepository {

    /**
     * 读取按 scope 聚合的本地 bar 事实。
     *
     * @param query 只读筛选条件，可跨 symbol / interval / dataset 聚合
     * @return 本地 bar 聚合事实；不存在数据时返回空列表
     */
    List<MarketdataQualityBarScopeFacts> loadBarScopeFacts(MarketdataQualityOverviewQuery query);

    /**
     * 读取 ingestion runs 的脱敏聚合事实。
     *
     * @param query 只读筛选条件
     * @return 最近成功、失败和最新 run id / status；不包含 raw summary 或 error payload
     */
    MarketdataQualityIngestionFacts loadIngestionFacts(MarketdataQualityOverviewQuery query);

    /**
     * 读取 dataset coverage 的最新覆盖聚合事实。
     *
     * @param query 只读筛选条件
     * @return dataset coverage 聚合；没有匹配 dataset 时返回 empty facts
     */
    MarketdataQualityDatasetCoverageFacts loadDatasetCoverageFacts(MarketdataQualityOverviewQuery query);
}
