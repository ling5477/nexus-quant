package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 冻结库存时间模型与完整10分钟窗口共同约束余量；不外推单个10秒噪声。 */
final class L6BProjectionGuard {
    static final String RESULT = "BLOCKED / PG_TMPFS_CAPACITY_BUDGET_AT_RISK";
    static final long DRAIN_LIMIT = 20_000_000_000L;
    static final class DrainComplete extends RuntimeException { }
    static final class ProducerStopped extends RuntimeException { }
    record Point(long scheduledMillis, long used, long orders, int phase) { }
    private final L6BContract contract;
    private final long runCapacity;
    private final ArrayDeque<Point> recent = new ArrayDeque<>();
    private final long[] rates;
    private long lastScheduled = -10_000, lastOrders;
    private int observationGaps;
    private long stoppedAt = -1;
    private boolean drainBudgetUnavailable;
    private ObjectNode last;
    private final List<ObjectNode> history = new ArrayList<>();

    L6BProjectionGuard(L6BContract contract,long capacity) {
        this.contract=contract;this.runCapacity=capacity;
        rates=new long[]{contract.base.rate(0),contract.base.rate(1),contract.base.rate(2)};
    }
    synchronized boolean stopped() { return stoppedAt >= 0; }
    synchronized boolean producerAllowed() { return !stopped(); }
    synchronized long stoppedAt() { return stoppedAt; }
    synchronized boolean drainExpired(long now) { return stopped() && (drainBudgetUnavailable || now - stoppedAt >= DRAIN_LIMIT); }
    synchronized void produce(L6StoragePhaseController.Admission send) throws Exception {
        if (stopped()) throw new ProducerStopped(); send.send();
    }

    synchronized long need(long now, long orders, long backlog) {
        L6PgCapacityContract.require(now >= 0 && now <= contract.timing.total() && orders >= 0
                && orders <= contract.orders && backlog >= 0 && backlog <= orders);
        long result = (contract.reserve+contract.restartStorage);
        if (stopped()) {
            long remaining = Math.max(0, DRAIN_LIMIT - (now - stoppedAt));
            result = Math.addExact(result, L6PgCapacityContract.ceilRatio(BigInteger.valueOf(remaining)
                    .multiply(BigInteger.valueOf(orders + 1)).multiply(BigInteger.valueOf(rates[2])), 1_000_000_000L));
            return Math.addExact(result, Math.multiplyExact(backlog, contract.base.backlogUnit()));
        }
        if (now < contract.timing.warmupNanos()) result = Math.addExact(result, contract.growth(now, contract.timing.warmupNanos(), rates[0], -1));
        if (now < contract.timing.activeEnd()) result = Math.addExact(result, contract.growth(Math.max(now, contract.timing.warmupNanos()), contract.timing.activeEnd(), rates[1], -1));
        // ACTIVE尚有潜在订单；DRAIN严格只使用已存在订单库存及未完成账务的显式余量。
        result = Math.addExact(result, contract.growth(Math.max(now, contract.timing.activeEnd()), contract.timing.total(),
                rates[2], now < contract.timing.activeEnd() ? contract.orders : orders));
        return Math.addExact(result, Math.multiplyExact(backlog, contract.base.backlogUnit()));
    }

