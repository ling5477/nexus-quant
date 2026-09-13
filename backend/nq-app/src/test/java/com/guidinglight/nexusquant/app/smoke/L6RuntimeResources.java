package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.number;

/** 所有collector只读取本轮持有的连接、PID、容器和目录；不扫描其他任务资源。 */
final class L6RuntimeResources implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final Set<String> ACTOR_FIELDS = Set.of("heapUsed", "heapCommitted", "heapMax", "gc", "threads", "peakThreads",
            "commandQueue", "metricsExecutor", "active", "idle", "pending", "poolMax", "acquisitionTimeoutCount",
            "acquisitionTimeoutDelta", "tickStarted", "tickCompleted", "tickFailed", "observations", "candidateAge");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final Connection reader;
    private final Path directory;
    private final List<ProcessHandle> processes;
    private final Map<Long, String> generations = new LinkedHashMap<>();
    private final String container;
    private long previousLogBytes;
    private final Map<String, L6ResourceSampler.Collector> collectors = new LinkedHashMap<>();
    private final Map<String, Set<String>> required = new LinkedHashMap<>();

    L6RuntimeResources(Connection reader, Path directory, List<B0Processes.Child> actors,
                       B0Processes.Child venue, String endpoint, String container) throws Exception {
        this.reader = reader;
        this.directory = directory.toAbsolutePath().normalize();
        this.container = container;
        B0Fixture.require(container.matches("[a-f0-9]{64}"));
        B0Fixture.requireVenue(endpoint);
        processes = java.util.stream.Stream.concat(java.util.stream.Stream.of(ProcessHandle.current(), venue.process.toHandle()),
                actors.stream().map(c -> c.process.toHandle())).toList();
        for (var process : processes) generations.put(process.pid(), process.info().startInstant().orElseThrow().toString());
        previousLogBytes = logBytes();
        try (var statement = reader.createStatement()) { statement.execute("SET statement_timeout='2000ms'"); }
        for (int i = 0; i < actors.size(); i++) {
            long pid = actors.get(i).process.pid();
            String metrics = Files.readString(directory.resolve("l6-metrics-" + pid + ".endpoint"));
            B0Fixture.requireVenue(metrics);
            long[] sequence = {0};
            add("nq" + i, ACTOR_FIELDS, stamp -> {
                ObjectNode value = get(metrics + "/metrics", stamp.token());
                if (!stamp.token().equals(value.path("sampleToken").asText())
                        || value.path("pid").asLong() != pid || value.path("observationSequence").asLong() <= sequence[0]) {
                    throw new IllegalStateException("L6_STALE_ACTOR_OBSERVATION");
                }
                sequence[0] = value.path("observationSequence").asLong();
                L6Measurements.requireMandatory(value);
                for (String field : List.of("heapUsed", "heapCommitted", "heapMax", "threads", "peakThreads",
                        "active", "idle", "pending", "poolMax", "tickStarted", "tickCompleted", "tickFailed")) numeric(value, field);
                if (!value.path("gc").isArray() || value.path("gc").isEmpty()) throw new IllegalStateException("GC_UNAVAILABLE");
                for (var gc : value.path("gc")) { numeric(gc, "count"); numeric(gc, "timeMillis"); }
                if (value.path("tickFailed").asLong() != 0) throw new IllegalStateException("L6_RECOVERY_TICK_FAILED");
                return measured(stamp, value, ACTOR_FIELDS);
            });
        }
        add("venue", Set.of("active", "queue", "capacity", "completed", "rejected"), stamp -> {
            ObjectNode value = get(endpoint + "/l5-metrics", stamp.token());
            for (String key : required.get("venue")) numeric(value, key);
            if (value.path("capacity").asInt() != 16 || value.path("queue").asInt() > 16) {
                throw new IllegalStateException("L6_VENUE_QUEUE_BOUND");
            }
            return measured(stamp, value, required.get("venue"));
        });
        add("postgres", Set.of("appConnections", "databaseConnections", "idleInTransaction", "backlog", "actionable",
                "auditRows", "eventRows", "transactions", "cursor"), stamp -> {
            ObjectNode value = L5BoundedWorkloadTest.sample(reader);
            value.put("databaseConnections", number(reader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()"));
            value.put("idleInTransaction", number(reader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state LIKE 'idle in transaction%'"));
            value.put("auditRows", number(reader, "SELECT count(*) FROM audit_logs"));
            value.put("eventRows", number(reader, "SELECT count(*) FROM event_store"));
            value.set("cursor", L5BoundedWorkloadTest.cursor(reader));
            if (value.path("transactions").asLong() > 1_000_000) throw new IllegalStateException("L6_TRANSACTION_BUDGET");
            return measured(stamp, value, required.get("postgres"));
        });
        add("os", Set.of("handles", "fd", "processes"), this::os);
        add("files", Set.of("logBytes", "logDeltaBytes", "ownedTempFileCount", "ownedTempBytes"), this::files);
        add("ownership", Set.of("ownedProcessCount", "ownedContainerCount", "pidGenerations"), stamp -> {
            for (var process : processes) {
                if (!process.isAlive() || !generations.get(process.pid()).equals(process.info().startInstant().orElseThrow().toString())) {
                    throw new IllegalStateException("L6_OWNED_PROCESS_GENERATION_LOST");
                }
            }
            String state = command("docker", "inspect", "--format", "{{.Id}} {{.State.Running}}", container).trim();
            if (!state.equals(container + " true")) throw new IllegalStateException("L6_OWNED_CONTAINER_LOST");
            ObjectNode value = JSON.createObjectNode().put("ownedProcessCount", processes.size()).put("ownedContainerCount", 1);
            value.set("pidGenerations", JSON.valueToTree(generations));
            return measured(stamp, value, required.get("ownership"));
        });
    }

    Map<String, L6ResourceSampler.Collector> collectors() { return collectors; }
    Map<String, Set<String>> required() { return required; }
    private void add(String key, Set<String> fields, L6ResourceSampler.Collector collector) {
        required.put(key, fields); collectors.put(key, collector);
    }

    static L6ResourceSampler.Observation measured(L6ResourceSampler.Stamp stamp, ObjectNode value, Set<String> fields) {
        ObjectNode states = JSON.createObjectNode();
        fields.stream().sorted().forEach(field -> states.put(field, "MEASURED"));
        return new L6ResourceSampler.Observation(stamp, value, states);
    }

    private ObjectNode get(String url, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2))
                .header("X-L6-Sample", token).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("L6_RESOURCE_HTTP_UNAVAILABLE: " + response.body());
        return (ObjectNode) JSON.readTree(response.body());
    }

    private L6ResourceSampler.Observation os(L6ResourceSampler.Stamp stamp) throws Exception {
        ObjectNode value = JSON.createObjectNode();
        var states = JSON.createObjectNode().put("processes", "MEASURED");
        if (System.getProperty("os.name").startsWith("Windows")) {
            String ids = processes.stream().map(p -> Long.toString(p.pid())).collect(java.util.stream.Collectors.joining(","));
            JsonNode rows = JSON.readTree(command("powershell", "-NoProfile", "-Command",
                    "@(Get-Process -Id " + ids + " -ErrorAction Stop | Select-Object Id,HandleCount,WorkingSet64) | ConvertTo-Json -Compress"));
            if (!rows.isArray() || rows.size() != processes.size()) throw new IllegalStateException("L6_OS_PROCESS_MISSING");
            long handles = 0;
            for (var row : rows) {
                numeric(row, "HandleCount"); numeric(row, "WorkingSet64");
                if (!generations.containsKey(row.path("Id").asLong())) throw new IllegalStateException("L6_UNOWNED_OS_PROCESS");
                handles += row.path("HandleCount").asLong();
            }
            value.set("processes", rows); value.put("handles", handles).put("fd", "NOT_APPLICABLE_WINDOWS");
            states.put("handles", "MEASURED").put("fd", "NOT_APPLICABLE");
        } else if (System.getProperty("os.name").equals("Linux")) {
            long count = 0;
            var rows = value.putArray("processes");
            for (var process : processes) {
                try (var fds = Files.list(Path.of("/proc", Long.toString(process.pid()), "fd"))) {
                    long fd = fds.count(); count += fd; rows.addObject().put("pid", process.pid()).put("fd", fd);
                }
            }
            value.put("fd", count).put("handles", "NOT_APPLICABLE_LINUX");
            states.put("fd", "MEASURED").put("handles", "NOT_APPLICABLE");
        } else { throw new IllegalStateException("L6_OS_COUNTER_UNAVAILABLE"); }
        return new L6ResourceSampler.Observation(stamp, value, states);
    }

    private long logBytes() throws Exception {
        long bytes = 0;
        try (var files = Files.list(directory)) {
            for (var file : files.filter(p -> p.getFileName().toString().endsWith(".log")).toList()) bytes += Files.size(file);
        }
        return bytes;
    }

    private L6ResourceSampler.Observation files(L6ResourceSampler.Stamp stamp) throws Exception {
        long logs = logBytes(), bytes = 0, count = 0;
        try (var paths = Files.walk(directory)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isSymbolicLink(path)) throw new IllegalStateException("L6_UNOWNED_PATH_LINK");
                if (Files.isRegularFile(path)) {
                    if (++count > 10_000) throw new IllegalStateException("L6_FILE_COUNT_SAFETY_LIMIT");
                    bytes += Files.size(path);
                }
            }
        }
        if (bytes >= 1024L * 1024 * 1024 || Files.getFileStore(directory).getUsableSpace() < 2L * 1024 * 1024 * 1024) {
            throw new IllegalStateException("L6_DISK_SAFETY_LIMIT");
        }
        if (logs < previousLogBytes) throw new IllegalStateException("L6_UNEXPLAINED_LOG_TRUNCATION");
        ObjectNode value = JSON.createObjectNode().put("logBytes", logs).put("logDeltaBytes", logs - previousLogBytes)
                .put("ownedTempFileCount", count).put("ownedTempBytes", bytes).put("ownedPath", directory.toString());
        previousLogBytes = logs;
        return measured(stamp, value, required.get("files"));
    }

    private String command(String... arguments) throws Exception {
        // 采集子进程日志也属于本轮目录；两秒超时后只回收本次创建的Process。
        Path log = Files.createTempFile(directory, "resource-command-", ".tmp");
        Process process = null;
        try {
            var builder = new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().clear(); builder.environment().putAll(B0Processes.cleanEnvironment());
            process = builder.start();
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("L6_RESOURCE_COMMAND_TIMEOUT");
            if (process.exitValue() != 0) throw new IllegalStateException("L6_RESOURCE_COMMAND_FAILED: " + Files.readString(log));
            return Files.readString(log);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("L6_RESOURCE_COMMAND_SURVIVOR");
            }
            Files.deleteIfExists(log);
        }
    }

    private static void numeric(JsonNode value, String key) {
        if (!value.path(key).isNumber() || value.path(key).asDouble() < 0 || !Double.isFinite(value.path(key).asDouble())) {
            throw new IllegalStateException("L6_MANDATORY_MEASUREMENT_UNAVAILABLE: " + key);
        }
    }

    @Override public void close() { client.close(); }
}
