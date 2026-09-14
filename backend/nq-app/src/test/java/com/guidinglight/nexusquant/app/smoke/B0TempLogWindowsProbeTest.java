package com.guidinglight.nexusquant.app.smoke;

import com.sun.nio.file.ExtendedOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 使用Windows真实禁止删除共享句柄，验证输出消费后仍可继续后续命令。 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named="nq.tempLog.probe", matches="true")
class B0TempLogWindowsProbeTest {
    @TempDir Path directory;

    @Test void repeatedRealChildrenSurviveRealWindowsDeleteLocks() throws Exception {
        List<Map<String,Object>> findings = new ArrayList<>();
        List<Process> children = new ArrayList<>();
        List<FileChannel> locks = new ArrayList<>();
        var logs = new B0TempLogs(directory, Files::deleteIfExists, findings::add, 4, 4096);
        var runner = new B0CommandRunner(logs, (path,args) -> {
            Process p = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(path.toFile()).start();
            children.add(p); return p;
        }, path -> {
            String output = Files.readString(path);
            locks.add(FileChannel.open(path, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE));
            return output;
        }, Duration.ofSeconds(5), Duration.ofSeconds(2));
        try {
            for (int i=0; i<24; i++) {
                assertTrue(runner.run(() -> false, B0CommandCleanupTest.child("ok")).contains("标准输出"));
                assertEquals(1, logs.registeredCount());
                locks.removeFirst().close();
                logs.sweep(); assertEquals(0, logs.registeredCount());
            }
            assertEquals(72, findings.size());
            assertTrue(children.stream().noneMatch(Process::isAlive));
            try (var files=Files.list(directory)) { assertEquals(0,files.count()); }
            System.out.println("B0_WINDOWS_TEMP_PROBE commands=24 deleteFindings=72 survivors=0 retainedFiles=0 PASS");
        } finally {
            for (var lock : locks) lock.close();
            for (var child : children) if(child.isAlive()) { child.destroyForcibly(); child.waitFor(); }
            logs.sweep();
        }
    }
}
