package com.guidinglight.nexusquant.common.tenant;

import java.util.Objects;
import java.util.Optional;

/**
 * 单个执行段的可信归属，不继承到子线程，也不携带令牌、连接或事务。
 * 后台任务必须从持久化身份重新验证后显式打开作用域，不能直接复制 HTTP 上下文。
 */
public final class TenantContext {
    private static final ThreadLocal<TenantIdentity> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<TenantExecutionIdentity> EXECUTION = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<TenantIdentity> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantIdentity requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException("trusted tenant context required"));
    }

    public static Optional<TenantExecutionIdentity> execution() {
        return Optional.ofNullable(EXECUTION.get());
    }

    public static Optional<TenantId> currentTenantId() {
        var user = current().map(TenantIdentity::tenantId);
        var worker = execution().map(TenantExecutionIdentity::tenantId);
        if (user.isPresent() && worker.isPresent() && !user.get().equals(worker.get())) {
            throw new IllegalStateException("TENANT_ACCESS_DENIED: mixed execution identity");
        }
        return user.isPresent() ? user : worker;
    }

    public static TenantId requireTenantId() {
        return currentTenantId().orElseThrow(() -> new IllegalStateException("TENANT_ACCESS_DENIED: trusted scope required"));
    }

    /** 仅限后台身份恢复端口传入刚从业务对象及控制表取得的执行身份。 */
    public static ExecutionScope openExecution(TenantExecutionIdentity verifiedIdentity) {
        Objects.requireNonNull(verifiedIdentity, "verifiedIdentity");
        if (currentTenantId().isPresent() && !requireTenantId().equals(verifiedIdentity.tenantId())) {
            throw new IllegalStateException("TENANT_ACCESS_DENIED: execution scope mismatch");
        }
        var previous = EXECUTION.get();
        EXECUTION.set(verifiedIdentity);
        return new ExecutionScope(previous);
    }

    /** 调用方只能传入经认证解析器重新校验的身份，并在同一线程关闭作用域。 */
    public static Scope open(TenantIdentity verifiedIdentity) {
        var previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(verifiedIdentity, "verifiedIdentity"));
        return new Scope(previous);
    }

    public static void clear() {
        CURRENT.remove();
        EXECUTION.remove();
    }

    public static final class ExecutionScope implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final TenantExecutionIdentity previous;
        private boolean closed;
        private ExecutionScope(TenantExecutionIdentity previous) { this.previous = previous; }
        @Override public void close() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("tenant execution thread mismatch");
            if (!closed) {
                closed = true;
                if (previous == null) EXECUTION.remove(); else EXECUTION.set(previous);
            }
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final TenantIdentity previous;
        private boolean closed;

        private Scope(TenantIdentity previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("tenant scope thread mismatch");
            if (!closed) {
                closed = true;
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        }
    }
}
