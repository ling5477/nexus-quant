package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.observability.operational.MicrometerOperationalObservation;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.StrategyRunRecoveryTick;
import com.guidinglight.nexusquant.strategy.application.StrategyRunRecoveryService;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleScanService;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunService;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunMonitorService;
import com.guidinglight.nexusquant.research.application.paper.PaperRunMonitorRunService;
import com.guidinglight.nexusquant.research.application.paper.PaperRunAlertCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunDailyReportGenerateCommand;
import com.zaxxer.hikari.HikariDataSource;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.scheduler.validationevidence.ValidationEvidenceRefreshService;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ExecutionException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.BufferedWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;

/** 只读采样与有限服务命令；timer代理原样调用，不替换结果或创建业务事实。 */
final class L6QualificationControls implements AutoCloseable {
    static boolean enabled;
    private final ConfigurableApplicationContext context;
    private final ObjectMapper json = new ObjectMapper();
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicInteger tickStarted = new AtomicInteger();
    private final AtomicInteger tickCompleted = new AtomicInteger();
    private final AtomicInteger tickFailed = new AtomicInteger();
    private final AtomicInteger commands = new AtomicInteger();
    private final ThreadPoolExecutor commandExecutor = L6Measurements.commands();
    private final AtomicReference<List<String>> candidateStatuses = new AtomicReference<>();
    private final AtomicReference<ValidationOperationsRuntimeEvidenceOverviewReadModel> validation = new AtomicReference<>();
    private final Counter acquisitionTimeout;
    private final double timeoutStart;
    private final BufferedWriter output;
    private int samples;
    private String paper;

