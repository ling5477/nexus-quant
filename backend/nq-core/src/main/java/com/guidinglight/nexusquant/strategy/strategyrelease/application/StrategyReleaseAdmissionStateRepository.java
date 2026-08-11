package com.guidinglight.nexusquant.strategy.strategyrelease.application;

/** Strategy Release admission state 的写边界。 */
public interface StrategyReleaseAdmissionStateRepository {

    /** 加载当前 admission generation；不存在必须 fail-closed。 */
    StrategyReleaseAdmissionState loadByPublishRecordId(String publishRecordId);

    /**
     * 对 server-controlled、已验证 release 执行一次性 identity binding。
     *
     * <p>实现必须锁 state、重载 publish/evaluation/dataset facts、原子绑定 quartet 并 bump revision；
     * 已绑定、事实漂移或写失败均 fail-closed。
     */
    StrategyReleaseAdmissionState bindVerifiedReleaseIdentity(VerifiedStrategyReleaseIdentity identity);
}
