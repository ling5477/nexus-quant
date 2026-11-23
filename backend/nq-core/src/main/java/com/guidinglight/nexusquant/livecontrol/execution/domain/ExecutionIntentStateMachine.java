package com.guidinglight.nexusquant.livecontrol.execution.domain;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** 与 V39 trigger 同源的 fail-closed 状态矩阵。 */
public final class ExecutionIntentStateMachine {

    private static final Map<ExecutionIntentState, EnumSet<ExecutionIntentState>> LEGAL = legalTransitions();

    public ExecutionIntentState transition(ExecutionIntentState current, ExecutionIntentState target) {
        if (!LEGAL.getOrDefault(current, EnumSet.noneOf(ExecutionIntentState.class)).contains(target)) {
            throw new LiveControlException(
                    "EXECUTION_INTENT_ILLEGAL_TRANSITION",
                    "illegal execution intent transition: " + current + " -> " + target
            );
        }
        return target;
    }

    private static Map<ExecutionIntentState, EnumSet<ExecutionIntentState>> legalTransitions() {
        EnumMap<ExecutionIntentState, EnumSet<ExecutionIntentState>> values =
                new EnumMap<>(ExecutionIntentState.class);
        values.put(ExecutionIntentState.CREATED,
                EnumSet.of(ExecutionIntentState.CLAIMED, ExecutionIntentState.CANCELLED));
        values.put(ExecutionIntentState.CLAIMED,
                EnumSet.of(ExecutionIntentState.CLAIMED, ExecutionIntentState.SEND_STARTED,
                        ExecutionIntentState.CANCELLED));
        values.put(ExecutionIntentState.SEND_STARTED,
                EnumSet.of(ExecutionIntentState.SEND_SUCCEEDED, ExecutionIntentState.UNKNOWN,
                        ExecutionIntentState.FAILED));
        values.put(ExecutionIntentState.UNKNOWN, EnumSet.of(ExecutionIntentState.RECONCILED));
        return Map.copyOf(values);
    }
}
