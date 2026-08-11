package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * admission-sensitive mutation 的 state-first 锁协调边界。
 *
 * <p>实现必须对 publish ID 去重、升序排序、有界校验，并在同一事务内先锁
 * {@code strategy_release_admission_state} 后执行 source mutation。实现不得直接修改 revision；
 * revision 只由数据库 source trigger 负责，避免 double bump。
 */
public interface AdmissionMutationCoordinator {

    /** 所有 application/DB fan-out 的不可放宽硬上限。 */
    int HARD_MAX_FAN_OUT = 256;

    <T> T withLockedAdmissionStates(Collection<String> publishRecordIds, Supplier<T> mutation);

    default void withLockedAdmissionStates(Collection<String> publishRecordIds, Runnable mutation) {
        withLockedAdmissionStates(publishRecordIds, () -> {
            mutation.run();
            return null;
        });
    }
}
