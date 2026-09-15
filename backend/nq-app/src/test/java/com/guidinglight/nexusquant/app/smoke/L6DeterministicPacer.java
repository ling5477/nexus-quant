package com.guidinglight.nexusquant.app.smoke;

import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

/** slot 属于绝对时钟；不递归移动deadline，间隔不足的slot丢弃而不积累债务。 */
final class L6DeterministicPacer {
    record Slot(long slotIndex, long scheduledElapsed, long actualElapsed, String decision,
                double driftMillis, String logicalOrderId, long completedElapsed, String reason) { }
    @FunctionalInterface interface Emit { String send(long slot) throws Exception; }
    @FunctionalInterface interface Record { void save(Slot slot) throws Exception; }
    /** 只表示明确未产生admission的busy拒绝，不承接超时或UNKNOWN外部结果。 */
    static final class AdmissionBusy extends Exception { }
    private final LongSupplier clock;
    private final long start, interval, producerEnd, total;
    private final BooleanSupplier admission;
    private long index;
    private long lastActual = Long.MIN_VALUE;

    L6DeterministicPacer(LongSupplier clock, long start, long interval, L6DurationContract duration) {
        this(clock, start, interval, duration, () -> true);
    }
    L6DeterministicPacer(LongSupplier clock, long start, long interval, L6DurationContract duration, BooleanSupplier admission) {
        if (interval < 1_000_000_000L || duration.total() / interval > 3000) throw new IllegalArgumentException("pacer bounds");
        this.clock = clock; this.start = start; this.interval = interval;
        this.producerEnd = duration.activeEnd(); this.total = duration.total();
        this.admission = admission;
    }
    long nextElapsed() {
        return index * interval;
    }
    void poll(boolean backpressure, Emit emit, Record record) throws Exception {
        long now = clock.getAsLong() - start;
        if (now < 0) throw new IllegalStateException("monotonic clock regressed");
        while (index * interval < Math.min(now, total) && index * interval + interval <= now) {
            long due = index++ * interval;
            record.save(slot(index - 1, due, now, due >= producerEnd ? "SKIPPED_PHASE" : "PAUSED_BACKPRESSURE", null, now, "MISSED_SLOT_NO_DEBT"));
        }
        long due = index * interval;
        if (due >= total || now < due || now < nextElapsed()) return;
        index++;
        if (now >= total || due >= producerEnd || now >= producerEnd) {
            record.save(slot(index - 1, due, now, now >= total ? "STOPPED" : "SKIPPED_PHASE", null, now, "PRODUCER_DISABLED")); return;
        }
        if (backpressure) { record.save(slot(index - 1, due, now, "PAUSED_BACKPRESSURE", null, now, "WORKLOAD_GATE")); return; }
        if (lastActual != Long.MIN_VALUE && now - lastActual < interval) {
            record.save(slot(index - 1, due, now, "PAUSED_BACKPRESSURE", null, now, "MINIMUM_DISPATCH_INTERVAL")); return;
        }
        // emit前重新读取monotonic authority；迟到的sampler或检查点不能延长admission。
        long dispatchNow = clock.getAsLong() - start;
        if (dispatchNow >= producerEnd || !admission.getAsBoolean()) {
            record.save(slot(index - 1, due, dispatchNow, "SKIPPED_PHASE", null, dispatchNow, "PRODUCER_DISABLED")); return;
        }
        String id;
        try { id = emit.send(index - 1); }
        catch (AdmissionBusy rejected) {
            record.save(slot(index - 1, due, now, "PAUSED_BACKPRESSURE", null,
                    clock.getAsLong() - start, "STRATEGY_RUN_ACTIVE"));
            return;
        }
        if (id == null || id.isBlank()) throw new IllegalStateException("emission identity missing");
        long completed = clock.getAsLong() - start;
        if (completed >= producerEnd) {
            record.save(slot(index - 1, due, now, "STOPPED", id, completed, "ADMISSION_CROSSED_PHASE_BOUNDARY"));
            throw new IllegalStateException("L6_ADMISSION_CROSSED_PHASE_BOUNDARY");
        }
        lastActual = now;
        record.save(slot(index - 1, due, now, "EMITTED", id, completed, "READY"));
    }
    private Slot slot(long i, long due, long now, String decision, String id, long completed, String reason) {
        return new Slot(i, due, now, decision, (now - due) / 1e6, id, completed, reason);
    }
}
