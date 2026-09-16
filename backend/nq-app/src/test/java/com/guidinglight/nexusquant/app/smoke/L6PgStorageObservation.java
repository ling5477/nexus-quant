package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** 整个 owned PG tmpfs 与数据库分项分开读取；数据库大小不能替代 WAL/文件系统占用。 */
final class L6PgStorageObservation {
    static final String UNAVAILABLE = "L6_PG_STORAGE_MEASUREMENT_UNAVAILABLE";
    static final String DATA = "/var/lib/postgresql/data";
    static final Set<String> FIELDS = Set.of("pgTmpfsCapacityBytes", "pgTmpfsUsedBytes", "pgTmpfsFreeBytes",
            "pgTmpfsUsageRatio", "pgDatabaseSizeBytes", "pgDataAllocatedBytes", "pgWalAllocatedBytes",
            "pgBaseAllocatedBytes", "pgGlobalAllocatedBytes", "pgOtherAllocatedBytes", "pgRelationStorage");
    private static final ObjectMapper JSON = new ObjectMapper();

    @FunctionalInterface interface Command { String run(String... args) throws Exception; }

    static ObjectNode collect(Connection reader, String ownedContainer, Command command) throws Exception {
        return collect(reader,ownedContainer,command,()->2);
    }

    /** B只读采集共享诊断截止；其他入口仍使用原查询上限。 */
    static ObjectNode collect(Connection reader, String ownedContainer, Command command, java.util.function.IntSupplier timeout) throws Exception {
        if (ownedContainer == null || !ownedContainer.matches("[a-f0-9]{64}")) throw unavailable();
        ObjectNode value = parseStat(command.run("docker", "exec", ownedContainer,
                "stat", "-f", "-c", "%T %S %b %f %a", DATA));
        // 三个分项与总目录都使用 allocated bytes；这组逐项读取不冒充原子文件系统快照。
        value.setAll(parseAllocated(command.run("docker", "exec", ownedContainer, "du", "--count-links",
                "--summarize", "--block-size=1", "--", DATA + "/base", DATA + "/pg_wal", DATA + "/global", DATA)));
        try (var statement = reader.createStatement()) {
            statement.setQueryTimeout(timeout.getAsInt());
            try (var result = statement.executeQuery("SELECT pg_database_size(current_database())")) {
                if (!result.next()) throw unavailable();
                long bytes = result.getLong(1);
                if (result.wasNull() || bytes < 0 || result.next()) throw unavailable();
                value.put("pgDatabaseSizeBytes", bytes);
            }
            statement.setQueryTimeout(timeout.getAsInt());
            // 一次有界目录查询保留所有用户关系；TOAST 已包含在 table bytes 中，不重复相加。
            try (var result = statement.executeQuery("""
                    SELECT n.nspname, c.relname, pg_table_size(c.oid), pg_indexes_size(c.oid),
                           pg_total_relation_size(c.oid)
                    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE c.relkind IN ('r','m') AND n.nspname NOT IN ('pg_catalog','information_schema')
                      AND n.nspname NOT LIKE 'pg_toast%' AND n.nspname NOT LIKE 'pg_temp_%'
                    ORDER BY 5 DESC, 1, 2 LIMIT 513
                    """)) {
                var relations = value.putArray("pgRelationStorage");
                while (result.next()) {
                    if (relations.size() == 512) throw unavailable();
                    var row = relations.addObject().put("schema", result.getString(1)).put("relation", result.getString(2));
                    for (int i = 3; i <= 5; i++) {
                        long bytes = result.getLong(i);
                        if (result.wasNull() || bytes < 0) throw unavailable();
                        row.put(List.of("tableBytes", "indexBytes", "totalBytes").get(i - 3), bytes);
                    }
                }
            }
        }
        return value;
    }

    static ObjectNode parseStat(String output) {
        try {
            String[] tokens = output.strip().split("\\s+");
            if (tokens.length != 5 || !tokens[0].equals("tmpfs")) throw unavailable();
            long block = positive(tokens[1]), total = positive(tokens[2]);
            long free = nonnegative(tokens[3]), available = nonnegative(tokens[4]);
            // tmpfs 不应存在保留块差额；拒绝异常输入，不能用 available=0 伪装未知值。
            if (free > total || available != free) throw unavailable();
            long capacity = Math.multiplyExact(block, total), remaining = Math.multiplyExact(block, free);
            return JSON.createObjectNode().put("pgTmpfsCapacityBytes", capacity)
                    .put("pgTmpfsUsedBytes", capacity - remaining).put("pgTmpfsFreeBytes", remaining)
                    .put("pgTmpfsUsageRatio", (double) (capacity - remaining) / capacity);
        } catch (RuntimeException error) { throw unavailable(); }
    }

    static ObjectNode parseAllocated(String output) {
        try {
            var sizes = new HashMap<String, Long>();
            for (String line : output.strip().split("\\R")) {
                String[] tokens = line.strip().split("\\s+");
                if (tokens.length != 2 || sizes.put(tokens[1], nonnegative(tokens[0])) != null) throw unavailable();
            }
            if (!sizes.keySet().equals(Set.of(DATA, DATA + "/base", DATA + "/pg_wal", DATA + "/global"))) throw unavailable();
            long base = sizes.get(DATA + "/base"), wal = sizes.get(DATA + "/pg_wal"), global = sizes.get(DATA + "/global");
            long other = Math.subtractExact(sizes.get(DATA), Math.addExact(base, Math.addExact(wal, global)));
            if (other < 0) throw unavailable();
            return JSON.createObjectNode().put("pgBaseAllocatedBytes", base).put("pgWalAllocatedBytes", wal)
                    .put("pgGlobalAllocatedBytes", global).put("pgDataAllocatedBytes", sizes.get(DATA))
                    .put("pgOtherAllocatedBytes", other);
        } catch (RuntimeException error) { throw unavailable(); }
    }

    private static long nonnegative(String token) {
        if (!token.matches("[0-9]+")) throw unavailable();
        return Long.parseLong(token);
    }
    private static long positive(String token) { long value = nonnegative(token); if (value == 0) throw unavailable(); return value; }
    private static IllegalStateException unavailable() { return new IllegalStateException(UNAVAILABLE); }
}
