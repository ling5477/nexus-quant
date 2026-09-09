package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 沿用 F002 ProcessBuilder/日志/PID 方式；B0 增加交互屏障和拥有实例身份的 PostgreSQL。 */
final class B0Processes {
    static Map<String, String> cleanEnvironment() {
        Map<String, String> result = new LinkedHashMap<>();
        // 仅读取必要的 OS 变量，既不继承也不读取机器 NQ、Spring、Java options 或 provider 凭证。
        for (String key : List.of("SystemRoot", "WINDIR", "PATH", "TEMP", "TMP", "COMSPEC")) {
            String value = System.getenv(key);
            if (value != null) result.put(key, value);
        }
        return result;
    }

    static Path root() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve("scripts/ci/delivery-supply-chain-lock.json"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) throw new IllegalStateException("repository root missing");
        return cursor;
    }

    static String command(String... args) throws Exception {
        Path log = Files.createTempFile("nq-b0-command-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) throw new IllegalStateException("B0 command timeout: " + args[0]);
            String result = Files.readString(log);
            if (process.exitValue() != 0) throw new IllegalStateException("B0 command failed: " + result);
            return result.trim();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IllegalStateException("B0 command cleanup failed");
            }
            Files.deleteIfExists(log);
        }
    }

    static final class Pg implements AutoCloseable {
        private final String name;
        private final String container;
        private final String url;
        private Pg(String name, String container, String url) { this.name = name; this.container = container; this.url = url; }

        static Pg start() throws Exception {
            var lock = new ObjectMapper().readTree(root().resolve("scripts/ci/delivery-supply-chain-lock.json").toFile());
            // 复用 canonical CI 锁定镜像；只允许本地缓存，默认测试不向 registry 发起下载。
            String digest = null;
            for (var node : lock.findParents("digest")) {
                if ("docker.io/library/postgres:16".equals(node.path("source").asText())) digest = node.path("digest").asText();
            }
            B0Fixture.require(digest != null && digest.matches("sha256:[a-f0-9]{64}"));
            String image = "postgres:16@" + digest;
            String name = "nq-b0-" + UUID.randomUUID();
            String container = command("docker", "run", "--detach", "--pull=never", "--name", name,
                    "--label", "nq.b0.identity=" + name, "--tmpfs", "/var/lib/postgresql/data",
                    "--publish", "127.0.0.1::5432", "--env", "POSTGRES_HOST_AUTH_METHOD=trust", image);
            Pg pg = new Pg(name, container, "");
            try {
                String binding = command("docker", "port", container, "5432/tcp");
                B0Fixture.require(binding.matches("127\\.0\\.0\\.1:[0-9]+"));
                pg = new Pg(name, container, "jdbc:postgresql://" + binding + "/postgres");
                long deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos();
                while (true) {
                    try (var connection = DriverManager.getConnection(pg.url, "postgres", "")) { break; }
                    catch (java.sql.SQLException failure) {
                        if (System.nanoTime() > deadline) throw failure;
                        Thread.sleep(100);
                    }
                }
                System.out.println("B0_PG container=" + name + " id=" + container + " url=" + pg.url + " image=" + image);
                return pg;
            } catch (Exception failure) {
                pg.close();
                throw failure;
            }
        }

        String ownedUrl() throws Exception {
            String label = new ObjectMapper().readTree(command("docker", "inspect", container))
                    .get(0).path("Config").path("Labels").path("nq.b0.identity").asText();
            B0Fixture.require(name.equals(label));
            String binding = command("docker", "port", container, "5432/tcp");
            B0Fixture.require(url.equals("jdbc:postgresql://" + binding + "/postgres"));
            return url;
        }

        boolean databaseAbsent(String database) throws Exception {
            try (var connection = DriverManager.getConnection(ownedUrl(), "postgres", "");
                 var query = connection.prepareStatement("SELECT count(*) FROM pg_database WHERE datname=?")) {
                query.setString(1, database);
                var result = query.executeQuery(); result.next(); return result.getLong(1) == 0;
            }
        }

        @Override public void close() throws Exception {
            command("docker", "rm", "--force", container);
            String remaining = command("docker", "ps", "-a", "--filter", "id=" + container, "--format", "{{.ID}}");
            B0Fixture.require(remaining.isBlank());
            System.out.println("B0_CLEANUP container=" + name + " remaining=0");
        }
    }

    static final class Child implements AutoCloseable {
        final Process process;
        final Path log;
        private final java.io.BufferedWriter input;
        private int resultCount;

        Child(Class<?> main, Path directory, String label, Map<String, String> environment) throws Exception {
            this(main, directory, label, environment, null);
        }

        /** B4 仅给隔离 JVM 前置由本地历史源码编译的测试 classpath，不修改当前生产类。 */
        Child(Class<?> main, Path directory, String label, Map<String, String> environment, Path legacyClasses) throws Exception {
            Files.createDirectories(directory);
            log = directory.resolve(label + ".log");
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            if (legacyClasses != null) {
                B0Fixture.require(legacyClasses.toAbsolutePath().normalize()
                        .startsWith(root().resolve("backend/nq-app/target").toAbsolutePath().normalize()));
                classpath = legacyClasses.toAbsolutePath() + java.io.File.pathSeparator + classpath;
            }
            Path argfile = directory.resolve(label + ".args");
            Files.writeString(argfile, "-Dfile.encoding=UTF-8\n-Dstdout.encoding=UTF-8\n-Dstderr.encoding=UTF-8\n"
                    + "-Duser.language=en\n-Duser.country=US\n-cp\n\"" + classpath.replace("\\", "\\\\").replace("\"", "\\\"")
                    + "\"\n" + main.getName() + "\n");
            String executable = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            ProcessBuilder builder = new ProcessBuilder(executable, "@" + argfile.toAbsolutePath());
            builder.environment().clear();
            builder.environment().putAll(environment);
            builder.directory(directory.toFile());
            builder.redirectErrorStream(true).redirectOutput(log.toFile());
            process = builder.start();
            input = process.outputWriter();
            System.out.println("B0_PROCESS label=" + label + " pid=" + process.pid() + " command=" + executable
                    + " @" + argfile.toAbsolutePath());
        }

        String ready() throws Exception { return await("B0_READY ", 1); }

        String send(String command) throws Exception {
            startCommand(command);
            return result();
        }

        void startCommand(String command) throws Exception {
            input.write(command); input.newLine(); input.flush();
            resultCount++;
        }

        String result() throws Exception { return await("B0_RESULT ", resultCount); }

        private String await(String prefix, int count) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(75).toNanos();
            while (System.nanoTime() < deadline) {
                List<String> matches = Files.readAllLines(log).stream().filter(line -> line.startsWith(prefix)).toList();
                if (matches.size() >= count) return matches.get(count - 1).substring(prefix.length());
                if (!process.isAlive()) throw new AssertionError("B0 child exited: " + Files.readString(log));
                Thread.sleep(100);
            }
            throw new AssertionError("B0 child timeout, log=" + log);
        }

        void kill() throws Exception {
            process.destroyForcibly();
            B0Fixture.require(process.waitFor(10, TimeUnit.SECONDS) && !process.isAlive());
            System.out.println("B0_KILLED pid=" + process.pid() + " exit=" + process.exitValue());
        }

        @Override public void close() throws Exception {
            if (process.isAlive()) kill();
            input.close();
            B0Fixture.require(!process.isAlive());
        }
    }
}
