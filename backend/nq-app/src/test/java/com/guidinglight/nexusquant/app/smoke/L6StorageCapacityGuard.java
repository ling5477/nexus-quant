package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 仅限存储校准的局部停止线；不是正式容量推导或整个60分钟的projection。 */
final class L6StorageCapacityGuard {
    // 两个16MiB WAL段作为本次观察/退出的最小余量，不据此冻结正式tmpfs容量。
    static final long MINIMUM_RESERVE = 32L * 1024 * 1024;
    static final long DRAIN_LIMIT_NANOS = 20_000_000_000L;
    private long previousUsed = -1, previousMillis = -1, maximumIntervalGrowth;
    private long stoppedAt = -1;
    private boolean emergency;
    private ObjectNode trigger;
    @FunctionalInterface interface Production { void start() throws Exception; }
    static final class ProducerStopped extends RuntimeException { }
    static final class DrainComplete extends RuntimeException { }

    synchronized void produce(Production production) throws Exception {
        if (!producerAllowed()) throw new ProducerStopped();
        // 锁只覆盖写入一次命令，不覆盖外部结果等待，风险采样不会被75秒默认等待阻塞。
        production.start();
    }
    synchronized String phase(String normal) { return stopped() ? "DRAIN" : normal; }
    synchronized String result() { return stopped() ? "BLOCKED / STORAGE_CALIBRATION_CAPACITY_AT_RISK" : "RUNNING"; }

    synchronized void observe(JsonNode sample, long now) {
        var value = sample.path("sources").path("postgres").path("values");
        long used = value.path("pgTmpfsUsedBytes").longValue();
        long free = value.path("pgTmpfsFreeBytes").longValue();
        long at = sample.path("elapsedMillis").longValue();
        if (!value.path("pgTmpfsFreeBytes").isIntegralNumber() || free < 0
                || !value.path("pgTmpfsUsedBytes").isIntegralNumber() || used < 0) {
            throw new IllegalStateException("L6_STORAGE_GUARD_OBSERVATION_MISSING");
        }
        if (previousUsed >= 0 && at > previousMillis) {
            long growth = Math.max(0, used - previousUsed);
            // 相邻边界可能非常接近；实际增长全部保留，最短按一个采集上限归一化。
            long span = Math.max(8_000, at - previousMillis);
            maximumIntervalGrowth = Math.max(maximumIntervalGrowth, Math.multiplyExact(growth, 10_000) / span);
        }
        previousUsed = used; previousMillis = at;
        long reserve = Math.max(MINIMUM_RESERVE, Math.multiplyExact(maximumIntervalGrowth, 3));
        if (free <= reserve && stoppedAt < 0) {
            stoppedAt = now;
            trigger = new ObjectMapper().createObjectNode().put("reason", "PG_TMPFS_CAPACITY_AT_RISK")
                    .put("elapsedNanos", now).put("freeBytes", free).put("reserveBytes", reserve)
                    .put("maximumObservedIntervalGrowthBytes", maximumIntervalGrowth);
        }
        emergency |= free <= Math.max(16L * 1024 * 1024, maximumIntervalGrowth);
    }
    synchronized boolean producerAllowed() { return stoppedAt < 0; }
    synchronized boolean stopped() { return stoppedAt >= 0; }
    synchronized boolean drainExpired(long now) { return stopped() && (emergency || now - stoppedAt >= DRAIN_LIMIT_NANOS); }
    synchronized long stoppedAt() { return stoppedAt; }
    synchronized ObjectNode evidence() { return trigger == null ? new ObjectMapper().createObjectNode().put("triggered", false) : trigger.deepCopy().put("triggered", true); }
}
