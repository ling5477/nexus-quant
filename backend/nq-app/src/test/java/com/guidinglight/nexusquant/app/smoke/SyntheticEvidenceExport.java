package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

/** 仅在原始证据落盘后导出 canonical 副本，保持真实进程与随机运行身份不变。 */
final class SyntheticEvidenceExport {
    private SyntheticEvidenceExport() { }

    static Path scripts() {
        return B0Processes.root().resolve("backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke");
    }

    static void write(Path raw, String batch, int run) throws Exception {
        String name = raw.getFileName().toString();
        if (!name.startsWith("raw-")) {
            throw new IllegalArgumentException("raw evidence filename must start with raw-");
        }
        Path canonical = raw.resolveSibling(name.substring(4));
        execute("synthetic_evidence.py", raw.toString(), canonical.toString(), batch, Integer.toString(run));
    }

    static void execute(String script, String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add("python");
        command.add(scripts().resolve(script).toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (!process.waitFor(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("synthetic evidence export timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("synthetic evidence export failed: " + process.exitValue());
        }
    }
}