    synchronized void observe(JsonNode sample, long elapsed) {
        var v = sample.path("sources").path("postgres").path("values");
        if (!"PERIODIC".equals(sample.path("sampleType").asText()) || !"MEASURED".equals(sample.path("status").asText())) throw new IllegalStateException("L6_PROJECTION_SAMPLE_INVALID");
        long capacity = nonnegative(v,"pgTmpfsCapacityBytes"), used = nonnegative(v,"pgTmpfsUsedBytes"), free = nonnegative(v,"pgTmpfsFreeBytes");
        long orders = nonnegative(v,"orders"), backlog = nonnegative(v,"backlog"), at = nonnegative(sample,"scheduledElapsedMillis");
        if (capacity != runCapacity || Math.addExact(used, free) != capacity || at > contract.timing.total()/1_000_000) throw new IllegalStateException("L6_PROJECTION_CAPACITY_DRIFT");
        int phase = stopped() ? 2 : at < contract.timing.warmupNanos()/1_000_000 ? 0 : at < contract.timing.activeEnd()/1_000_000 ? 1 : 2;
        if (at%10_000!=0 || at<=lastScheduled || orders<lastOrders) throw new IllegalStateException("L6_PROJECTION_CADENCE_OR_INVENTORY_DRIFT");
        if (elapsed < at * 1_000_000) throw new IllegalStateException("L6_PROJECTION_STALE_SAMPLE");
        boolean gap=at-lastScheduled!=10_000 || "SLOT_OVERRUN".equals(sample.path("slotStatus").asText());
        // 孤立缺槽只中断实测增长窗口；冻结rate和scheduled时刻的保守余量不减少。
        if(gap) { recent.clear();observationGaps++; }
        lastScheduled=at;lastOrders=orders;
        recent.addLast(new Point(at, used, orders, phase));
        if (recent.size() > 61) recent.removeFirst();
        if (!gap && recent.size() == 61 && recent.getFirst().phase() == phase) {
            long exposure = 0; Point previous = null;
            for (Point point : recent) {
                if (previous != null) exposure = Math.addExact(exposure, Math.multiplyExact(10, previous.orders()+1));
                previous = point;
            }
            long growth = Math.max(0, used - recent.getFirst().used());
            rates[phase] = Math.max(rates[phase], L6PgCapacityContract.ceilRatio(BigInteger.valueOf(growth), exposure));
        }
        // free在采集开始之后取得；用scheduled时刻计算剩余量，避免漏算采集期间增长。
        long required = need(at * 1_000_000, orders, backlog);
        if (free < required && stoppedAt < 0) stoppedAt = elapsed;
        if (stopped()) {
            long drainNeed = need(Math.min(elapsed, contract.timing.total()), orders, backlog) - (contract.reserve+contract.restartStorage);
            // 若连有界drain本身也无余量，登记DRAIN后立即清理，禁止继续写到ENOSPC。
            if (free < drainNeed) drainBudgetUnavailable = true;
        }
        last = new ObjectMapper().createObjectNode().put("elapsedNanos", elapsed).put("phase", phase == 0 ? "WARMUP" : phase == 1 ? "ACTIVE" : "DRAIN")
                .put("freeBytes", free).put("usedBytes", used).put("runCapacityBytes", runCapacity)
                .put("freeMinusNeedBytes", free-required).put("projectedRemainingStorageNeed", required).put("orders", orders).put("backlog", backlog)
                .put("warmupRate", rates[0]).put("activeRate", rates[1]).put("drainRate", rates[2])
                .put("samplingGap",gap).put("growthWindowPoints",recent.size()).put("observationGaps",observationGaps)
                .put("producerDisabled", stopped() || elapsed >= contract.timing.activeEnd()).put("triggered", stopped());
        if (history.size() >= contract.samples+1) throw new IllegalStateException("L6_PROJECTION_HISTORY_BOUND");
        history.add(last.deepCopy());
    }

    private static long nonnegative(JsonNode n, String key) {
        JsonNode value = n.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw new IllegalStateException("L6_PROJECTION_MEASUREMENT_UNAVAILABLE");
        return value.longValue();
    }
    synchronized ObjectNode evidence() {
        var n = new ObjectMapper().createObjectNode().put("triggered", stopped()).put("stoppedAtElapsedNanos", stoppedAt)
                .put("drainBudgetUnavailable", drainBudgetUnavailable)
                .put("result", stopped() ? RESULT : "MEASURED");
        n.set("samples", new ObjectMapper().valueToTree(history)); return n;
    }
}
