package com.guidinglight.nexusquant.strategy.domain.shadowrun;

/**
 * Shadow Run 创建幂等键与不可变 provenance 冲突。
 *
 * <p>相同 {@code idempotencyKey} 只能复用具有相同 {@code publishId} 与
 * {@code artifactDigest} 的既有事实；冲突时必须 fail-closed，不能把另一 release 的
 * Shadow Run 当作当前请求的幂等成功结果。
 */
public class ShadowRunIdempotencyConflictException extends RuntimeException {

    private static final String REASON_CODE = "IDEMPOTENCY_CONFLICT";

    /**
     * 创建不携带具体 anchor 值的安全冲突异常，避免 digest 或请求载荷进入日志/API 错误链。
     */
    public ShadowRunIdempotencyConflictException() {
        super(REASON_CODE + ": idempotency key is already bound to different release provenance");
    }

    /**
     * 返回稳定错误码，供后续 application/API 边界映射；异常消息不包含 digest 或请求载荷。
     */
    public String reasonCode() {
        return REASON_CODE;
    }
}
