package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 仅观测本轮实际执行对象及只读数据库；缺失必须失败，不填默认零。 */
final class L6Measurements {
    private static final ObjectMapper JSON = new ObjectMapper();

    static void requireMandatory(JsonNode sample) {
        var queue = sample.path("commandQueue");
        for (String field : List.of("queueSize", "activeCount", "completedCount", "queueCapacity")) {
            requireNonnegative(queue.path(field));
        }
        if (!queue.path("queuePresent").asBoolean()
                || !(queue.path("queueSize").asLong() == 0 ? "MEASURED_ZERO" : "MEASURED")
                .equals(queue.path("measurementStatus").asText())) unavailable();
        for (String field : List.of("acquisitionTimeoutCount", "counterStart", "counterEnd", "acquisitionTimeoutDelta")) {
            requireNonnegative(sample.path(field));
        }
        if (!"hikaricp.connections.timeout".equals(sample.path("acquisitionTimeoutMetric").asText())) unavailable();
        var age = sample.path("candidateAge");
        requireNonnegative(age.path("scanEligibleCandidateCount"));
        requireNonnegative(age.path("eligibleCandidateCount"));
        if (!age.path("sampledAt").isTextual()) unavailable();
        if (age.path("eligibleCandidateCount").asLong() == 0) {
            if (!age.path("oldestCandidateAgeMillis").isNull()
                    || !"NONE".equals(age.path("oldestAgeStatus").asText())) unavailable();
        } else {
            requireNonnegative(age.path("oldestCandidateAgeMillis"));
            if (!"MEASURED".equals(age.path("oldestAgeStatus").asText())) unavailable();
        }
    }

    private static void requireNonnegative(JsonNode value) {
        if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0) unavailable();
    }

    private static void unavailable() {
        throw new IllegalStateException("L6_MANDATORY_MEASUREMENT_UNAVAILABLE");
    }

    static ThreadPoolExecutor commands() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());
    }

    static ObjectNode queue(ThreadPoolExecutor executor) {
        if (executor == null) throw new IllegalStateException("COMMAND_QUEUE_UNAVAILABLE");
        int size = executor.getQueue().size();
        return JSON.createObjectNode().put("queuePresent", true).put("queueSize", size)
                .put("measurementStatus", size == 0 ? "MEASURED_ZERO" : "MEASURED")
                .put("activeCount", executor.getActiveCount()).put("completedCount", executor.getCompletedTaskCount())
                .put("queueCapacity", size + executor.getQueue().remainingCapacity());
    }

    static Counter timeoutCounter(HikariDataSource pool, MeterRegistry registry) {
        // 名称和pool标签来自实际注册的Hikari meter；未绑定时禁止返回零值替代。
        var counters = registry.getMeters().stream().filter(Counter.class::isInstance).map(Counter.class::cast)
                .filter(c -> c.getId().getName().equals("hikaricp.connections.timeout")
                        && pool.getPoolName().equals(c.getId().getTag("pool"))).toList();
        if (counters.size() != 1) throw new IllegalStateException("HIKARI_TIMEOUT_COUNTER_UNAVAILABLE");
        return counters.getFirst();
    }

    static ObjectNode age(NamedParameterJdbcTemplate jdbc, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) throw new IllegalStateException("ELIGIBILITY_NOT_OBSERVED");
        // 扫描状态来自真实reconcile调用参数；待收敛谓词复用既有L5/L6账务backlog owner。
        String backlog = (String) ReflectionTestUtils.getField(L5BoundedWorkloadTest.class, "BACKLOG");
        if (backlog == null || !backlog.startsWith("SELECT count(*) FROM orders o WHERE ")) {
            throw new IllegalStateException("BACKLOG_CONTRACT_UNAVAILABLE");
        }
        String pending = backlog.substring("SELECT count(*) FROM orders o WHERE ".length());
        return jdbc.queryForObject("""
                WITH clock AS (SELECT statement_timestamp() AS sampled_at),
                candidates AS (SELECT o.* FROM orders o WHERE o.venue=:venue AND o.status IN (:statuses)),
                pending AS (SELECT o.* FROM candidates o WHERE (
                """ + pending + """
                )) SELECT (SELECT count(*) FROM candidates) AS scan_count, count(p.order_id) AS eligible_count,
                min(p.created_at) AS oldest, c.sampled_at,
                CASE WHEN count(p.order_id)=0 THEN NULL ELSE
                    greatest(0,extract(epoch FROM(c.sampled_at-min(p.created_at)))*1000)::bigint END AS age
                FROM clock c LEFT JOIN pending p ON true GROUP BY c.sampled_at
                """,
                new MapSqlParameterSource("venue", "OKX").addValue("statuses", statuses), (rs, row) -> {
                    ObjectNode n = JSON.createObjectNode().put("sampledAt", rs.getTimestamp("sampled_at").toInstant().toString())
                            .put("scanEligibleCandidateCount", rs.getLong("scan_count"))
                            .put("eligibleCandidateCount", rs.getLong("eligible_count"));
                    Long age = rs.getObject("age", Long.class);
                    if (age == null) n.putNull("oldestCandidateAgeMillis"); else n.put("oldestCandidateAgeMillis", age);
                    return n.put("oldestAgeStatus", age == null ? "NONE" : "MEASURED");
                });
    }
}
