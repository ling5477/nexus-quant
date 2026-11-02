package com.guidinglight.nexusquant.livecontrol.execution.application.port;

import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;

/**
 * 独立 execution worker 的 fail-closed 生命周期边界。
 *
 * <p>实现可以在 claim 前和 durable {@code SEND_STARTED} 后、fake mutation 前重新核验
 * release/kill authority，也可以在测试环境注入进程终止。回调不得执行真实 provider 调用，
 * 默认 NOOP 保持既有 application service 调用行为。</p>
 */
public interface ExecutionAttemptLifecycle {

    ExecutionAttemptLifecycle NOOP = new ExecutionAttemptLifecycle() {
    };

    default void beforeClaim() {
    }

    default void afterClaim(ExecutionIntent intent) {
    }

    default void afterSendStarted(ExecutionIntent intent) {
    }

    default void beforeFakeMutation(ExecutionIntent intent) {
    }

    default void afterFakeMutation(ExecutionIntent intent, FakeExchangeResult result) {
    }
}
