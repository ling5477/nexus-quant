package com.guidinglight.nexusquant.common.tenant;

import java.util.List;
import java.util.function.Supplier;

/** 只能按持久化业务身份恢复后台作用域，调用方不能指定目标租户或传递旧令牌。 */
public interface TenantWorkAccess {
    enum Kind { PAPER, STRATEGY, ORDER, STRATEGY_SCHEDULE, PAPER_SCHEDULE, ACCOUNT, EXPERIMENT }
    List<String> next(Kind kind, int limit);
    TenantExecutionIdentity restore(Kind kind, String workId);

    default <T> T execute(Kind kind, String workId, Supplier<T> action) {
        var identity = restore(kind, workId);
        try (var ignored = TenantContext.openExecution(identity)) { return action.get(); }
    }
}
