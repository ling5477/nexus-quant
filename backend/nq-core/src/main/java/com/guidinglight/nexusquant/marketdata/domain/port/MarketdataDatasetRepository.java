package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetCoverage;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * MarketdataDatasetRepository 定义历史行情接入数据集与质量覆盖的持久化端口。
 *
 * <p>Why: core 只表达 dataset 业务语义和端口，JDBC 统计 SQL 必须留在 infra，避免 `nq-core` 依赖数据库实现。
 */
public interface MarketdataDatasetRepository {

    void insert(MarketdataDataset dataset);

    Optional<MarketdataDataset> findByDatasetId(UUID datasetId);

    List<MarketdataDataset> list(
            String exchangeCode, String marketType, String symbol, String interval);

    MarketdataDatasetCoverage calculateCoverage(MarketdataDataset dataset, Instant calculatedAt);

    void insertCoverage(MarketdataDatasetCoverage coverage);

    /** 先锁发布准入状态再锁数据集；回调内的覆盖计算、历史与主状态必须在同一事务提交。 */
    <T> T withQualityMutationLock(UUID datasetId, Function<QualityWriter, T> mutation);

    /** 写入器只对本次已锁定的数据集有效，避免业务回调重新获取准入锁而颠倒锁顺序。 */
    @FunctionalInterface
    interface QualityWriter {
        boolean update(
                MarketdataDatasetStatus status,
                MarketdataQualityStatus qualityStatus,
                long barCount,
                long gapCount,
                Instant updatedAt);
    }

    boolean updateQuality(
            UUID datasetId,
            MarketdataDatasetStatus status,
            MarketdataQualityStatus qualityStatus,
            long barCount,
            long gapCount,
            Instant updatedAt);
}
