package com.guidinglight.nexusquant.common.trace;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * TraceIdContext 统一管理 traceId 在 MDC 中的写入与读取。
 *
 * Why:
 * docs/CONTRACTS.md 强制 traceId 贯穿 HTTP/事件/日志。
 * 骨架阶段先固化上下文键名和生成规则，后续模块直接复用，避免重复实现。
 */
public final class TraceIdContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_KEY = "trace_id";

    private TraceIdContext() {
        // 工具类不允许实例化。
    }

    /**
     * 写入 traceId；若入参为空则生成新值。
     *
     * @param traceId 来自上游请求或任务入口的 traceId，可空
     * @return 可用的 traceId
     */
    public static String putOrCreate(String traceId) {
        String resolved = (traceId == null || traceId.isBlank()) ? newTraceId() : traceId;
        MDC.put(TRACE_ID_KEY, resolved);
        return resolved;
    }

    /**
     * @return 当前线程上下文中的 traceId；若不存在返回 null。
     */
    public static String get() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 清理线程上下文，避免线程复用时污染下一次请求。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 生成平台统一格式 traceId。
     *
     * @return 形如 trc-xxxxxxxx 的随机标识
     */
    public static String newTraceId() {
        return "trc-" + UUID.randomUUID();
    }
}
