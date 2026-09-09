package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 真实事务代理外层的一次性提交后屏障；不替换仓储、不写表，由父进程执行强杀。 */
final class B4ProcessFaults {
    private B4ProcessFaults() { }

    static void armAfterTradeCommit(TradeRepository repository) {
        B0Fixture.require(repository instanceof Advised);
        var armed = new AtomicBoolean(true);
        ((Advised) repository).addAdvice(0, (MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (java.util.Set.of("insert", "insertWithRequiredEvent").contains(invocation.getMethod().getName())
                    && armed.compareAndSet(true, false)) {
                // 外层 proceed 已经过完整 TransactionInterceptor；另由只读数据库连接确认 durable facts。
                B0Fixture.require(!TransactionSynchronizationManager.isActualTransactionActive());
                System.out.println("B4_CUT AFTER_TRADE_COMMIT transactionActive=false");
                System.out.flush();
                if (!new CountDownLatch(1).await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("BLOCKED / B4_HARNESS_CONTROL_GAP: controller did not kill at cut");
                }
            }
            return result;
        });
    }
}
