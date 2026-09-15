package com.guidinglight.nexusquant.app.smoke;

import java.util.concurrent.TimeUnit;

/** 整个双actor串行扫描完成后再等待5秒；恢复路径共享同一个门，不补跑错过的时隙。 */
final class L6BReconcileTiming {
    static final long DELAY_NANOS = TimeUnit.SECONDS.toNanos(5);
    private long next;
    private boolean running;

    synchronized long next() { return next; }

    synchronized void begin(long now) {
        if (running || now < next) throw new IllegalStateException("L6_RECONCILE_OVERLAP_OR_EARLY_START");
        running = true;
    }

    synchronized void complete(long now) {
        if (!running) throw new IllegalStateException("L6_RECONCILE_NOT_RUNNING");
        running = false;
        next = Math.addExact(now, DELAY_NANOS);
    }
}
