package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 锁外预检通过后暂停真实调用，不替换返回或写入业务事实。 */
final class L5FillControls {
    private L5FillControls() { }

    static void arm(TradeRepository repository) throws Exception {
        Files.deleteIfExists(Path.of("fill-cut"));
        Files.deleteIfExists(Path.of("fill-release"));
        var armed = new AtomicBoolean(true);
        ((Advised) repository).addAdvice(0, (MethodInterceptor) invocation -> {
            if ("insertWithRequiredEvent".equals(invocation.getMethod().getName())
                    && armed.compareAndSet(true, false)) {
                B0Fixture.require(!TransactionSynchronizationManager.isActualTransactionActive());
                Files.writeString(Path.of("fill-cut"), "PRECHECK_PASSED");
                long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                while (!Files.exists(Path.of("fill-release"))) {
                    if (System.nanoTime() > deadline) throw new IllegalStateException("fill barrier timeout");
                    Thread.sleep(20);
                }
            }
            return invocation.proceed();
        });
    }
}
