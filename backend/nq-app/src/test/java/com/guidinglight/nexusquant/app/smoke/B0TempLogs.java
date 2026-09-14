package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 只管理本实例创建的日志；最多32个登记项和16MiB残留，不扫描或删除外部文件。 */
final class B0TempLogs {
    @FunctionalInterface interface Delete { void delete(Path path) throws IOException; }
    @FunctionalInterface interface Observe { void record(Map<String, Object> finding) throws IOException; }
    static final B0TempLogs DEFAULT = new B0TempLogs(null, Files::deleteIfExists, finding -> {
        System.err.println("B0_TEMP_LOG_CLEANUP " + new ObjectMapper().writeValueAsString(finding));
        if (System.err.checkError()) throw new IOException("B0 cleanup observation unavailable");
    }, 32, 16L * 1024 * 1024);
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { DEFAULT.sweep(); }
            catch (IOException failure) { System.err.println("B0_TEMP_LOG_FINAL_CLEANUP_FAILURE " + failure); }
        }, "b0-temp-log-cleanup"));
    }
    private final Path directory;
    private final Delete delete;
    private final Observe observe;
    private final int maxFiles;
    private final long maxBytes;
    // false 表示命令未完成或证据未读取，任何延后清理都不得删除它。
    private final Map<Path, Boolean> owned = new LinkedHashMap<>();

    B0TempLogs(Path directory, Delete delete, Observe observe, int maxFiles, long maxBytes) {
        this.directory = directory; this.delete = delete; this.observe = observe;
        this.maxFiles = maxFiles; this.maxBytes = maxBytes;
    }

    synchronized Path create() throws IOException {
        sweep();
        requireBudget();
        if (owned.size() >= maxFiles) throw new IOException("B0 temporary log count budget exhausted");
        Path path = directory == null ? Files.createTempFile("nq-b0-command-", ".log")
                : Files.createTempFile(directory, "nq-b0-command-", ".log");
        owned.put(path, false);
        return path;
    }

    synchronized void finish(Path path, boolean consumedAndDead) throws IOException {
        if (!owned.containsKey(path)) throw new IOException("B0 unowned temporary log");
        owned.put(path, consumedAndDead);
        if (consumedAndDead) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try { delete.delete(path); owned.remove(path); return; }
                catch (IOException failure) {
                    finding(path, "NON_BLOCKING", attempt, failure.getClass().getSimpleName());
                    if (attempt < 3) {
                        try { Thread.sleep(attempt * 10L); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }
        } else {
            finding(path, "EVIDENCE_RETAINED", 0, "OUTPUT_NOT_CAPTURED_OR_PROCESS_NOT_CONFIRMED_DEAD");
        }
        requireBudget();
    }

    synchronized void sweep() throws IOException {
        for (var entry : Map.copyOf(owned).entrySet()) {
            if (!entry.getValue()) continue;
            try { delete.delete(entry.getKey()); owned.remove(entry.getKey()); }
            catch (IOException failure) { finding(entry.getKey(), "NON_BLOCKING_DEFERRED", 1, failure.getClass().getSimpleName()); }
        }
    }

    private void requireBudget() throws IOException {
        long bytes = 0;
        for (Path path : owned.keySet()) bytes = Math.addExact(bytes, Files.size(path));
        if (bytes > maxBytes) throw new IOException("B0 temporary log byte budget exhausted: " + bytes);
    }

    private void finding(Path path, String classification, int attempt, String reason) throws IOException {
        observe.record(Map.of("path", path.toString(), "classification", classification,
                "attempt", attempt, "reason", reason, "registeredCount", owned.size()));
    }

    synchronized int registeredCount() { return owned.size(); }
}
