package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.trading.domain.EffectiveOrderParameters;
import java.util.List;
import java.util.Optional;

/** Strategy 生命周期的持久事实端口；扫描预留不构成订单或外部发送资格。 */
public interface StrategyRunExecutionRepository {
    Optional<StrategyDispatchWork> findWork(String strategyRunId);

    Optional<EffectiveOrderParameters> findEffective(String strategyRunId);

    /** 必须在本地 prepare 事务内锁原 run；调用方随后重新读取唯一 Order。 */
    StrategyRun lockRun(String strategyRunId);

    /** 与 Order/初始 authority 同事务；不能单独提交无法继续的 DISPATCHING。 */
    boolean beginDispatch(String strategyRunId);

    /** 从锁定的 Order/Trade/authority 事实收敛同 run；不使用旧 callback 的状态猜测。 */
    boolean project(String strategyRunId);

    /** 独立短事务推进循环检查游标；每批最多 50，返回后才能调用网络。 */
    List<String> reserveCandidates(int limit);

    List<String> findCandidates(String strategyId, int limit);
}
