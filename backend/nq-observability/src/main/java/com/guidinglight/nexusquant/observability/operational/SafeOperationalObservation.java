package com.guidinglight.nexusquant.observability.operational;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;

/** 隔离观测记录失败；本适配器不捕获业务代码的异常。 */
public final class SafeOperationalObservation implements OperationalObservation {
    private final OperationalObservation delegate;
    private final AtomicBoolean warned = new AtomicBoolean();

    public SafeOperationalObservation(OperationalObservation delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void record(Operation operation, Signal signal, long value) {
        try {
            delegate.record(operation, signal, value);
        } catch (RuntimeException ex) {
            if (warned.compareAndSet(false, true)) {
                // 每个调用方最多记录一次固定诊断，避免泄露异常、载荷或动态标识符。
                LoggerFactory.getLogger(SafeOperationalObservation.class)
                        .warn("operational_observation_unavailable; business execution continues");
            }
        }
    }
}
