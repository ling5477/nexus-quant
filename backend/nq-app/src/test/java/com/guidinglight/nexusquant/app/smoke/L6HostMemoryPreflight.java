package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** 用最大预算而非RSS执行60% gate；失败时尚未创建PG、Venue、NQ或formal timer。 */
final class L6HostMemoryPreflight {
    static final String EXCEEDED = "BLOCKED / L6_HOST_MEMORY_SAFETY_BUDGET_EXCEEDED";
    private static final ObjectMapper JSON = new ObjectMapper();
    record Entry(long availableBytes, long controllerHeapBytes, long mavenHeapBytes) { }

    static Entry observe() throws Exception {
        long available;
        if (System.getProperty("os.name").startsWith("Windows")) {
            available = Math.multiplyExact(Long.parseLong(B0Processes.command("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory")), 1024);
        } else {
            var matcher = Pattern.compile("(?m)^MemAvailable:\\s+(\\d+) kB$").matcher(Files.readString(Path.of("/proc/meminfo")));
            if (!matcher.find()) throw new IllegalStateException("L6_AVAILABLE_HOST_MEMORY_UNAVAILABLE");
            available = Math.multiplyExact(Long.parseLong(matcher.group(1)), 1024);
        }
        ProcessHandle parent = ProcessHandle.current(); Long heap = null;
        for (int i = 0; i < 6 && parent.parent().isPresent(); i++) {
            parent = parent.parent().orElseThrow();
            String name = Path.of(parent.info().command().orElse("unknown")).getFileName().toString();
            if (name.equals("java") || name.equals("java.exe")) {
                String jcmd = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "jcmd.exe" : "jcmd").toString();
                var match = Pattern.compile("-XX:MaxHeapSize=(\\d+)").matcher(B0Processes.command(jcmd, Long.toString(parent.pid()), "VM.flags"));
                if (!match.find()) throw new IllegalStateException("L6_MAVEN_HEAP_BUDGET_UNAVAILABLE");
                heap = Long.parseLong(match.group(1)); break;
            }
        }
        if (heap == null) throw new IllegalStateException("L6_MAVEN_HEAP_BUDGET_UNAVAILABLE");
        return new Entry(available, Runtime.getRuntime().maxMemory(), heap);
    }

    static long budget(L6PgCapacityContract c, long capacity) {
        L6PgCapacityContract.require(capacity >= B0Processes.Pg.defaultTmpfsBytes());
        long result = Math.addExact(2*c.memory("nqHeapEachBytes"), c.memory("venueHeapBytes"));
        for (String key : new String[]{"controllerHeapBytes", "mavenHeapBytes", "nativeAndToolsBudgetBytes"}) result = Math.addExact(result, c.memory(key));
        return Math.addExact(result, c.pgMemory(capacity));
    }

    static ObjectNode verify(L6PgCapacityContract c, long capacity, Entry entry) {
        long budget = budget(c, capacity);
        var proof = JSON.createObjectNode().put("availableHostMemoryAtEntry", entry.availableBytes())
                .put("totalQualificationOwnedBudget", budget).put("maximumFraction", "0.60")
                .put("pgContainerBudgetBytes", c.pgMemory(capacity)).put("pgTmpfsMaximumBytes", capacity)
                .put("tmpfsAlreadyIncludedInPgContainerBudget", true)
                .put("controllerActualMaxHeapBytes", entry.controllerHeapBytes()).put("mavenActualMaxHeapBytes", entry.mavenHeapBytes())
                .put("pgStarted", false).put("venueStarted", false).put("nqStarted", false)
                .put("formalTimerStarted", false).put("orders", 0)
                .put("host60PercentLimitBytes", BigInteger.valueOf(entry.availableBytes()).multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(5)).longValueExact());
        if (entry.availableBytes() <= 0 || entry.controllerHeapBytes() <= 0 || entry.mavenHeapBytes() <= 0
                || entry.controllerHeapBytes() > c.memory("controllerHeapBytes") || entry.mavenHeapBytes() > c.memory("mavenHeapBytes")) {
            throw new IllegalStateException("BLOCKED / L6_HOST_MEMORY_RUNTIME_BUDGET_UNBOUNDED");
        }
        if (BigInteger.valueOf(budget).multiply(BigInteger.valueOf(5)).compareTo(BigInteger.valueOf(entry.availableBytes()).multiply(BigInteger.valueOf(3))) > 0) {
            throw new IllegalStateException(EXCEEDED);
        }
        return proof.put("status", "PASS");
    }

    @FunctionalInterface interface Ready { void run(ObjectNode proof) throws Exception; }
    static void beforeRuntime(L6PgCapacityContract c, long capacity, Entry entry, Ready ready) throws Exception { ready.run(verify(c, capacity, entry)); }
}
