package com.guidinglight.nexusquant.livecontrol.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * LiveSession 的唯一领域状态机。应用与 JDBC 只能消费该判定，不能各自复制业务迁移规则。
 */
public final class LiveSessionStateMachine {

    private static final Map<LiveSessionState, Map<LiveSessionCommand, LiveSessionState>> TRANSITIONS = transitions();

    public LiveSessionState transition(LiveSessionState current, LiveSessionCommand command) {
        if (current.terminal()) {
            throw new LiveControlException("LIVE_SESSION_TERMINAL", "terminal live session cannot transition");
        }
        if (command == LiveSessionCommand.KILL) {
            return LiveSessionState.KILLED;
        }
        if (command == LiveSessionCommand.FAIL && Set.of(
                LiveSessionState.APPROVED,
                LiveSessionState.LIVE_WARMUP,
                LiveSessionState.LIVE_ACTIVE,
                LiveSessionState.LIVE_PAUSED
        ).contains(current)) {
            return LiveSessionState.FAILED;
        }
        LiveSessionState target = TRANSITIONS.getOrDefault(current, Map.of()).get(command);
        if (target == null) {
            throw new LiveControlException(
                    "LIVE_SESSION_ILLEGAL_TRANSITION",
                    "illegal live session transition: " + current + " / " + command
            );
        }
        return target;
    }

    private static Map<LiveSessionState, Map<LiveSessionCommand, LiveSessionState>> transitions() {
        Map<LiveSessionState, Map<LiveSessionCommand, LiveSessionState>> result =
                new EnumMap<>(LiveSessionState.class);
        put(result, LiveSessionState.APPROVAL_PENDING,
                LiveSessionCommand.APPROVE, LiveSessionState.APPROVED,
                LiveSessionCommand.REJECT, LiveSessionState.REJECTED);
        put(result, LiveSessionState.APPROVED,
                LiveSessionCommand.APPROVAL_EXPIRED, LiveSessionState.APPROVAL_PENDING,
                LiveSessionCommand.START, LiveSessionState.LIVE_WARMUP);
        put(result, LiveSessionState.LIVE_WARMUP,
                LiveSessionCommand.ACTIVATE, LiveSessionState.LIVE_ACTIVE,
                LiveSessionCommand.PAUSE, LiveSessionState.LIVE_PAUSED);
        put(result, LiveSessionState.LIVE_ACTIVE,
                LiveSessionCommand.PAUSE, LiveSessionState.LIVE_PAUSED,
                LiveSessionCommand.STOP, LiveSessionState.LIVE_STOPPED);
        put(result, LiveSessionState.LIVE_PAUSED,
                LiveSessionCommand.RESUME, LiveSessionState.LIVE_ACTIVE,
                LiveSessionCommand.STOP, LiveSessionState.LIVE_STOPPED);
        put(result, LiveSessionState.LIVE_STOPPED,
                LiveSessionCommand.BEGIN_RECONCILE, LiveSessionState.LIVE_RECONCILING);
        put(result, LiveSessionState.LIVE_RECONCILING,
                LiveSessionCommand.RECONCILE_PASS, LiveSessionState.LIVE_RECONCILED,
                LiveSessionCommand.RECONCILE_BLOCK, LiveSessionState.RECONCILIATION_BLOCKED);
        put(result, LiveSessionState.RECONCILIATION_BLOCKED,
                LiveSessionCommand.RESOLVE_AND_CLOSE, LiveSessionState.LIVE_RECONCILED);
        return Map.copyOf(result);
    }

    private static void put(
            Map<LiveSessionState, Map<LiveSessionCommand, LiveSessionState>> target,
            LiveSessionState from,
            LiveSessionCommand commandOne,
            LiveSessionState stateOne,
            LiveSessionCommand commandTwo,
            LiveSessionState stateTwo
    ) {
        EnumMap<LiveSessionCommand, LiveSessionState> values = new EnumMap<>(LiveSessionCommand.class);
        values.put(commandOne, stateOne);
        values.put(commandTwo, stateTwo);
        target.put(from, Map.copyOf(values));
    }

    private static void put(
            Map<LiveSessionState, Map<LiveSessionCommand, LiveSessionState>> target,
            LiveSessionState from,
            LiveSessionCommand command,
            LiveSessionState state
    ) {
        target.put(from, Map.of(command, state));
    }
}
