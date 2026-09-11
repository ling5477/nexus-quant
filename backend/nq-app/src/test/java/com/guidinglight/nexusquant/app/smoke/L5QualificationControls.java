package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleScanService;
import com.guidinglight.nexusquant.strategy.application.StrategyRunRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.observability.operational.MicrometerOperationalObservation;
import com.zaxxer.hikari.HikariDataSource;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Set;
import java.nio.file.Path;
import java.io.BufferedWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** 采样只读实例状态，不持有业务连接或事务；stdin 单命令天然限制每 JVM 的在途工作。 */
final class L5QualificationControls implements AutoCloseable {
    static boolean enabled;
    static boolean repeatedFault;
    static boolean killTransition;

    /** 普通L5入口仍拒绝全部故障命令，仅此资格入口允许四个已接受的控制。 */
    static boolean isRepeatedFaultCommand(String command) {
        return repeatedFault && command != null && Set.of("ARM_B4_TRADE_COMMIT", "ARM_V51_B",
                "ARM_B4_TX ACK WIRE", "ARM_B4_TX LEDGER WIRE").contains(command);
    }
    private final ConfigurableApplicationContext context;
    private final HikariDataSource pool;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
    private final BufferedWriter output;
    private final AtomicReference<Throwable> samplingFailure = new AtomicReference<>();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private int samples;
    private L5ConcurrentControls concurrent;
    private L5TradeTargetBarrier target;
    private final ReentrantLock workloadSlot = new ReentrantLock(true);

    L5QualificationControls(ConfigurableApplicationContext context) throws Exception {
        this.context = context;
        pool = context.getBean(HikariDataSource.class);
        output = Files.newBufferedWriter(Path.of("l5-metrics-" + ProcessHandle.current().pid() + ".ndjson"));
        sampler.scheduleWithFixedDelay(() -> {
            try { sample(); } catch (Throwable error) { samplingFailure.compareAndSet(null, error); }
        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    private synchronized void sample() throws Exception {
        if (++samples > 7200) throw new IllegalStateException("L5 sampling budget exceeded");
        var bean = pool.getHikariPoolMXBean();
        ObjectNode node = mapper.createObjectNode().put("timeMillis", System.currentTimeMillis())
                .put("pid", ProcessHandle.current().pid()).put("active", bean.getActiveConnections())
                .put("idle", bean.getIdleConnections()).put("waiting", bean.getThreadsAwaitingConnection())
                .put("total", bean.getTotalConnections()).put("max", pool.getMaximumPoolSize())
                .put("threads", ManagementFactory.getThreadMXBean().getThreadCount())
                .put("activeCommands", active.get()).put("completedCommands", completed.get())
                .put("commandQueue", workloadSlot.getQueueLength()).put("reconcileActive", concurrent == null ? 0 : concurrent.active.get());
        output.write(mapper.writeValueAsString(node)); output.newLine(); output.flush();
    }

    String handle(String command) throws Exception {
        validateCommand(command);
        if (concurrent != null) concurrent.checkFailure();
        if ("L5_TARGET_ARM".equals(command)) {
            B0Fixture.require(target == null && repeatedFault);
            target = new L5TradeTargetBarrier(ProcessHandle.current().pid(), "b0-fill-b0-venue-85", "l5-trace-85");
            B4ProcessFaults.armAfterTradeCommit(context.getBean(TradeRepository.class), target);
            return "ARMED";
        }
        if ("L5_TARGET_RELEASE".equals(command)) { B0Fixture.require(target != null); target.release(); return "RELEASED"; }
        if (command.startsWith("L5C_")) {
            if (concurrent == null) concurrent = new L5ConcurrentControls(context, workloadSlot);
            return concurrent.handle(command);
        }
        if (samplingFailure.get() != null) throw new IllegalStateException("measurement unavailable", samplingFailure.get());
        // 此锁只限制测试投喂速率；跨 JVM 的数据库竞争仍由真实业务实现处理。
        workloadSlot.lockInterruptibly();
        if (active.incrementAndGet() != 1) throw new IllegalStateException("one pending command per JVM");
        try {
            if (command.startsWith("L5_PLACE ")) {
                int identity = Integer.parseInt(command.substring(9));
                if (identity < 1 || identity > 240) throw new IllegalArgumentException("L5 identity out of bounds");
                var jdbc = context.getBean(JdbcTemplate.class);
                long account = jdbc.queryForObject("SELECT account_id FROM accounts WHERE account_code='b0-account'", Long.class);
                String db = jdbc.queryForObject("SELECT current_database()", String.class);
                String client = "l5" + db.substring(db.length() - 20) + String.format("%04d", identity);
                var result = context.getBean(OrderCommandService.class).placeOrder(new PlaceOrderRequest(
                        "l5-request-" + identity, account, null, "OKX", "BTC-USDT", client, account + ":" + client,
                        "l5_test", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("100"),
                        new BigDecimal("0.1"),
                        "GTC", "l5-trace-" + identity, "SIM", null));
                return "L5_PLACE " + result.status();
            }
            return switch (command) {
                case "L5_KILL_ENGAGE" -> {
                    var kill = context.getBean(KillSwitchService.class);
                    var snapshot = kill.engage(kill.snapshot().version(), "L5_LOAD_STOP", "L5_QUALIFICATION", "l5-kill-transition");
                    yield "L5_KILL " + snapshot.status() + " " + snapshot.version();
                }
                case "L5_RECONCILE" -> "L5_RECONCILE " + context.getBean(OkxRestReconcileService.class).reconcileOnce(100);
                case "L5_SCAN" -> "L5_SCAN " + context.getBean(StrategyScheduleScanService.class).scanOnce("l5-scan");
                case "L5_PROJECT" -> "L5_PROJECT " + context.getBean(StrategyRunRecoveryService.class).recoverAll();
                case "L5_OBSERVE" -> mapper.writeValueAsString(context.getBean(MicrometerOperationalObservation.class).snapshot());
                default -> throw new IllegalArgumentException("L5 clean-load command only");
            };
        } finally { active.decrementAndGet(); completed.incrementAndGet(); workloadSlot.unlock(); }
    }

    static void validateCommand(String command) {
        if (killTransition && !repeatedFault && "L5_KILL_ENGAGE".equals(command)) return;
        if (repeatedFault && Set.of("L5_TARGET_ARM", "L5_TARGET_RELEASE", "L5C_PAUSE", "L5C_RESUME", "L5C_TARGET_STEP", "L5C_TARGET_STATUS").contains(command == null ? "" : command)) return;
        if (command != null && command.matches("L5_PLACE [0-9]{1,3}")) {
            int identity = Integer.parseInt(command.substring(9));
            if (identity >= 1 && identity <= 240) return;
        }
        if ("L5_RECONCILE".equals(command) || "L5_SCAN".equals(command)
                || "L5_PROJECT".equals(command) || "L5_OBSERVE".equals(command)
                || "L5C_BEGIN".equals(command) || "L5C_STOP".equals(command) || "L5C_STEP".equals(command)) return;
        throw new IllegalArgumentException("L5 clean-load command outside frozen bounds");
    }

    @Override public void close() throws Exception {
        // actor 失败仍必须回收 sampler 与文件；try-with-resources 保留首因和后续清理异常。
        try (output) {
            try {
                if (target != null) target.release();
                if (concurrent != null) concurrent.close();
            } finally {
                sampler.shutdownNow();
                if (!sampler.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("sampler not stopped");
            }
            sample();
            if (samplingFailure.get() != null) throw new IllegalStateException("measurement failed", samplingFailure.get());
        }
    }
}
