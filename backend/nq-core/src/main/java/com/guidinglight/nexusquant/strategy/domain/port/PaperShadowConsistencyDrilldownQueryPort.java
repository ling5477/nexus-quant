package com.guidinglight.nexusquant.strategy.domain.port;

import java.util.UUID;

/**
 * PaperShadowConsistencyDrilldownQueryPort 是 GateS-2 consistency drilldown 的只读查询端口。
 *
 * <p>实现必须只执行 SELECT；不得 INSERT/UPDATE/DELETE，不得创建 Shadow Run、event、snapshot 或
 * consistency report，不得调用 runner、scheduler、adapter、credential store、order、account 或 ledger 服务。
 */
public interface PaperShadowConsistencyDrilldownQueryPort {

    /**
     * 加载单个 Shadow Run 的 Paper vs Shadow drilldown facts。
     *
     * @param shadowRunId 本地 Shadow Run id
     * @return drilldown facts；run 不存在时返回 missingRun facts，由 application service 映射为 404
     */
    PaperShadowConsistencyDrilldownFacts loadDrilldownFacts(UUID shadowRunId);
}
