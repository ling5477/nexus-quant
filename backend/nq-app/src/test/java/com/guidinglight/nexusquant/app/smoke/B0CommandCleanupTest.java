package com.guidinglight.nexusquant.app.smoke;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 同时覆盖真实子进程结果与可控的Windows删除异常，不启动交易qualification。 */
class B0CommandCleanupTest {
    @TempDir Path directory;
    final List<Map<String, Object>> findings = new ArrayList<>();
    final List<Process> processes = new ArrayList<>();

    B0TempLogs logs(B0TempLogs.Delete delete, int files, long bytes) {
        return new B0TempLogs(directory, delete, findings::add, files, bytes);
    }

    B0CommandRunner runner(B0TempLogs logs, B0CommandRunner.Reader read, Duration timeout) {
        return new B0CommandRunner(logs, (path, args) -> {
            Process p = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(path.toFile()).start();
            processes.add(p); return p;
        }, read, timeout, Duration.ofSeconds(2));
    }

    static String[] child(String mode) {
        return new String[]{Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), Output.class.getName(), mode};
    }

    void noSurvivors() { assertTrue(processes.stream().noneMatch(Process::isAlive)); }

    @Test void successCapturesBothStreamsAndDeletes() throws Exception {
        var logs = logs(Files::deleteIfExists, 2, 1024);
        assertEquals("标准输出\n标准错误", runner(logs, Files::readString, Duration.ofSeconds(5)).run(() -> false, child("ok")).replace("\r", ""));
        assertEquals(0, logs.registeredCount()); assertTrue(findings.isEmpty()); noSurvivors();
    }

    @Test void transientDeleteIsObservableAndDoesNotInvalidateSuccess() throws Exception {
        var tries = new AtomicInteger();
        var logs = logs(path -> { if (tries.incrementAndGet()==1) throw new AccessDeniedException(path.toString()); Files.deleteIfExists(path); }, 2, 1024);
        var r = runner(logs, Files::readString, Duration.ofSeconds(5));
        assertTrue(r.run(() -> false, child("ok")).contains("标准输出"));
        assertEquals(2, tries.get()); assertEquals("NON_BLOCKING", findings.getFirst().get("classification"));
        assertTrue(r.run(() -> false, child("ok")).contains("标准输出"));
        assertEquals(0, logs.registeredCount()); noSurvivors();
    }

    @Test void persistentDeletionIsBoundedAndDeferredCleanupRecovers() throws Exception {
        var tries = new AtomicInteger();
        var locked = new AtomicBoolean(true);
        var logs = logs(path -> { tries.incrementAndGet(); if (locked.get()) throw new AccessDeniedException(path.toString()); Files.deleteIfExists(path); }, 2, 1024);
        var r = runner(logs, Files::readString, Duration.ofSeconds(5));
        assertTrue(r.run(() -> false, child("ok")).contains("标准输出"));
        assertEquals(3, tries.get()); assertEquals(1, logs.registeredCount()); noSurvivors();
        assertTrue(r.run(() -> false, child("ok")).contains("标准输出"));
        assertEquals(7, tries.get());
        assertThrows(IOException.class, () -> r.run(() -> false, child("ok")));
        assertEquals(2, processes.size());
        locked.set(false); logs.sweep(); assertEquals(0, logs.registeredCount()); noSurvivors();
    }

    @Test void failedExitRemainsPrimaryDespiteDeletionFailure() throws Exception {
        var logs = logs(path -> { throw new AccessDeniedException(path.toString()); }, 2, 1024);
        var error = assertThrows(IllegalStateException.class, () -> runner(logs, Files::readString, Duration.ofSeconds(5)).run(() -> false, child("fail")));
        assertTrue(error.getMessage().startsWith("B0 command failed:")); assertEquals(3, findings.size()); noSurvivors();
    }

    @Test void timeoutTerminatesOwnedProcessAndPreservesUnconsumedEvidence() {
        var logs = logs(Files::deleteIfExists, 2, 1024);
        var error = assertThrows(IllegalStateException.class, () -> runner(logs, Files::readString, Duration.ofMillis(250)).run(() -> false, child("sleep")));
        assertTrue(error.getMessage().startsWith("B0 command timeout:")); assertEquals(1, logs.registeredCount()); noSurvivors();
    }

    @Test void stopSupplierRetainsStoragePendingAndKillsProcess() {
        var logs = logs(Files::deleteIfExists, 2, 1024);
        assertThrows(L6BusinessCheckpoint.StoragePending.class, () -> runner(logs, Files::readString, Duration.ofSeconds(5)).run(() -> true, child("sleep")));
        noSurvivors();
    }

    @Test void unreadableOutputFailsAndIsNeverDeleted() {
        var deletes = new AtomicInteger();
        var logs = logs(path -> deletes.incrementAndGet(), 2, 1024);
        assertThrows(IOException.class, () -> runner(logs, path -> { throw new IOException("unreadable evidence"); }, Duration.ofSeconds(5)).run(() -> false, child("ok")));
        assertEquals(0, deletes.get()); assertEquals(1, logs.registeredCount()); noSurvivors();
    }

    @Test void processCleanupFailureOverridesStopAndRetainsPrimaryAsSuppressed() throws Exception {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(process.destroyForcibly()).thenReturn(process);
        var logs = logs(Files::deleteIfExists, 2, 1024);
        var r = new B0CommandRunner(logs, (path,args) -> process, Files::readString, Duration.ZERO, Duration.ZERO);
        var error = assertThrows(IllegalStateException.class, () -> r.run(() -> true, "fake"));
        assertEquals("B0 command cleanup failed", error.getMessage());
        assertInstanceOf(L6BusinessCheckpoint.StoragePending.class, error.getSuppressed()[0]);
        verify(process).destroyForcibly(); assertEquals(1, logs.registeredCount());
    }

    @Test void residueByteBudgetAndObservationFailureAreBlocking() {
        var logs = logs(path -> { throw new AccessDeniedException(path.toString()); }, 2, 1);
        assertThrows(IOException.class, () -> runner(logs, Files::readString, Duration.ofSeconds(5)).run(() -> false, child("ok")));
        var broken = new B0TempLogs(directory, path -> { throw new AccessDeniedException(path.toString()); }, finding -> { throw new IOException("observation unavailable"); }, 2, 1024);
        assertThrows(IOException.class, () -> runner(broken, Files::readString, Duration.ofSeconds(5)).run(() -> false, child("ok")));
        noSurvivors();
    }

    @Test void ownedCleanupStillExecutesAfterObservationAndBudgetFailures() throws Exception {
        var broken = new B0TempLogs(directory, path -> { throw new AccessDeniedException(path.toString()); },
                finding -> { throw new IOException("observation unavailable"); }, 1, 1);
        var r = runner(broken, Files::readString, Duration.ofSeconds(5));
        assertThrows(IOException.class, () -> r.run(() -> false, child("ok")));
        assertThrows(IOException.class, () -> r.run(() -> false, child("ok")));
        var exhausted = runner(logs(path -> { throw new AccessDeniedException(path.toString()); }, 1, 1), Files::readString, Duration.ofSeconds(5));
        assertThrows(IOException.class, () -> exhausted.run(() -> false, child("ok")));
        assertThrows(IOException.class, () -> exhausted.run(() -> false, child("ok")));
        Path marker = directory.resolve("cleanup-started");
        String[] command = child(marker.toString());
        B0CommandRunner.runOwnedCleanup(Duration.ofSeconds(5), command);
        long pid = Long.parseLong(Files.readString(marker));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        assertThrows(IllegalStateException.class, () -> B0CommandRunner.runOwnedCleanup(Duration.ofSeconds(5), child("fail")));
        Path sleeping = directory.resolve("cleanup-timeout-pid");
        assertThrows(IllegalStateException.class, () -> B0CommandRunner.runOwnedCleanup(Duration.ofSeconds(1), child("sleep@" + sleeping)));
        long sleepingPid = Long.parseLong(Files.readString(sleeping));
        assertFalse(ProcessHandle.of(sleepingPid).map(ProcessHandle::isAlive).orElse(false));
        noSurvivors();
    }

    public static final class Output {
        public static void main(String[] args) throws Exception {
            // Windows重定向流不保证采用file.encoding，夹具按字节显式输出UTF-8。
            System.out.write("标准输出\n".getBytes(StandardCharsets.UTF_8)); System.out.flush();
            System.err.write("标准错误\n".getBytes(StandardCharsets.UTF_8)); System.err.flush();
            if (args[0].startsWith("sleep@")) {
                Files.writeString(Path.of(args[0].substring(6)), Long.toString(ProcessHandle.current().pid()));
                Thread.sleep(30000); return;
            }
            if (args[0].equals("sleep")) Thread.sleep(30000);
            if (args[0].equals("fail")) System.exit(7);
            if (!List.of("ok","sleep","fail").contains(args[0])) Files.writeString(Path.of(args[0]), Long.toString(ProcessHandle.current().pid()));
        }
    }
}
