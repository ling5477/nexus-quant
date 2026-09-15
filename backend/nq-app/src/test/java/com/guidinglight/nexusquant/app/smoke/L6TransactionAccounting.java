package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 只计测试进程真正结束的JDBC事务；不保存SQL、参数或业务身份，也不改变事务边界。 */
final class L6TransactionAccounting {
    private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> ORIGIN = new ThreadLocal<>();

    @FunctionalInterface interface Work<T> { T run() throws Exception; }
    static <T> T within(String origin, Work<T> work) throws Exception {
        String previous = ORIGIN.get(); ORIGIN.set(origin);
        try { return work.run(); }
        finally { if (previous == null) ORIGIN.remove(); else ORIGIN.set(previous); }
    }
    static ObjectNode snapshot() {
        var result = new ObjectMapper().createObjectNode();
        new TreeMap<>(COUNTS).forEach((key, count) -> result.put(key, count.get()));
        return result;
    }
    static void connectionEstablished() {
        // 锁定的PG JDBC 42.7.9建连协议含两个隐式事务，由EMPTY真实PG负载差分测试约束。
        COUNTS.computeIfAbsent("CONNECTION_SETUP/OTHER", k -> new AtomicLong()).addAndGet(2);
    }
    static Connection wrap(Connection actual, String fallback) {
        final class State {
            String group;
            void begin() { if (group == null) group = classify(fallback); }
            void end() { if (group != null) COUNTS.computeIfAbsent(group, k -> new AtomicLong()).incrementAndGet(); group = null; }
        }
        var state = new State();
        return (Connection) Proxy.newProxyInstance(L6TransactionAccounting.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("isValid") && !actual.isClosed()
                    && actual.unwrap(org.postgresql.core.BaseConnection.class).getTransactionState() == org.postgresql.core.TransactionState.IDLE) {
                Object result = invoke(actual, method, args);
                COUNTS.computeIfAbsent("CONNECTION_HEALTH/OTHER", k -> new AtomicLong()).incrementAndGet(); return result;
            }
            // PG JDBC的隔离级别getter/setter会发送SHOW/SET；仅SQL代理会漏计这些真实事务。
            if ((name.equals("getTransactionIsolation") || name.equals("setTransactionIsolation"))
                    && actual.unwrap(org.postgresql.core.BaseConnection.class).getTransactionState() == org.postgresql.core.TransactionState.IDLE) {
                Object result = invoke(actual, method, args);
                String group = classify(fallback).split("/")[0] + "/JDBC_SESSION";
                COUNTS.computeIfAbsent(group, k -> new AtomicLong()).incrementAndGet(); return result;
            }
            if (name.equals("commit") || name.equals("rollback") && (args == null || args.length == 0)
                    || name.equals("setAutoCommit") && Boolean.TRUE.equals(args[0]) && !actual.getAutoCommit()) {
                Object result = invoke(actual, method, args); state.end(); return result;
            }
            if (name.equals("close")) {
                Object result = invoke(actual, method, args); state.end(); return result;
            }
            Object result = invoke(actual, method, args);
            if (!(result instanceof Statement statement)) return result;
            Class<?> api = statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return Proxy.newProxyInstance(L6TransactionAccounting.class.getClassLoader(), new Class<?>[]{api}, (p, m, a) -> {
                if (!m.getName().startsWith("execute")) return invoke(statement, m, a);
                state.begin();
                try { return invoke(statement, m, a); }
                finally { if (actual.getAutoCommit()) state.end(); }
            });
        });
    }
    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private static String classify(String fallback) {
        String origin = ORIGIN.get(), owner = "OTHER";
        for (var frame : Thread.currentThread().getStackTrace()) {
            String c = frame.getClassName();
            if (owner.equals("OTHER")) {
                if (c.contains("JdbcTradeRepository")) owner = "TRADE";
                else if (c.contains("JdbcLedger") || c.contains("JdbcAccountSnapshot") || c.contains("JdbcPosition")) owner = "LEDGER";
                else if (c.contains("JdbcOrder")) owner = "ORDER";
                else if (c.contains("JdbcAudit")) owner = "AUDIT";
            }
            if (origin == null && (c.contains("ValidationEvidenceRefreshService") || c.contains("ValidationEvidenceScheduler"))) origin = "VALIDATION";
            if (origin == null && c.contains("StrategyRunRecoveryTick")) origin = "SCHEDULER";
            if (origin == null && c.contains("OkxRestReconcileService")) origin = "RECONCILIATION_OTHER";
        }
        return (origin == null ? fallback : origin) + "/" + owner;
    }
}
