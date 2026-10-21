package com.guidinglight.nexusquant.common.tenant;

import java.util.Objects;
import java.util.Optional;

/**
 * 单个执行段的可信归属，不继承到子线程，也不携带令牌、连接或事务。
 * 后台任务必须从持久化身份重新验证后显式打开作用域，不能直接复制 HTTP 上下文。
 */
public final class TenantContext {
    private static final ThreadLocal<TenantIdentity> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<TenantIdentity> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantIdentity requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException("trusted tenant context required"));
    }

    /** 调用方只能传入经认证解析器重新校验的身份，并在同一线程关闭作用域。 */
    public static Scope open(TenantIdentity verifiedIdentity) {
        var previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(verifiedIdentity, "verifiedIdentity"));
        return new Scope(previous);
    }

    public static void clear() {
        CURRENT.remove();
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
