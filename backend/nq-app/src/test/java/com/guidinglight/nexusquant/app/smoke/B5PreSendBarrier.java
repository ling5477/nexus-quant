package com.guidinglight.nexusquant.app.smoke;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 仅暂停原调用线程；不替换业务返回值、不生成 owner、不改变事务或数据库事实。 */
final class B5PreSendBarrier {
    private static final CountDownLatch RESUME = new CountDownLatch(1);

    static void arm(Object service) {
        arm(service, "preparePlaceOrder", "PRE_SEND");
    }

    static void arm(Object service, String method, String cut) {
        B0Fixture.require(service instanceof Advised);
        var armed = new AtomicBoolean(true);
        // 位于事务拦截器外侧，返回后必须已提交，允许另一个真实 JVM 进入恢复路径。
        ((Advised) service).addAdvice(0, (MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (method.equals(invocation.getMethod().getName()) && armed.compareAndSet(true, false)) {
                B0Fixture.require(!TransactionSynchronizationManager.isActualTransactionActive());
                System.out.println("B5_CUT " + cut + " transactionActive=false epochMillis=" + System.currentTimeMillis());
                System.out.flush();
                B0Fixture.require(RESUME.await(60, TimeUnit.SECONDS));
            }
            return result;
        });
    }

    static void release() { RESUME.countDown(); }
}
