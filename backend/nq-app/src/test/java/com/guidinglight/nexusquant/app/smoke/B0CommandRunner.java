package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** 主命令、进程终止和辅助文件清理分别记账，文件占用不能覆盖已取得的命令结果。 */
final class B0CommandRunner {
    @FunctionalInterface interface Starter { Process start(Path log, String[] args) throws Exception; }
    @FunctionalInterface interface Reader { String read(Path log) throws Exception; }
    static final B0CommandRunner DEFAULT = new B0CommandRunner(B0TempLogs.DEFAULT,
            (log, args) -> new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start(),
            Files::readString, Duration.ofSeconds(45), Duration.ofSeconds(5));
    private final B0TempLogs logs;
    private final Starter starter;
    private final Reader reader;
    private final long timeoutNanos;
    private final long cleanupMillis;

    B0CommandRunner(B0TempLogs logs, Starter starter, Reader reader, Duration timeout, Duration cleanup) {
        this.logs = logs; this.starter = starter; this.reader = reader;
        timeoutNanos = timeout.toNanos(); cleanupMillis = cleanup.toMillis();
    }

    String run(BooleanSupplier stop, String... args) throws Exception {
        Path log = logs.create();
        Process process = null;
        Exception primary = null;
        String output = null;
        boolean captured = false;
        boolean interrupted = false;
        try {
            process = starter.start(log, args);
            long deadline = System.nanoTime() + timeoutNanos;
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (stop.getAsBoolean()) throw new L6BusinessCheckpoint.StoragePending();
                if (System.nanoTime() >= deadline) throw new IllegalStateException("B0 command timeout: " + args[0]);
            }
            output = reader.read(log); captured = true;
            if (process.exitValue() != 0) throw new IllegalStateException("B0 command failed: " + output);
        } catch (Exception failure) {
            primary = failure;
            interrupted = failure instanceof InterruptedException;
        } finally {
            // 中断不免除拥有进程的终止责任；完成清理后恢复中断标记。
            interrupted |= Thread.interrupted();
            try {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(cleanupMillis, TimeUnit.MILLISECONDS) || process.isAlive()) {
                        throw new IllegalStateException("B0 command cleanup failed");
                    }
                }
            } catch (Exception safety) {
                if (primary != null) safety.addSuppressed(primary);
                primary = safety;
                interrupted |= safety instanceof InterruptedException;
            }
            try {
                logs.finish(log, captured && (process == null || !process.isAlive()));
            } catch (Exception evidence) {
                if (primary == null) primary = evidence; else primary.addSuppressed(evidence);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
        if (primary != null) throw primary;
        return output.trim();
    }

    static void removeOwnedContainer(String container) throws Exception {
        // 日志预算或观测故障不能阻止已拥有容器的删除；删除后的缺失核验仍由调用方完成。
        runOwnedCleanup(Duration.ofSeconds(45), "docker", "rm", "--force", container);
    }

    static void runOwnedCleanup(Duration timeout, String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        Exception primary = null;
        boolean interrupted = Thread.interrupted();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IllegalStateException("B0 owned cleanup timeout");
            if (process.exitValue() != 0) throw new IllegalStateException("B0 owned cleanup command failed: " + process.exitValue());
        } catch (Exception failure) {
            primary = failure; interrupted |= failure instanceof InterruptedException;
        } finally {
            try {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS) || process.isAlive()) throw new IllegalStateException("B0 owned cleanup process survived");
                }
            } catch (Exception safety) {
                if (primary != null) safety.addSuppressed(primary);
                primary = safety; interrupted |= safety instanceof InterruptedException;
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
        if (primary != null) throw primary;
    }
}
