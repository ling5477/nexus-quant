package com.guidinglight.nexusquant.app.smoke;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** 沿用原L6的45秒执行合同；超时取消只是请求中断，调用方仍须闭锁该代。 */
final class L6CommandExecution {
    static final long EXECUTION_SECONDS = 45;

    static <T> T await(ExecutorService executor, Callable<T> work) throws Exception {
        var future = executor.submit(work);
        try { return future.get(EXECUTION_SECONDS, TimeUnit.SECONDS); }
        catch (ExecutionException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            throw error;
        } catch (Exception failure) {
            future.cancel(true);
            throw failure;
        }
    }
}
