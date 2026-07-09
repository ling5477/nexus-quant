package com.guidinglight.nexusquant.monitoring.domain.port;

/**
 * IncidentReplayReviewOverviewQueryPort 是 GateT-3 Incident / Replay Review overview 的只读查询端口。
 *
 * <p>实现只能读取允许的本地事实表，不得创建 incident、alert、replay、review、acknowledge、
 * escalation 或 closeout 记录，也不得调用 runner、scheduler、adapter、credential、order、account 或 ledger 服务。
 */
public interface IncidentReplayReviewOverviewQueryPort {

    /**
     * 加载 GateT-3 review overview 所需的本地事实投影。
     *
     * @return SELECT-only facts；无数据时返回稳定空结构
     */
    IncidentReplayReviewOverviewFacts loadOverviewFacts();
}
