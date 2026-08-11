package com.guidinglight.nexusquant.strategy.strategyrelease.application;

/**
 * 按 publish anchor 加载 Release admission preview 服务端事实的 SELECT-only port。
 *
 * <p>实现不得写库、调用 runner/scheduler、访问 credential/private endpoint 或读取客户端声明的
 * validation、window、filesystem、artifact 或 side-effect truth。
 */
public interface StrategyReleaseAdmissionPreviewFactsRepository {

    /**
     * 加载指定 publish record 的 validation/window/safety 快照。
     *
     * @param publishRecordId canonical publish anchor
     * @return 服务端事实；不存在或无法安全解析时返回 missing 快照
     */
    StrategyReleaseAdmissionPreviewFacts loadByPublishRecordId(String publishRecordId);
}
