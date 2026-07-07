package com.guidinglight.nexusquant.strategy.domain.port;

/**
 * ShadowRunOverviewQueryPort 是 GateS-1 Shadow Run overview 的只读查询端口。
 *
 * <p>用途：为 application service 提供本地 Shadow Run overview facts。实现必须只执行 SELECT，
 * 不得 INSERT/UPDATE/DELETE，不得创建 run/event/snapshot/report，不得调用 runner、scheduler、
 * adapter、credential store、order、account 或 ledger 服务。幂等性：同一数据库快照下重复调用只返回
 * 相同本地事实，不产生副作用。
 */
public interface ShadowRunOverviewQueryPort {

    /**
     * 加载 Shadow Run overview 所需的最小本地 facts。
     *
     * @return 当前 overview facts；空数据必须返回稳定空结构，不能抛 500 风格异常
     */
    ShadowRunOverviewFacts loadOverviewFacts();
}
