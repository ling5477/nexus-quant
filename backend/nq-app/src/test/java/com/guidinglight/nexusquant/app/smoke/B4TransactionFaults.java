package com.guidinglight.nexusquant.app.smoke;

import java.sql.DriverManager;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 仅在真实事务内部标记一次目标提交；不替换事务管理器或业务写入。 */
final class B4TransactionFaults {
    private B4TransactionFaults() { }

    static void installDeferredRejection(B0Fixture fixture) throws Exception {
        B0Fixture.requireLoopbackDatabase(fixture.url(), fixture.name());
        try (var connection = DriverManager.getConnection(fixture.url(), "postgres", "");
             var statement = connection.createStatement()) {
            // 新建 owned fixture 启动前安装；只检查会话标记，不读写业务事实。
            statement.execute("CREATE FUNCTION b4_commit_rejection() RETURNS trigger LANGUAGE plpgsql AS $$ "
                    + "BEGIN IF current_setting('nq.b4_reject',true)='on' THEN "
                    + "RAISE EXCEPTION 'B4_DEFERRED_COMMIT_REJECTION' USING ERRCODE='40001'; "
                    + "END IF; RETURN NEW; END $$");
            for (String table : Set.of("orders", "trades", "ledger_entries")) {
                statement.execute("CREATE CONSTRAINT TRIGGER b4_commit_rejection AFTER INSERT OR UPDATE ON "
                        + table + " DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION b4_commit_rejection()");
            }
        }
    }

    static void arm(Object repository, JdbcTemplate jdbc, String method, String mode) {
        B0Fixture.require(repository instanceof Advised);
        B0Fixture.require(Set.of("ROLLBACK", "REJECT", "WIRE").contains(mode));
        var armed = new AtomicBoolean(true);
        // 追加在 TransactionInterceptor 内侧，业务SQL执行完毕但事务尚未提交。
        ((Advised) repository).addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (method.equals(invocation.getMethod().getName()) && armed.compareAndSet(true, false)) {
                B0Fixture.require(TransactionSynchronizationManager.isActualTransactionActive());
                System.out.println("B4_TX_EXECUTED method=" + method + " mode=" + mode + " transactionActive=true");
                System.out.flush();
                switch (mode) {
                    case "ROLLBACK" -> throw new IllegalStateException("B4_EXPLICIT_ROLLBACK_BEFORE_COMMIT");
                    case "REJECT" -> jdbc.execute("SET LOCAL nq.b4_reject='on'");
                    // 可见的固定协议标记只定位当前连接；wire proxy 不推断业务SQL或异常类型。
                    case "WIRE" -> jdbc.execute("SET LOCAL application_name='b4-target-commit'");
                    default -> throw new IllegalStateException("unsupported B4 fault");
                }
            }
            return result;
        });
    }
}
