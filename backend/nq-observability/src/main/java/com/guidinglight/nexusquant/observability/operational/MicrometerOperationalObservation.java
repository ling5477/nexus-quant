package com.guidinglight.nexusquant.observability.operational;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Locale;

/** 进程内有限指标集合；快照表示最近观测事实，不代表数据库实时积压量。 */
public final class MicrometerOperationalObservation implements OperationalObservation {
    private final MeterRegistry registry;
    private final Clock clock;
    private final Map<Operation, State> states = new EnumMap<>(Operation.class);

    public MicrometerOperationalObservation(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        for (Operation operation : Operation.values()) {
            states.put(operation, new State());
        }
    }

    @Override
    public void record(Operation operation, Signal signal, long value) {
        State state = states.get(operation);
        synchronized (state) {
            Tags tags = Tags.of("component", operation.component(), "operation", operation.operation());
            // 延迟注册也处于调用方的隔离边界内，注册失败不得影响业务结果。
            registerGauges(state, operation, tags);
            long now = clock.instant().getEpochSecond();
            if (signal == Signal.UNRESOLVED_SNAPSHOT) {
                state.unresolved.set(value);
            } else {
                state.counters.get(signal).increment(value);
            }
            if (signal == Signal.ATTEMPT) state.lastExecution.set(now);
            if (signal == Signal.SUCCESS) state.lastSuccess.set(now);
            if (signal == Signal.FAILURE) state.lastFailure.set(now);
            if (signal == Signal.DEGRADED || signal == Signal.UNRESOLVED) state.lastDegraded.set(now);
            state.observed = true;
        }
    }

    private void registerGauges(State state, Operation operation, Tags tags) {
        if (state.registered) return;
        gauge("nq.operational.last.execution", state.lastExecution, tags);
        gauge("nq.operational.last.success", state.lastSuccess, tags);
        gauge("nq.operational.last.failure", state.lastFailure, tags);
        gauge("nq.operational.last.degraded", state.lastDegraded, tags);
        if (operation == Operation.LEDGER_RECONCILE) {
            gauge("nq.operational.unresolved.snapshot", state.unresolved, tags);
        }
        // 首次执行时同时注册零值结果计数，避免尚未失败被误读为指标缺失。
        for (Signal signal : Signal.values()) {
            if (signal == Signal.UNRESOLVED_SNAPSHOT) continue;
            if (operation == Operation.CRITICAL_ALERT && signal != Signal.EMITTED) continue;
            if (operation != Operation.CRITICAL_ALERT && signal == Signal.EMITTED) continue;
            if (signal == Signal.UNRESOLVED && operation != Operation.OKX_RECONCILE
                    && operation != Operation.LEDGER_RECOVERY) continue;
            String name = switch (signal) {
                case UNRESOLVED -> "nq.operational.unresolved";
                case EMITTED -> "nq.operational.alerts";
                default -> "nq.operational.executions";
            };
            state.counters.put(signal, Counter.builder(name).tags(tags)
                    .tag("result", signal.name().toLowerCase(Locale.ROOT)).register(registry));
        }
        state.registered = true;
    }

    private void gauge(String name, AtomicLong value, Tags tags) {
        Gauge.builder(name, value, AtomicLong::doubleValue).tags(tags).register(registry);
    }

    /** 仅返回安全摘要；尚未观测到的时间戳和数量使用 -1 表示未知。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        states.forEach((operation, state) -> {
            synchronized (state) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("observed", state.observed);
                detail.put("lastExecutionEpochSeconds", state.lastExecution.get());
                detail.put("lastSuccessEpochSeconds", state.lastSuccess.get());
                detail.put("lastFailureEpochSeconds", state.lastFailure.get());
                detail.put("lastDegradedEpochSeconds", state.lastDegraded.get());
                Map<String, Double> totals = new LinkedHashMap<>();
                state.counters.forEach((signal, counter) -> totals.put(signal.name(), counter.count()));
                detail.put("totals", totals);
                if (operation == Operation.LEDGER_RECONCILE) detail.put("lastObservedUnresolved", state.unresolved.get());
                result.put(operation.operation(), detail);
            }
        });
        return result;
    }

    private static final class State {
        private final Map<Signal, Counter> counters = new EnumMap<>(Signal.class);
        private final AtomicLong lastExecution = new AtomicLong(-1);
        private final AtomicLong lastSuccess = new AtomicLong(-1);
        private final AtomicLong lastFailure = new AtomicLong(-1);
        private final AtomicLong lastDegraded = new AtomicLong(-1);
        private final AtomicLong unresolved = new AtomicLong(-1);
        private boolean registered;
        private boolean observed;
    }
}
