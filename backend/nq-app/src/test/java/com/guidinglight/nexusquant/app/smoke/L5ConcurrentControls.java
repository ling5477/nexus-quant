package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import java.nio.file.Files;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 每个 JVM 一个有界真实对账 actor；观察器不替换返回值，不增加业务锁或业务写入。 */
final class L5ConcurrentControls implements AutoCloseable {
    private final ObjectMapper json = new ObjectMapper();
    private final ArrayNode reservations = json.createArrayNode();
    private final ArrayNode calls = json.createArrayNode();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicBoolean stepComplete = new AtomicBoolean(true);
    private final AtomicBoolean step = new AtomicBoolean();
    private final AtomicBoolean stop = new AtomicBoolean();
    final AtomicInteger active = new AtomicInteger();
    private final OkxRestReconcileService reconcile;
    private Future<?> pending;
    private final ReentrantLock workloadSlot;

    L5ConcurrentControls(ConfigurableApplicationContext context, ReentrantLock workloadSlot) {
        this.workloadSlot = workloadSlot;
        reconcile = context.getBean(OkxRestReconcileService.class);
        var repository = (Advised) context.getBean(OrderRepository.class);
        var jdbc = context.getBean(JdbcTemplate.class);
        // 放在既有事务 advice 之后，读本次持有的 cursor 行版本；不另加锁、屏障或提交。
        repository.addAdvice((MethodInterceptor) invocation -> {
            if (!"reserveReconciliationCandidates".equals(invocation.getMethod().getName())) return invocation.proceed();
            Object result = invocation.proceed();
            B0Fixture.require(TransactionSynchronizationManager.isActualTransactionActive());
            var cursor = jdbc.queryForMap("SELECT revision,cursor_order_id FROM reconciliation_scan_cursors WHERE venue='OKX'");
            ObjectNode sample = json.createObjectNode().put("revision", ((Number) cursor.get("revision")).longValue())
                    .put("cursor_order_id", (String) cursor.get("cursor_order_id"))
                    .put("limit", (Integer) invocation.getArguments()[2]).put("timeMillis", System.currentTimeMillis());
            var selected = sample.putArray("selected");
            for (Object item : (List<?>) result) {
                OrderRecord order = (OrderRecord) item;
                selected.addObject().put("order_id", order.orderId()).put("version", order.version()).put("status", order.status().name());
            }
            synchronized (reservations) { B0Fixture.require(reservations.size() < 100); reservations.add(sample); }
            persist();
            return result;
        });
    }

    String handle(String command) throws Exception {
        switch (command) {
            case "L5C_PAUSE":
                B0Fixture.require(L5QualificationControls.repeatedFault);
                paused.set(true);
                B0Fixture.require(workloadSlot.tryLock(5, TimeUnit.SECONDS));
                workloadSlot.unlock();
                return "PAUSED";
            case "L5C_RESUME":
                B0Fixture.require(L5QualificationControls.repeatedFault); paused.set(false); return "RESUMED";
            case "L5C_TARGET_STEP":
                B0Fixture.require(L5QualificationControls.repeatedFault && paused.get() && pending != null);
                B0Fixture.require(stepComplete.compareAndSet(true, false));
                B0Fixture.require(step.compareAndSet(false, true)); return "STEP_REQUESTED";
            case "L5C_TARGET_STATUS": return stepComplete.get() ? "COMPLETE" : "PENDING";
            case "L5C_BEGIN":
                B0Fixture.require(pending == null);
                pending = worker.submit(() -> {
                    try {
                        for (int i = 0; i < 80 && !stop.get(); i++) { boolean forced = step.getAndSet(false); once(forced); if (forced) stepComplete.set(true); Thread.sleep(500); }
                        if (!stop.get()) throw new IllegalStateException("L5 concurrent actor budget exhausted");
                    } catch (Exception error) { throw new IllegalStateException("L5 reconciliation actor failed", error); }
                });
                return "L5C_STARTED";
            case "L5C_STOP":
                stop.set(true);
                if (pending != null) pending.get(45, TimeUnit.SECONDS);
                return "L5C_STOPPED";
            case "L5C_STEP":
                B0Fixture.require(pending == null || pending.isDone()); once(false); return "L5C_STEPPED";
            default: throw new IllegalArgumentException("unknown concurrent command");
        }
    }

    void checkFailure() throws Exception { if (pending != null && pending.isDone()) pending.get(); }

    private void once(boolean forced) {
        workloadSlot.lock();
        if (paused.get() && !forced) { workloadSlot.unlock(); return; }
        ObjectNode call = json.createObjectNode().put("startMillis", System.currentTimeMillis());
        B0Fixture.require(active.incrementAndGet() == 1);
        try { call.put("newTrades", reconcile.reconcileOnce(40)); }
        catch (RuntimeException error) { call.put("error", error.getClass().getSimpleName()); throw error; }
        finally {
            active.decrementAndGet(); call.put("endMillis", System.currentTimeMillis());
            synchronized (calls) { calls.add(call); }
            workloadSlot.unlock();
            persist();
        }
    }

    /** 故障资格在强杀前保留已观察的游标事实；写入失败直接中断资格。 */
    private synchronized void persist() {
        ObjectNode report = json.createObjectNode().put("pid", ProcessHandle.current().pid());
        synchronized (reservations) { report.set("reservations", reservations.deepCopy()); }
        synchronized (calls) { report.set("calls", calls.deepCopy()); }
        try {
            Files.writeString(Path.of("l5c-actor-" + ProcessHandle.current().pid() + ".json"), json.writeValueAsString(report));
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    @Override public void close() throws Exception {
        stop.set(true);
        worker.shutdown();
        try { B0Fixture.require(worker.awaitTermination(45, TimeUnit.SECONDS)); checkFailure(); }
        finally {
            ObjectNode report = json.createObjectNode().put("pid", ProcessHandle.current().pid());
            synchronized (reservations) { report.set("reservations", reservations); }
            synchronized (calls) { report.set("calls", calls); }
            Files.writeString(Path.of("l5c-actor-" + ProcessHandle.current().pid() + ".json"), json.writeValueAsString(report));
        }
    }
}
