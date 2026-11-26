package com.guidinglight.nexusquant.app.marketdata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 在独立 JVM 验证进程禁令，父进程的环境与 Spring 全局设置不受影响。 */
public final class PublicMarketReadProcess {
    private PublicMarketReadProcess() {}

    public static void run(Class<?> fixture, Path log, Map<String, String> vetoes, String... args)
            throws Exception {
        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dspring.getenv.ignore=true", "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("java.class.path"), fixture.getName()));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("CI");
        builder.environment().remove("NQ_NO_OUTBOUND");
        builder.environment().putAll(vetoes);
        var process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "公开行情许可验证子进程超时");
            assertEquals(0, process.exitValue(), Files.readString(log));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "公开行情许可验证子进程未退出");
            }
        }
    }
}
