package com.guidinglight.nexusquant.livecontrol.domain.port;

import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** LIVE control-plane facts 的领域 port；JDBC 与 transaction manager 不得反向渗入该契约。 */
public interface LiveControlRepository {

    /** 返回当前数据库事务时间，避免信任客户端 wall clock。 */
    java.time.Instant currentTime();

    void createRiskLimitSet(RiskLimitSet riskLimitSet);

    Optional<RiskLimitSet> findRiskLimitSet(UUID riskLimitSetId);

    /**
     * 在创建会话的同一短事务内锁定并核对 account、credential、release admission 与 risk facts。
     * 实现不得读取 credential material；任一引用或冻结 digest 不一致时返回 false。
     */
    boolean lockAndValidateSessionReferences(LiveSession session);

    void createSession(LiveSession session);

    Optional<LiveSession> findSession(UUID sessionId);

    Optional<LiveSession> lockSession(UUID sessionId);

    boolean compareAndSetSession(LiveSession expected, LiveSession updated);

    LiveSessionEvent appendSessionEvent(LiveSessionEvent unsequencedEvent);

    void appendApproval(OperatorApproval approval);

    Optional<OperatorApproval> findApproval(UUID approvalId);

    Optional<OperatorApproval> findValidApproval(LiveSession session, Instant now);
}
