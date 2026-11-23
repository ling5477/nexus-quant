package com.guidinglight.nexusquant.trading.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.trading.domain.EffectiveOrderParameters;
import java.util.Optional;

/** 交易准备所需的最小绑定事实；实现与策略恢复共用同一持久 owner 和事务锁。 */
public interface StrategyOrderBindingRepository {
    StrategyRun lockRun(String strategyRunId);

    Optional<StrategyDispatchWork> findWork(String strategyRunId);

    boolean beginDispatch(String strategyRunId);

    Optional<EffectiveOrderParameters> findEffective(String strategyRunId);

    /** 在原 run 锁内一次绑定；竞争者必须读取 winner 的决定。 */
    void bindEffective(String strategyRunId, EffectiveOrderParameters parameters);
}
