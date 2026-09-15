package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

/** 正式L6-A预算在T=0前派生并冻结；事务总量和短窗口放大分别拒绝，不能在运行中增加额度。 */
final class L6HardBudgets {
    static final long CHECKPOINT_NANOS = 60_000_000_000L;
    static final long RAW_CAP = 1_073_741_824L;
    static final long REPLAY_TRANSACTIONS = 7, SCAN_TRANSACTIONS = 2;
    static final long CHAIN_TRANSACTIONS = 50;
    private final long projectedTransactions, reserveTransactions, transactionCap, projectedBytes, diskFloor;
    private final long startTransactions;
    private final int orders;
    private final ObjectNode preflight;
    private final ArrayDeque<Point> recent = new ArrayDeque<>();
    private long lastAt = -1, lastTransactions;
    private Long finalTransactions;
    private String failure;
    private record Point(long millis, long transactions) { }

    L6HardBudgets(L6DurationContract timing, long interval, QualificationCapacity capacity,
                  long startTransactions, long freeDisk, JsonNode entryActors) {
        if (timing.warmupNanos() != 600_000_000_000L || timing.activeEnd() != 3_000_000_000_000L
                || timing.total() != 3_600_000_000_000L || interval != 7_117_650_000L || startTransactions < 0) reject("SCOPE");
        capacity.validate(); orders = Math.toIntExact(Math.ceilDiv(timing.activeEnd(), interval));
        if (orders != capacity.runOrderBudget() || capacity.venueLogicalOrderCapacity() < orders || orders > 3000) reject("ORDERS");
        this.startTransactions = startTransactions;
        long invocations = 2 * Math.ceilDiv(3600, 5);
        long reconciliation = invocations * (SCAN_TRANSACTIONS + Math.min(orders, 100) * REPLAY_TRANSACTIONS);
        long business = orders * CHAIN_TRANSACTIONS;
        long scheduler = 1440, scanObservation = 1440 * 2, validation = 24 * 4;
        // 两个actor各一次只读age；PG合并事务另有一次SET隔离级别，每10秒共四个事务。
        long sampler = 360 * 4;
        long controller = (720 + 506 + 60 + 3) * 3;
        long checkpoints = 61 * 3;
        // Hikari默认500ms存活检查旁路窗；20条物理连接最多每秒40次检查，另计两次建连轮换。
        long connectionHealth = 3600 * 40 + 80;
        projectedTransactions = reconciliation + business + scheduler + scanObservation + validation + sampler + controller + checkpoints + connectionHealth;
        // 完整双游标绕行加一分钟最大正常驱动、整批新链以及启动/池内部命令余量。
        reserveTransactions = 2 * Math.ceilDiv(orders, 100) * (SCAN_TRANSACTIONS + 100 * REPLAY_TRANSACTIONS)
                + 24 * (SCAN_TRANSACTIONS + 100 * REPLAY_TRANSACTIONS) + CHAIN_TRANSACTIONS * 9 + 1024;
        transactionCap = Math.ceilDiv(projectedTransactions + reserveTransactions, 1000) * 1000;
        // 原始样本逐字段字节上界：保留全部Venue事件与完整账务快照，不靠压缩或删除满足1GiB。
        long checkpointCount = 61;
        long eventByteSeconds = 60 * checkpointCount * (checkpointCount + 1) / 2;
        long venueEvents = 2 * 100 * eventByteSeconds / 5;
        projectedBytes = venueEvents * 128 + checkpointCount * orders * 8192 + 32L * 1_048_576;
        long rawReserve = (2 * 720 * 100 * 128L + orders * 8192L) + 16L * 1_048_576;
        if (projectedBytes + rawReserve >= RAW_CAP) reject("RAW_ARTIFACT_INSUFFICIENT");
        diskFloor = Math.max(2L * 1_073_741_824, freeDisk / 5);
        if (freeDisk < projectedBytes + rawReserve + diskFloor) reject("DISK_INSUFFICIENT");
        if (!entryActors.isArray() || entryActors.size() != 2) reject("ACTOR_MEASUREMENT_MISSING");
        for (var actor : entryActors) {
            if (actor.path("poolMax").asInt() != 10) reject("POOL_COST_MODEL_SCOPE");
            if (!actor.path("tickStarted").isIntegralNumber() || !actor.path("commands").isIntegralNumber()
                    || actor.path("tickStarted").asLong() < 0 || actor.path("commands").asLong() < 0) reject("ACTOR_MEASUREMENT_MISSING");
            if (actor.path("tickStarted").asLong() + 720 + 60 > 1000) reject("SCHEDULER_INSUFFICIENT");
            if (actor.path("commands").asLong() + 720 * 2 + orders + 64 > 4000) reject("COMMANDS_INSUFFICIENT");
        }
        preflight = new ObjectMapper().createObjectNode().put("status", "SUFFICIENT").put("frozenBeforeT0", true)
                .put("startTransactions", startTransactions).put("transactionSemantics", "RUN_DELTA_AND_ROLLING_RATE")
                .put("projectedTransactions", projectedTransactions).put("reserveTransactions", reserveTransactions)
                .put("transactionHardCap", transactionCap).put("projectedRawBytes", projectedBytes)
                .put("rawReserveBytes", rawReserve).put("rawHardCap", RAW_CAP).put("entryFreeDiskBytes", freeDisk)
                .put("diskFreeFloorBytes", diskFloor).put("orders", orders).put("fillsPerOrder", 1).put("fillSafetyCap", 4)
                .put("faults", 0).put("restarts", 0).put("restartSafetyCap", 6).put("reconciliationInvocations", invocations)
                .put("ticksPerActor", 720).put("tickSafetyCap", 1000).put("commandsPerActorUpper", 1440 + orders + 64)
                .put("commandSafetyCap", 4000).put("resourceSamples", 360).put("sampleSafetyCap", 500)
                .put("checkpointCountUpper", checkpointCount).put("checkpointIntervalSeconds", 60)
                .put("fileCountUpper", 256).put("fileCountSafetyCap", 10000)
                .put("pgStorage", "REQUIRES_EXISTING_DYNAMIC_CAPACITY_PREFLIGHT_AND_RUNTIME_GUARD")
                .put("hostMemory", "REQUIRES_EXISTING_60_PERCENT_ENTRY_PREFLIGHT");
        preflight.putObject("transactionGroups").put("businessChain", business).put("qualificationReconciliation", reconciliation)
                .put("scheduler", scheduler).put("schedulerObservation", scanObservation).put("validation", validation)
                .put("sampler", sampler).put("controller", controller).put("checkpoint", checkpoints).put("connectionHealthAndSetup", connectionHealth);
    }

