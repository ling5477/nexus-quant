package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/** 固定修复前的真实 scheduler；继续调用未改变的普通 transitionOrder，永久保留失败竞争。 */
final class L6ConvergenceBaseline {
    static final String HEAD = "23548b75093a62d7614e16f8abcaf9ff2ea32ed7";
    static Path compile(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path source = directory.resolve("OkxRestReconcileService.java");
        Files.writeString(source, B0Processes.command("git", "-C", B0Processes.root().toString(), "show",
                HEAD + ":" + B4LegacyClasspath.SOURCE));
        Path classes = directory.resolve("classes"); Files.createDirectories(classes);
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        B0Fixture.require(ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-classpath", cp, "-d", classes.toString(), source.toString()) == 0);
        return classes;
    }
}
