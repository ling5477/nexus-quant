package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreation;

import java.util.Optional;
import java.util.UUID;

/** 编号锁和完成回执均依附本地业务事务；查询他人的编号不读取其私有原输入。 */
public interface StrategyScheduleCreationRepository {
    void lock(UUID id);

    Optional<Identity> identity(UUID id);

    Optional<StrategyScheduleCreation> findOwned(UUID id, long owner);

    void complete(StrategyScheduleCreation creation);

    record Identity(long ownerUserId, String inputSha256) {}
}
