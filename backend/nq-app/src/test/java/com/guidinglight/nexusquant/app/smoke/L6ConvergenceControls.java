package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.trading.application.OrderCommandWriteService;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 仅暂停真实写服务的事务入口；原调用、异常与数据库事实不替换。 */
final class L6ConvergenceControls implements AutoCloseable {
    static boolean enabled;
    private final ConfigurableApplicationContext context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger ticks = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private Future<?> pending;
    private Future<?> scheduled;

    L6ConvergenceControls(ConfigurableApplicationContext context) {
        this.context = context;
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("l6-convergence-scheduled-");
        scheduler.initialize();
    }

    String handle(String command) throws Exception {
        if (command.startsWith("L6C_CONVERGE ") || command.startsWith("L6C_TRANSITION ")) {
            String orderId = context.getBean(JdbcTemplate.class).queryForObject("SELECT order_id FROM orders", String.class);
            OrderStatus status = OrderStatus.valueOf(command.split(" ")[1]);
            var writer = context.getBean(OrderCommandWriteService.class);
            try {
                var result = command.startsWith("L6C_CONVERGE ")
                        ? writer.reconcileOrderStatus(orderId, status, "L6C_TEST", "l6c-trace")
                        : writer.transitionOrder(orderId, status, "L6C_TEST", "l6c-trace");
                return result.status() + "/" + result.version();
            } catch (RuntimeException failure) { return "ERROR " + failure.getMessage(); }
        }
        if (command.startsWith("L6C_ONCE ")) {
            int limit = Integer.parseInt(command.split(" ")[1]);
            B0Fixture.require(limit > 0 && limit <= 100);
            return "RECOVER " + context.getBean(OkxRestReconcileService.class).reconcileOnce(limit);
        }
        if (command.startsWith("L6C_CAS ")) {
            String target = command.substring(8);
            var armed = new AtomicBoolean(true);
            ((Advised) context.getBean(OrderRepository.class)).addAdvice(0, (MethodInterceptor) invocation -> {
                if ("compareAndSetStatus".equals(invocation.getMethod().getName())
                        && target.equals(String.valueOf(invocation.getArguments()[3])) && armed.compareAndSet(true, false)) {
                    B0Fixture.require(TransactionSynchronizationManager.isActualTransactionActive());
                    System.out.println("L6C_CUT " + target); System.out.flush();
                    B0Fixture.require(release.await(60, TimeUnit.SECONDS));
                }
                return invocation.proceed();
            });
            return "ARMED " + target;
        }
        if (command.startsWith("L6C_ARM ")) {
            String target = command.substring(8);
            var armed = new AtomicBoolean(true);
            var writer = (Advised) context.getBean(OrderCommandWriteService.class);
            writer.addAdvice(0, (MethodInterceptor) invocation -> {
                if (Set.of("transitionOrder", "reconcileOrderStatus").contains(invocation.getMethod().getName())
                        && target.equals(String.valueOf(invocation.getArguments()[1]))
                        && armed.compareAndSet(true, false)) {
                    B0Fixture.require(!TransactionSynchronizationManager.isActualTransactionActive());
                    System.out.println("L6C_CUT " + target);
                    System.out.flush();
                    B0Fixture.require(release.await(60, TimeUnit.SECONDS));
                }
                return invocation.proceed();
            });
            return "ARMED " + target;
        }
        if ("L6C_BEGIN".equals(command)) {
            B0Fixture.require(pending == null);
            pending = executor.submit(() -> {
                try {
                    context.getBean(OkxRestReconcileService.class).reconcileOnce(100);
                    return "OK";
                } catch (RuntimeException failure) {
                    return "ERROR " + failure.getMessage();
                }
            });
            return "BEGIN";
        }
        if ("L6C_RELEASE".equals(command)) { release.countDown(); return "RELEASED"; }
        if ("L6C_AWAIT".equals(command)) {
            Object result = pending.get(30, TimeUnit.SECONDS);
            pending = null;
            return String.valueOf(result);
        }
        if ("L6C_SCHEDULE".equals(command)) {
            // 使用 Spring 默认 recurring-task error handler；计数后重新抛出原异常。
            scheduled = scheduler.scheduleWithFixedDelay(() -> {
                try { context.getBean(OkxRestReconcileService.class).scheduledReconcile(); }
                catch (RuntimeException failure) { errors.incrementAndGet(); throw failure; }
                finally { ticks.incrementAndGet(); }
            }, Duration.ofMillis(250));
            return "SCHEDULED";
        }
        if ("L6C_TICKS".equals(command)) return "TICKS " + ticks.get() + " ERRORS " + errors.get();
        if ("L6C_UNSCHEDULE".equals(command)) { scheduled.cancel(false); return "UNSCHEDULED"; }
        return null;
    }

    @Override public void close() {
        release.countDown();
        scheduler.shutdown();
        executor.shutdownNow();
    }
}