    L6QualificationControls(ConfigurableApplicationContext context) throws Exception {
        this.context = context;
        var tasks = context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class);
        B0Fixture.require(tasks.size() == 1 && tasks.values().iterator().next().getScheduledTasks().size() == 1);
        var proxy = new ProxyFactory(context.getBean(StrategyRunRecoveryService.class));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) call -> {
            if (!call.getMethod().getName().equals("recoverAll")) return call.proceed();
            if (tickStarted.incrementAndGet() > 1000) throw new IllegalStateException("L6 tick budget");
            try { Object result = call.proceed(); tickCompleted.incrementAndGet(); return result; }
            catch (Throwable error) { tickFailed.incrementAndGet(); failure.compareAndSet(null, error); throw error; }
        });
        ReflectionTestUtils.setField(context.getBean(StrategyRunRecoveryTick.class), "recovery", proxy.getProxy());
        acquisitionTimeout = L6Measurements.timeoutCounter(context.getBean(HikariDataSource.class), context.getBean(MeterRegistry.class));
        timeoutStart = acquisitionTimeout.count();
        var orderProxy = new ProxyFactory(context.getBean(OrderCommandService.class));
        orderProxy.setProxyTargetClass(true);
        orderProxy.addAdvice((MethodInterceptor) call -> {
            if (call.getMethod().getName().equals("reserveReconciliationCandidates")) {
                @SuppressWarnings("unchecked")
                Collection<OrderStatus> statuses = (Collection<OrderStatus>) call.getArguments()[1];
                candidateStatuses.set(statuses.stream().map(Enum::name).toList());
            }
            return call.proceed();
        });
        ReflectionTestUtils.setField(context.getBean(OkxRestReconcileService.class), "orderCommandService", orderProxy.getProxy());
        // 空库真实扫描捕获生产传入的候选合同，不复制生产状态列表。
        context.getBean(OkxRestReconcileService.class).reconcileOnce(100);
        var aggregateProxy = new ProxyFactory(context.getBean(ValidationOperationsRuntimeEvidenceOverviewQueryService.class));
        aggregateProxy.setProxyTargetClass(true);
        aggregateProxy.addAdvice((MethodInterceptor) call -> {
            Object result = call.proceed();
            if (result instanceof ValidationOperationsRuntimeEvidenceOverviewReadModel model) {
                // 先保存真实逐来源结果，再交给资格判定；失败时也保留原始降级原因和事实时间。
                Files.writeString(Path.of("l6-validation-" + ProcessHandle.current().pid() + ".ndjson"),
                        context.getBean(ObjectMapper.class).writeValueAsString(model) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                validation.set(model);
            }
            return result;
        });
        ReflectionTestUtils.setField(context.getBean(ValidationEvidenceRefreshService.class), "queryService", aggregateProxy.getProxy());
        output = Files.newBufferedWriter(Path.of("l6-resources-" + ProcessHandle.current().pid() + ".ndjson"));
        sampler.scheduleWithFixedDelay(() -> {
            try { sample(); } catch (Throwable error) { failure.compareAndSet(null, error); }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private synchronized void sample() throws Exception {
        if (++samples > 500) throw new IllegalStateException("L6 resource sample budget");
        output.write(json.writeValueAsString(resources())); output.newLine(); output.flush();
    }

    private ObjectNode resources() {
        var pool = context.getBean(HikariDataSource.class);
        var bean = pool.getHikariPoolMXBean();
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var threads = ManagementFactory.getThreadMXBean();
        ObjectNode n = json.createObjectNode().put("timeMillis", System.currentTimeMillis())
                .put("pid", ProcessHandle.current().pid()).put("heapUsed", heap.getUsed())
                .put("heapCommitted", heap.getCommitted()).put("heapMax", heap.getMax())
                .put("threads", threads.getThreadCount()).put("peakThreads", threads.getPeakThreadCount())
                .put("active", bean.getActiveConnections()).put("idle", bean.getIdleConnections())
                .put("pending", bean.getThreadsAwaitingConnection()).put("poolMax", pool.getMaximumPoolSize())
                .put("tickStarted", tickStarted.get()).put("tickCompleted", tickCompleted.get())
                .put("tickFailed", tickFailed.get()).put("commands", commands.get());
        n.set("commandQueue", L6Measurements.queue(commandExecutor));
        n.set("candidateAge", L6Measurements.age(context.getBean(NamedParameterJdbcTemplate.class), candidateStatuses.get()));
        n.put("acquisitionTimeoutMetric", acquisitionTimeout.getId().getName())
                .put("acquisitionTimeoutCount", acquisitionTimeout.count()).put("counterStart", timeoutStart)
                .put("counterEnd", acquisitionTimeout.count()).put("acquisitionTimeoutDelta", acquisitionTimeout.count() - timeoutStart);
        if (acquisitionTimeout.count() != timeoutStart) throw new IllegalStateException("UNEXPECTED_HIKARI_ACQUISITION_TIMEOUT");
        var gc = n.putArray("gc");
        ManagementFactory.getGarbageCollectorMXBeans().forEach(b -> gc.addObject().put("name", b.getName())
                .put("count", b.getCollectionCount()).put("timeMillis", b.getCollectionTime()));
        n.set("observations", json.valueToTree(context.getBean(MicrometerOperationalObservation.class).snapshot()));
        var observed = n.path("observations").path("validation_refresh").path("totals");
        if (observed.path("FAILURE").asLong() != 0) throw new IllegalStateException("VALIDATION_SCHEDULER_EXECUTION_FAILED");
        long completed = observed.path("DEGRADED").asLong() + observed.path("SUCCESS").asLong();
        if (completed > 0) n.set("validationQualification", L6ValidationContract.evaluate(validation.get(),
                observed.path("ATTEMPT").asLong(), completed, observed.path("FAILURE").asLong()));
        var names = n.putArray("threadNames");
        for (var info : threads.getThreadInfo(threads.getAllThreadIds())) if (info != null) names.add(info.getThreadName());
        L6Measurements.requireMandatory(n);
        return n;
    }

    String handle(String command) throws Exception {
        try { return commandExecutor.submit(() -> execute(command)).get(45, TimeUnit.SECONDS); }
        catch (ExecutionException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            throw error;
        }
    }

    private String execute(String command) throws Exception {
        if (failure.get() != null) throw new IllegalStateException("L6 sampling/timer failure", failure.get());
        if (commands.incrementAndGet() > 4000) throw new IllegalStateException("L6 command budget");
        return switch (command) {
            case "L6_SCAN" -> "SCAN " + context.getBean(StrategyScheduleScanService.class).scanOnce("l6-scan");
            case "L6_RECONCILE" -> "RECONCILE " + context.getBean(OkxRestReconcileService.class).reconcileOnce(100);
            case "L6_METRICS" -> json.writeValueAsString(resources());
            case "L6_PAPER" -> paper();
            default -> throw new IllegalArgumentException("L6 command outside frozen allowlist");
        };
    }

    private String paper() throws Exception {
        B0Fixture.require(paper == null);
        paper = context.getBean(PaperTradingRunService.class).create(new PaperTradingRunCreateCommand(
                "l6-publish", "SIM", "PAPER", "SPOT", "BTC-USDT", "1m", "{}", "l6-fixture")).paperRunId();
        // 独立合法 CREATED PaperRun 是正常监控对照，不与 ordinary 账务链自动关联。
        var normal = context.getBean(PaperRunMonitorRunService.class).runOnce(paper);
        var monitor = context.getBean(PaperRunMonitorService.class);
        B0Fixture.require(monitor.listAlerts(paper, null, null).isEmpty());
        monitor.createAlert(new PaperRunAlertCreateCommand(paper, "L6_POSITIVE_CONTROL", "CRITICAL",
                "隔离观测正例", "L6 资格正对照", "L6_TEST", "{}"));
        var date = LocalDate.now(ZoneOffset.UTC);
        var first = monitor.generateDailyReport(new PaperRunDailyReportGenerateCommand(paper, date));
        var second = monitor.generateDailyReport(new PaperRunDailyReportGenerateCommand(paper, date));
        B0Fixture.require(monitor.listDailyReports(paper).size() == 1 && second.alertCount() == 1);
        return json.writeValueAsString(json.createObjectNode().put("normalControl", true)
                .put("criticalAlerts", monitor.listAlerts(paper, null, "CRITICAL").size())
                .put("reportCount", 1).put("alertCount", second.alertCount()).put("runId", paper));
    }

    @Override public void close() throws Exception {
        commandExecutor.shutdown();
        if (!commandExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
            commandExecutor.shutdownNow();
            throw new IllegalStateException("L6 command executor shutdown");
        }
        sampler.shutdown();
        try {
            if (!sampler.awaitTermination(15, TimeUnit.SECONDS)) throw new IllegalStateException("L6 sampler shutdown");
            sample();
            if (failure.get() != null) throw new IllegalStateException("L6 sampler failed", failure.get());
        } finally { output.close(); }
    }
}
