package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/** 从已固定失败 HEAD 的真实 producer 源码编译旧进程；不用测试代码制造 Trade 或删除事件。 */
final class B4LegacyClasspath {
    static final String HEAD = "e9509e351e6fbc6179e5e081cb03e66c8cb6ad99";
    static final String SOURCE = "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java";

    static Path compile(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path source = directory.resolve("OkxRestReconcileService.java");
        String original = B0Processes.command("git", "-C", B0Processes.root().toString(), "show", HEAD + ":" + SOURCE);
        Files.writeString(source, original);
        Path classes = directory.resolve("classes"); Files.createDirectories(classes);
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-classpath", cp, "-d", classes.toString(), source.toString());
        B0Fixture.require(result == 0);
        return classes;
    }
}
