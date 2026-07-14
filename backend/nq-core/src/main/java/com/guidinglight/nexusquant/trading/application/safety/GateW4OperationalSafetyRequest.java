package com.guidinglight.nexusquant.trading.application.safety;

import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;

import java.time.Instant;
import java.util.Objects;

/** Internal-only GateW-4 safety assessment 输入；不包含 credential、账户或订单 payload。 */
public record GateW4OperationalSafetyRequest(
        KillSwitchSnapshot killSwitchSnapshot,
        GateW4OperationalSafetyFactBundle facts,
        Instant evaluatedAt
) {

    /** 固定一次评估的 snapshot、evidence facts 与受控时间。 */
    public GateW4OperationalSafetyRequest {
        Objects.requireNonNull(killSwitchSnapshot, "killSwitchSnapshot must not be null");
        Objects.requireNonNull(facts, "facts must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    }
}
