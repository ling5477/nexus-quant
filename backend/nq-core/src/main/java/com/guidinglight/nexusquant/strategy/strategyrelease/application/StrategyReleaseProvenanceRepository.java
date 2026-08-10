package com.guidinglight.nexusquant.strategy.strategyrelease.application;

/**
 * Strategy Release provenance 的只读 repository port。
 *
 * <p>实现必须只读取现有 publish/run/evaluation/version/dataset 事实；不得写库、创建 Shadow Run、
 * 调用 scheduler/Python/交易所或读取 credential。
 */
public interface StrategyReleaseProvenanceRepository {

    /**
     * 按 canonical publish identity 读取一条 provenance 聚合事实。
     *
     * @param publishRecordId {@code backtest_publish_records.publish_record_id}
     * @return 存在或 missing 的安全事实对象
     */
    StrategyReleaseProvenanceFacts loadByPublishRecordId(String publishRecordId);
}
