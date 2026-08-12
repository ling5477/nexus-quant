package com.guidinglight.nexusquant.livecontrol.execution.application.port;

import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;

/** Application-owned mutation/query contract；不暴露 provider DTO 或 credential。 */
public interface FakeExchangeMutationPort {

    FakeExchangeResult place(ExecutionIntent intent);

    FakeExchangeResult cancel(ExecutionIntent intent);

    FakeExchangeQueryResult queryByClientOrderId(String clientOrderId);
}
