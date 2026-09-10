package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.trading.application.OrderCommandWriteService;
import com.guidinglight.nexusquant.trading.application.StrategyOrderPreparationService;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.scheduler.validationevidence.ValidationEvidenceScheduler;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.StrategyRunRecoveryService;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleScanService;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerRequest;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRecoveryRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyTriggerGateway;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerService;
import com.guidinglight.nexusquant.strategy.application.StrategyDefinitionService;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleService;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;

/** 屏障只暂停实际调用，不替换返回值、不创建订单，也不转移业务发送资格。 */
final class B5QualificationControls implements AutoCloseable {
    static boolean schedulerEnabled;
    static boolean strategyRecoveryEnabled;
    private final ConfigurableApplicationContext context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final CountDownLatch insertRelease = new CountDownLatch(1);
    private final CountDownLatch schedulerRelease = new CountDownLatch(1);
    private final AtomicBoolean holdScheduler = new AtomicBoolean();
    private final AtomicInteger callbacks = new AtomicInteger();
    private final CountDownLatch strategyRelease = new CountDownLatch(1);
    private final AtomicBoolean holdStrategy = new AtomicBoolean();
    private Future<String> pending;

    B5QualificationControls(ConfigurableApplicationContext context) {
        this.context = context;
        var scan = context.getBean(StrategyScheduleScanService.class);
        var gateway = context.getBean(StrategyTriggerGateway.class);
        var proxy = new ProxyFactory(gateway);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            if ("trigger".equals(invocation.getMethod().getName())) {
                var request = (StrategyManualTriggerRequest) invocation.getArguments()[0];
                System.out.println("B5_ADMISSION_ATTEMPT key=" + request.dispatchIdentity());
                System.out.flush();
            }
            if ("trigger".equals(invocation.getMethod().getName()) && holdStrategy.compareAndSet(true, false)) {
                System.out.println("B5_STRATEGY_CUT localBusyHeld=true");
                System.out.flush();
                B0Fixture.require(strategyRelease.await(45, TimeUnit.SECONDS));
            }
            return invocation.proceed();
        });
        // 测试代理继续调用原 gateway；仅让两个 JVM 都经过生产窗口/dedup 检查后暂停。
        ReflectionTestUtils.setField(scan, "strategyTriggerGateway", proxy.getProxy());
        if (schedulerEnabled) {
            var processors = context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class);
            B0Fixture.require(processors.size() == 1);
            var tasks = processors.values().iterator().next().getScheduledTasks();
            B0Fixture.require(tasks.size() == 1);
            System.out.println("B5_SCHEDULING tasks=" + tasks);
            Object query = context.getBean(ValidationOperationsRuntimeEvidenceOverviewQueryService.class);
            B0Fixture.require(query instanceof Advised);
            ((Advised) query).addAdvice(0, (MethodInterceptor) invocation -> {
                if ("overview".equals(invocation.getMethod().getName())) {
                    callbacks.incrementAndGet();
                    B0Fixture.require(TransactionSynchronizationManager.isActualTransactionActive());
                    B0Fixture.require(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                    int pid = context.getBean(JdbcTemplate.class).queryForObject("SELECT pg_backend_pid()", Integer.class);
                    System.out.println("B5_SCHED_CALLBACK backendPid=" + pid + " readOnly=true count=" + callbacks.get());
                    System.out.flush();
                    if (holdScheduler.compareAndSet(true, false)) B0Fixture.require(schedulerRelease.await(45, TimeUnit.SECONDS));
                }
                return invocation.proceed();
            });
        }
    }

    String handle(String command) throws Exception {
        if (command.startsWith("ARM_V51_TX ")) {
            String[] parts = command.split(" ");
            Object bean = switch (parts[1]) {
                case "A" -> context.getBean(StrategyRunRepository.class);
                case "B" -> context.getBean(StrategyOrderPreparationService.class);
                case "C" -> context.getBean(StrategyRunExecutionRepository.class);
                default -> throw new IllegalArgumentException("unknown V51 boundary");
            };
            String method = switch (parts[1]) { case "A" -> "admit"; case "B" -> "prepare"; default -> "project"; };
            B4TransactionFaults.arm(bean, context.getBean(JdbcTemplate.class), method, parts[2]);
            return "V51_TX_ARMED";
        }
        switch (command) {
            case "BEGIN_V51_MANUAL":
                B0Fixture.require(pending == null);
                pending = worker.submit(() -> context.getBean(StrategyManualTriggerService.class).trigger(
                        new StrategyManualTriggerRequest("b5-strategy", "v51-manual", "BTC-USDT", OrderSide.BUY,
                                OrderType.LIMIT, new BigDecimal("7"), new BigDecimal("100"), "v51-manual")).toString());
                return "MANUAL_STARTED";
            case "DISABLE_V51":
                context.getBean(StrategyDefinitionService.class).disable("b5-strategy");
                context.getBean(StrategyScheduleService.class).disable("b5");
                return "V51_DISABLED";
            case "ARM_V51_B":
                B5PreSendBarrier.arm(context.getBean(StrategyOrderPreparationService.class), "prepare", "V51_B");
                return "V51_B_ARMED";
            case "RECOVER_V51_ALL":
                return "V51_RECOVERED " + context.getBean(StrategyRunRecoveryService.class).recoverAll();
            case "CANCEL_V51":
                String orderId = context.getBean(JdbcTemplate.class).queryForObject(
                        "SELECT order_id FROM orders WHERE strategy_run_id IS NOT NULL", String.class);
                return context.getBean(OrderCommandService.class).cancelOrder(
                        new CancelOrderRequest(orderId, 1L, null, "V51_CANCEL_PROOF", "v51-cancel")).toString();
            case "ARM_B5_ADMISSION":
                B5PreSendBarrier.arm(context.getBean(StrategyRunRepository.class), "admit", "AFTER_ADMISSION");
                return "ADMISSION_ARMED";
            case "RECOVER_B5_STRATEGY":
                return "STRATEGY_RECOVERED " + context.getBean(
                        StrategyRunRecoveryService.class).recover("b5-strategy");
            case "ARM_B5_STRATEGY_WIRE":
                B4TransactionFaults.arm(context.getBean(
                        StrategyRunRecoveryRepository.class),
                        context.getBean(JdbcTemplate.class), "recoverNoSendDispatches", "WIRE");
                return "STRATEGY_WIRE_ARMED";
            case "ARM_B5_STRATEGY": holdStrategy.set(true); return "STRATEGY_ARMED";
            case "BEGIN_B5_STRATEGY":
                B0Fixture.require(pending == null); pending = worker.submit(this::runStrategy); return "STRATEGY_STARTED";
            case "RELEASE_B5_STRATEGY": strategyRelease.countDown(); return "STRATEGY_RELEASED";
            case "AWAIT_B5_STRATEGY":
                B0Fixture.require(pending != null);
                String scanResult = pending.get(50, TimeUnit.SECONDS); pending = null; return scanResult;
            case "RUN_B5_STRATEGY": return runStrategy();
            case "ARM_B5_BEFORE_INSERT":
                var armed = new AtomicBoolean(true);
                ((Advised) context.getBean(OrderCommandWriteService.class)).addAdvice(0, (MethodInterceptor) invocation -> {
                    if ("preparePlaceOrder".equals(invocation.getMethod().getName()) && armed.compareAndSet(true, false)) {
                        B0Fixture.require(!TransactionSynchronizationManager.isActualTransactionActive());
                        System.out.println("B5_BEFORE_INSERT transactionActive=false");
                        System.out.flush();
                        B0Fixture.require(insertRelease.await(45, TimeUnit.SECONDS));
                    }
                    return invocation.proceed();
                });
                return "INSERT_ARMED";
            case "RELEASE_B5_INSERT": insertRelease.countDown(); return "INSERT_RELEASED";
            case "ARM_B5_SCHED": holdScheduler.set(true); return "SCHED_ARMED";
            case "BEGIN_B5_SCHED":
                B0Fixture.require(pending == null);
                pending = worker.submit(this::runScheduler); return "SCHED_STARTED";
            case "RELEASE_B5_SCHED": schedulerRelease.countDown(); return "SCHED_RELEASED";
            case "AWAIT_B5_SCHED":
                B0Fixture.require(pending != null);
                String result = pending.get(50, TimeUnit.SECONDS); pending = null; return result;
            case "RUN_B5_SCHED": return runScheduler();
            default: return null;
        }
    }

    private String runScheduler() {
        context.getBean(ValidationEvidenceScheduler.class).scheduledRefresh();
        return "SCHED_DONE callbacks=" + callbacks.get();
    }

    private String runStrategy() {
        return context.getBean(StrategyScheduleScanService.class)
                .scanOnce("b5-schedule").toString();
    }

    @Override public void close() throws Exception {
        insertRelease.countDown(); schedulerRelease.countDown(); strategyRelease.countDown(); worker.shutdownNow();
        B0Fixture.require(worker.awaitTermination(5, TimeUnit.SECONDS));
    }
}
