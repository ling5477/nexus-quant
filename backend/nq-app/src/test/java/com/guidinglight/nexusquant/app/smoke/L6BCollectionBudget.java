package com.guidinglight.nexusquant.app.smoke;

import java.time.Duration;
import java.util.function.LongSupplier;

/** 只读采集操作共享一个10秒诊断预算；不再把旧2秒拆分当作单次HTTP或SQL的SLA。 */
final class L6BCollectionBudget {
    private record Limit(long deadline,LongSupplier clock) { }
    private static final ThreadLocal<Limit> CURRENT=new ThreadLocal<>();
    static void begin(long deadline,LongSupplier clock) { if(CURRENT.get()!=null)throw new IllegalStateException("nested sampling");CURRENT.set(new Limit(deadline,clock)); }
    static Duration remaining() {
        var limit=CURRENT.get();
        long nanos=limit==null?L6BSlotSampler.INTERVAL:limit.deadline-limit.clock.getAsLong();
        if(nanos<=0)throw new IllegalStateException("L6_COLLECTION_DIAGNOSTIC_BUDGET_EXHAUSTED");
        return Duration.ofNanos(nanos);
    }
    static void end() { CURRENT.remove(); }
    static int querySeconds() { return Math.toIntExact(Math.ceilDiv(remaining().toNanos(),1_000_000_000L)); }
}
