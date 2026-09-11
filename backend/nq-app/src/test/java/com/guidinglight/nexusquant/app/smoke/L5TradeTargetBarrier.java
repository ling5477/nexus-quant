package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 只匹配预定的逻辑fill和owner，不产生业务事实或替换生产返回值。 */
final class L5TradeTargetBarrier {
    private final long ownerPid;
    private final String fill;
    private final String trace;
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final CountDownLatch release = new CountDownLatch(1);

    L5TradeTargetBarrier(long ownerPid, String fill, String trace) {
        if (ownerPid <= 0 || fill == null || trace == null) throw new IllegalArgumentException("invalid target");
        this.ownerPid = ownerPid; this.fill = fill; this.trace = trace;
    }

    boolean claim(long pid, String observedFill, String observedTrace) {
        return pid == ownerPid && fill.equals(observedFill) && trace.equals(observedTrace)
                && claimed.compareAndSet(false, true);
    }

    void afterCommit(PaperTradeRecord trade) throws InterruptedException {
        if (!claim(ProcessHandle.current().pid(), trade.exchangeTradeId(), trade.traceId())) return;
        B0Fixture.require(!TransactionSynchronizationManager.isActualTransactionActive());
        System.out.println("L5_TARGET_MATCHED " + trade.tradeId() + " " + trade.orderId());
        System.out.flush();
        if (!awaitRelease(20, TimeUnit.SECONDS)) throw new IllegalStateException("BLOCKED / FAULT_TARGET_NOT_REACHED: release timeout");
    }

    boolean awaitRelease(long timeout, TimeUnit unit) throws InterruptedException { return release.await(timeout, unit); }
    void release() { release.countDown(); }
}
