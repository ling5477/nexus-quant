package com.guidinglight.nexusquant.core.recovery;

import java.time.Instant;

/**
 * NoopRecoveryService 是无副作用占位实现。
 *
 * Why:
 * Gate A 不落真实回放逻辑，但 app 装配需要一个可运行的默认实现。
 */
public class NoopRecoveryService implements RecoveryService {

    @Override
    public RecoveryReport rebuild(String traceId) {
        Instant now = Instant.now();
        return new RecoveryReport(now, now, 0L, 0L, 0L, 0L, traceId);
    }
}
