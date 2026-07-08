package com.guidinglight.nexusquant.strategy.domain.port;

/**
 * ConsistencyEvidenceOverviewQueryPort 是 GateT-2 consistency evidence overview 的只读查询端口。
 *
 * <p>实现必须只执行 SELECT；不得 INSERT/UPDATE/DELETE，不得创建 consistency report、snapshot、event、
 * Paper run 或 Shadow run，不得调用 runner、scheduler、adapter、credential store、order、account 或 ledger 服务。
 */
public interface ConsistencyEvidenceOverviewQueryPort {

    /**
     * 加载 bounded consistency evidence overview facts。
     *
     * @return SELECT-only facts；没有本地 consistency evidence 时返回空集合
     */
    ConsistencyEvidenceOverviewFacts loadOverviewFacts();
}