    synchronized void observe(JsonNode record) {
        JsonNode value = record.path("sources").path("postgres").path("values");
        if (!value.path("transactions").isIntegralNumber() || !record.path("elapsedMillis").isIntegralNumber()) reject("MEASUREMENT_MISSING");
        long at = record.path("elapsedMillis").asLong(), total = value.path("transactions").asLong();
        check(at, total);
        if (!value.path("maximumFillsPerOrder").isIntegralNumber() || value.path("maximumFillsPerOrder").asLong() > 1) reject("L6_A_FILL_AMPLIFICATION");
        if (value.path("orders").asLong() > orders || value.path("appConnections").asLong() > 20) reject("WORKLOAD_OR_POOL_AMPLIFICATION");
        for (String key : new String[]{"nq0", "nq1"}) {
            JsonNode actor = record.path("sources").path(key).path("values");
            if (!actor.path("transactionsByOriginAndOwner").isObject()) reject("ACCOUNTING_MISSING");
        }
        JsonNode files = record.path("sources").path("files").path("values");
        if (files.path("ownedTempBytes").asLong() >= RAW_CAP || files.path("freeDiskBytes").asLong(-1) < diskFloor) reject("DISK_RUNTIME");
    }

    synchronized void check(long at, long total) {
        if (failure != null) throw new IllegalStateException(failure);
        if (at < 0 || at <= lastAt || total < startTransactions || lastAt >= 0 && total < lastTransactions) fail("COUNTER_REGRESSION");
        long delta = total - startTransactions;
        if (delta > transactionCap) fail("TRANSACTION_CAP");
        // 两次采样之间保留采集偏移及PG统计刷新的一整个10秒余量；不据正常短突发宣判失控。
        for (var point : recent) {
            long span = at - point.millis;
            if (span >= 50_000) {
                long cycles = Math.ceilDiv(span + 10_000, 5000);
                long rateBudget = 2 * cycles * (SCAN_TRANSACTIONS + 100 * REPLAY_TRANSACTIONS)
                        + Math.ceilDiv(span + 10_000, 7117) * CHAIN_TRANSACTIONS + Math.ceilDiv(span + 10_000, 1000) * 40 + 1024;
                if (total - point.transactions > rateBudget) fail("TRANSACTION_RATE_AMPLIFICATION");
                break;
            }
        }
        recent.addLast(new Point(at, total));
        while (!recent.isEmpty() && at - recent.getFirst().millis > 60_000) recent.removeFirst();
        lastAt = at; lastTransactions = total;
    }
    private void fail(String reason) { failure = "L6_HARD_BUDGET_" + reason; throw new IllegalStateException(failure); }
    synchronized void checkFinal(long total) {
        // FINAL与最后一个collector可能重叠；终检只核总量，不写入独立采样时钟。
        finalTransactions = total;
        if (total < startTransactions || total - startTransactions > transactionCap) fail("FINAL_TRANSACTION_CAP");
    }
    static void reject(String reason) { throw new IllegalStateException("BLOCKED / L6_HARD_BUDGET_" + reason); }
    synchronized ObjectNode evidence() {
        return preflight.deepCopy().put("observedTransactionDelta", lastAt < 0 ? 0 : lastTransactions - startTransactions)
                .put("lastObservedMillis", lastAt).put("finalAbsoluteTransactions", finalTransactions).put("failure", failure);
    }
    ObjectNode admission(JsonNode pg, JsonNode host) {
        if (!"PASS".equals(pg.path("preflightResult").asText()) || !"PASS".equals(host.path("status").asText())) reject("RESOURCE_PREFLIGHT_MISSING");
        ObjectNode result = evidence().put("pgStorage", "SUFFICIENT").put("hostMemory", "SUFFICIENT");
        result.set("pgCapacityPreflight", pg.deepCopy()); result.set("hostMemoryPreflight", host.deepCopy());
        return result;
    }
    void write(Path path) throws Exception { Files.writeString(path, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(evidence())); }
}
