package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * Shadow Run runner skeleton 运行期异常。
 *
 * <p>失败模式：当 runner 已经创建本地 Shadow Run fact 后遇到运行期异常，会先尽力把 run 推进
 * 到 {@code FAILED}（失败）并写入失败事件，然后通过本异常重新抛出，避免调用方误以为异常已被吞掉。
 * 如果异常发生在 create 之前，{@link #failureResult()} 可能为空。
 */
public class ShadowRunRunnerException extends RuntimeException {

    private final ShadowRunRunnerResult failureResult;

    public ShadowRunRunnerException(String message, Throwable cause, ShadowRunRunnerResult failureResult) {
        super(message, cause);
        this.failureResult = failureResult;
    }

    /**
     * 返回 runner 尝试写入 FAILED 后形成的本地结果；为空表示失败发生在 run 创建前。
     */
    public ShadowRunRunnerResult failureResult() {
        return failureResult;
    }
}
