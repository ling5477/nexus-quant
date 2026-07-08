package com.guidinglight.nexusquant.monitoring.domain.port;

/**
 * IncidentReplayOverviewQueryPort 是 GateS-6 Incident / Replay overview 的只读查询端口。
 *
 * <p>实现只能读取允许的本地事实表，不得创建 incident、alert、replay，也不得调用 runner、
 * scheduler、adapter、credential、order、account 或 ledger 服务。
 */
public interface IncidentReplayOverviewQueryPort {

    /**
     * 加载 Incident / Replay overview 所需的本地事实投影。
     *
     * @return SELECT-only facts；无数据时返回稳定空结构
     */
    IncidentReplayOverviewFacts loadOverviewFacts();
}
