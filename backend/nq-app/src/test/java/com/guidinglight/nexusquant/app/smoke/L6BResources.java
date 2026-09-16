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

/** 所有collector只读取本轮持有的连接、PID、容器和目录；不扫描其他任务资源。 */
final class L6BResources implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final Set<String> ACTOR_FIELDS = Set.of("jvmUptimeMillis", "heapUsed", "heapCommitted", "heapMax", "gc", "threads", "peakThreads",
            "commandQueue", "metricsExecutor", "active", "idle", "pending", "poolMax", "acquisitionTimeoutCount",
            "acquisitionTimeoutDelta", "tickStarted", "tickCompleted", "tickFailed", "observations", "candidateAge", "transactionsByOriginAndOwner");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofNanos(L6BSlotSampler.INTERVAL)).build();
    private final Connection reader;
    private final Path directory;
    private final L6BActors actors;
    private final B0Processes.Child venue;
    private final L6BContract contract;
    private final long ownedMemoryBudget, entryAvailableMemory;
    private final Map<Long, String> generations = new LinkedHashMap<>();
    private final String container;
    private final L6ResourceFileLifecycle fileLifecycle;
    private long previousLogBytes;
    private final Map<String, L6ResourceSampler.Collector> collectors = new LinkedHashMap<>();
    private final Map<String, Set<String>> required = new LinkedHashMap<>();

    L6BResources(Connection reader, Path directory, L6BActors actors,
                 B0Processes.Child venue, String endpoint, String container, L6BContract contract,long capacity,long entryAvailableMemory) throws Exception {
        boolean formalStorage=true, storageCalibration=false;
        L6ResourceFileLifecycle fileLifecycle=new L6ResourceFileLifecycle();
        this.actors=actors; this.venue=venue; this.contract=contract;
        this.ownedMemoryBudget=L6HostMemoryPreflight.budget(contract.base,capacity);
        this.entryAvailableMemory=entryAvailableMemory;
        verifyMemory(ownedMemoryBudget,entryAvailableMemory,entryAvailableMemory);
        this.reader = reader;
        this.fileLifecycle = fileLifecycle;
        this.directory = directory.toAbsolutePath().normalize();
        this.container = container;
        B0Fixture.require(container.matches("[a-f0-9]{64}"));
        B0Fixture.requireVenue(endpoint);
        previousLogBytes = logBytes();
        try (var statement = reader.createStatement()) { statement.execute("SET statement_timeout='"+(L6BSlotSampler.INTERVAL/1_000_000)+"ms'"); }
        for (int i=0;i<2;i++) {
            final int actorIndex=i;
            final long[] sequence={0}, priorPid={-1};
            var fields=Set.of("logicalActor","generation","pid","lifecycle","processAlive","startTimestamp");
            add("nq"+i, fields, stamp -> {
                var generation=actors.get(actorIndex);
                if(generation==null)throw new IllegalStateException("L6_B_ACTOR_IDENTITY_MISSING");
                ObjectNode value=generation.evidence();
                if ("RUNNING".equals(generation.state)) {
                    actors.assertOwned(generation,generation.child.process.pid());
                    String metrics=Files.readString(directory.resolve("l6-metrics-"+generation.child.process.pid()+".endpoint"));
                    B0Fixture.requireVenue(metrics);
                    ObjectNode measured=get(metrics+"/metrics",stamp.token());
                    if(priorPid[0]!=generation.child.process.pid()) { priorPid[0]=generation.child.process.pid();sequence[0]=0; }
                    if(!stamp.token().equals(measured.path("sampleToken").asText())
                            || measured.path("pid").asLong()!=priorPid[0] || measured.path("observationSequence").asLong()<=sequence[0])
                        throw new IllegalStateException("L6_STALE_ACTOR_OBSERVATION");
                    sequence[0]=measured.path("observationSequence").asLong();
                    L6Measurements.requireMandatory(measured);
                    for(String field:List.of("heapUsed","heapCommitted","heapMax","threads","peakThreads","active","idle","pending","poolMax","tickStarted","tickCompleted","tickFailed")) numeric(measured,field);
                    if(!measured.path("gc").isArray() || measured.path("gc").isEmpty())throw new IllegalStateException("GC_UNAVAILABLE");
                    for(var gc:measured.path("gc")){numeric(gc,"count");numeric(gc,"timeMillis");}
                    if(measured.path("tickFailed").asLong()!=0)throw new IllegalStateException("L6_RECOVERY_TICK_FAILED");
                    value.setAll(measured);value.setAll(generation.evidence());value.put("measurementScope","JVM_AND_LIFECYCLE");
                } else {
                    // 已登记重启窗口只报告实测生命周期；缺失的JVM指标不填零、不计入GC稳定窗口。
                    value.put("jvmMeasurementState","PLANNED_PROCESS_TRANSITION");
                    value.put("measurementScope","LIFECYCLE_ONLY");
                }
                return measured(stamp,value,fields);
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
        var postgresFields = new java.util.HashSet<>(Set.of("appConnections", "databaseConnections", "idleInTransaction", "backlog", "actionable",
                "auditRows", "eventRows", "transactions", "cursor", "orders"));
        // 仅正式入口增加存储必测项；L5、readiness 与已接受 calibration 合同保持原样。
        if (formalStorage) postgresFields.addAll(L6PgStorageObservation.FIELDS);
        if (storageCalibration) postgresFields.addAll(Set.of("terminalOrders", "fills", "trades", "TradeExecuted", "ledgerEntries", "fullChainCompleted", "fullChainOrderIds", "databaseBytes", "walBytes"));
        add("postgres", Set.copyOf(postgresFields), stamp -> {
            if (formalStorage) {
                reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                reader.setAutoCommit(false);
            }
            try {
            ObjectNode value = L5BoundedWorkloadTest.sample(reader,L6BResources::number);
            value.put("databaseConnections", number(reader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()"));
            value.put("idleInTransaction", number(reader, "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state LIKE 'idle in transaction%'"));
            value.put("auditRows", number(reader, "SELECT count(*) FROM audit_logs"));
            value.put("eventRows", number(reader, "SELECT count(*) FROM event_store"));
            value.set("cursor", JSON.readTree(value(reader,"SELECT coalesce(jsonb_agg(to_jsonb(c))::text,'[]') FROM reconciliation_scan_cursors c WHERE venue='OKX'")));
            value.set("controllerTransactionsByOriginAndOwner", L6TransactionAccounting.snapshot());
            if (formalStorage) {
                value.setAll(L6PgStorageObservation.collect(reader, container, this::command, ()->querySeconds(reader)));
                value.put("maximumFillsPerOrder", number(reader, "SELECT coalesce(max(fills),0) FROM (SELECT count(*) fills FROM trades GROUP BY order_id) t"));
            }
            if ((!formalStorage || storageCalibration) && value.path("transactions").asLong() > 1_000_000) throw new IllegalStateException("L6_TRANSACTION_BUDGET");
            return measured(stamp, value, required.get("postgres"));
            } finally { if (formalStorage) { reader.rollback(); reader.setAutoCommit(true); } }
        });
        add("os", Set.of("handles", "fd", "processes","availableHostMemoryBytes","host60PercentLimitBytes","ownedMemoryBudgetBytes"), this::os);
        add("files", Set.of("logBytes", "logDeltaBytes", "ownedTempFileCount", "ownedTempBytes"), this::files);
        add("ownership", Set.of("ownedProcessCount", "ownedContainerCount", "pidGenerations"), stamp -> {
            actors.requireHealthy();
            var processes=processes();
            generations.clear();
            for(var process:processes)generations.put(process.pid(),process.info().startInstant().orElseThrow().toString());
            String state = command("docker", "inspect", "--format", "{{.Id}} {{.State.Running}}", container).trim();
            if (!state.equals(container + " true")) throw new IllegalStateException("L6_OWNED_CONTAINER_LOST");
            ObjectNode value = JSON.createObjectNode().put("ownedProcessCount", processes.size()).put("ownedContainerCount", 1);
            value.set("pidGenerations", JSON.valueToTree(generations));
            return measured(stamp, value, required.get("ownership"));
        });
    }

    L6BSlotSampler sampler(L6SamplingSchedule schedule, long startedNanos, Path output) {
        return new L6BSlotSampler(collectors(), required(), System::nanoTime, java.time.Instant::now,
                startedNanos, schedule, output, actors);
    }

    private List<ProcessHandle> processes() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(ProcessHandle.current(),venue.process.toHandle()),
                actors.liveProcesses().stream()).toList();
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
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(L6BCollectionBudget.remaining())
                .header("X-L6-Sample", token).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("L6_RESOURCE_HTTP_UNAVAILABLE: " + response.body());
        return (ObjectNode) JSON.readTree(response.body());
    }

    private L6ResourceSampler.Observation os(L6ResourceSampler.Stamp stamp) throws Exception {
        var processes=processes();
        generations.clear();
        for(var process:processes)generations.put(process.pid(),process.info().startInstant().orElseThrow().toString());
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
        long available;
        if(System.getProperty("os.name").startsWith("Windows")) {
            available=Long.parseLong(command("powershell","-NoProfile","-NonInteractive","-Command",
                    "(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory").trim())*1024;
        } else {
            var match=java.util.regex.Pattern.compile("(?m)^MemAvailable:\\s+(\\d+) kB$")
                    .matcher(Files.readString(Path.of("/proc/meminfo")));
            if(!match.find())throw new IllegalStateException("L6_B_HOST_MEMORY_UNAVAILABLE");
            available=Long.parseLong(match.group(1))*1024;
        }
        long ceiling=entryAvailableMemory*3/5;
        verifyMemory(ownedMemoryBudget,entryAvailableMemory,available);
        value.put("availableHostMemoryBytes",available).put("host60PercentLimitBytes",ceiling).put("ownedMemoryBudgetBytes",ownedMemoryBudget)
                .put("frozenEntryAvailableMemoryBytes",entryAvailableMemory).put("availableMemoryReserveFloorBytes",entryAvailableMemory-ceiling);
        for(String key:List.of("availableHostMemoryBytes","host60PercentLimitBytes","ownedMemoryBudgetBytes"))states.put(key,"MEASURED");
        return new L6ResourceSampler.Observation(stamp, value, states);
    }

    static void verifyMemory(long budget,long entryAvailable,long currentAvailable) {
        // 入口60%额度保持冻结；正常分配已反映在当前free中，不能再次扣完整预算。
        long ceiling=entryAvailable*3/5;
        if(entryAvailable<=0 || budget<=0 || budget>ceiling || currentAvailable<entryAvailable-ceiling)
            throw new IllegalStateException("L6_B_HOST_MEMORY_RESERVE_EXCEEDED");
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
            long fileBytes;
            while ((fileBytes = fileLifecycle.nextFileBytes(iterator)) != L6ResourceFileLifecycle.END_OF_FILES) {
                if (fileBytes >= 0) {
                    if (++count > 10_000) throw new IllegalStateException("L6_FILE_COUNT_SAFETY_LIMIT");
                    bytes += fileBytes;
                }
            }
        }
        if (bytes >= contract.rawCap || Files.getFileStore(directory).getUsableSpace() < 2L * 1024 * 1024 * 1024) {
            throw new IllegalStateException("L6_DISK_SAFETY_LIMIT");
        }
        if (logs < previousLogBytes) throw new IllegalStateException("L6_UNEXPLAINED_LOG_TRUNCATION");
        ObjectNode value = JSON.createObjectNode().put("logBytes", logs).put("logDeltaBytes", logs - previousLogBytes)
                .put("freeDiskBytes", Files.getFileStore(directory).getUsableSpace()).put("ownedTempFileCount", count).put("ownedTempBytes", bytes).put("ownedPath", directory.toString());
        previousLogBytes = logs;
        return measured(stamp, value, required.get("files"));
    }

    private String command(String... arguments) throws Exception {
        // 采集操作共享本次槽长的诊断预算；超时后只回收本次创建的Process。
        Path log = Files.createTempFile(directory, "resource-command-", ".tmp");
        Process process = null;
        try {
            var builder = new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().clear(); builder.environment().putAll(B0Processes.cleanEnvironment());
            process = builder.start();
            if (!process.waitFor(L6BCollectionBudget.remaining().toNanos(), java.util.concurrent.TimeUnit.NANOSECONDS)) throw new IllegalStateException("L6_RESOURCE_COMMAND_TIMEOUT");
            if (process.exitValue() != 0) throw new IllegalStateException("L6_RESOURCE_COMMAND_FAILED: " + Files.readString(log));
            return Files.readString(log);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("L6_RESOURCE_COMMAND_SURVIVOR");
            }
            fileLifecycle.deleteTemporary(log);
        }
    }

    private static int querySeconds(Connection reader) {
        try(var timeout=reader.createStatement()) {
            timeout.execute("SET LOCAL statement_timeout='"+Math.max(1,L6BCollectionBudget.remaining().toMillis())+"ms'");
            return L6BCollectionBudget.querySeconds();
        } catch(Exception error) { throw new IllegalStateException("L6_RESOURCE_SQL_DEADLINE",error); }
    }
    private static long number(Connection reader,String sql) throws Exception {
        return Long.parseLong(value(reader,sql));
    }
    private static String value(Connection reader,String sql) throws Exception {
        try(var statement=reader.createStatement()) {
            statement.setQueryTimeout(querySeconds(reader));
            try(var rows=statement.executeQuery(sql)) {
                if(!rows.next())throw new IllegalStateException("L6_QUERY_EMPTY");
                String value=rows.getString(1);
                if(value==null || rows.next())throw new IllegalStateException("L6_QUERY_SHAPE");
                return value;
            }
        }
    }

    private static void numeric(JsonNode value, String key) {
        if (!value.path(key).isNumber() || value.path(key).asDouble() < 0 || !Double.isFinite(value.path(key).asDouble())) {
            throw new IllegalStateException("L6_MANDATORY_MEASUREMENT_UNAVAILABLE: " + key);
        }
    }

    @Override public void close() { client.close(); }
}
