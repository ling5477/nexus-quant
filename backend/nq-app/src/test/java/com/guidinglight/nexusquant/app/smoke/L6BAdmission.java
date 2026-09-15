package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import org.postgresql.util.PSQLException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** B的准入观察只等待数据库事实；明确回滚的busy拒绝消耗当前slot，不重发该请求。 */
final class L6BAdmission {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ACTIVE = "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY strategy_run_id)::text,'[]') FROM ("
            + "SELECT r.strategy_run_id,r.strategy_id,r.request_id,r.status,o.order_id,o.status AS order_status,"
            + "EXISTS(SELECT 1 FROM strategy_run_dispatch_work w WHERE w.strategy_run_id=r.strategy_run_id) AS work_present "
            + "FROM strategy_runs r LEFT JOIN orders o ON o.strategy_run_id=r.strategy_run_id "
            + "WHERE r.status IN ('CREATED','DISPATCHING','RUNNING') ORDER BY r.strategy_run_id LIMIT 3) t";
    @FunctionalInterface interface Recorder { void append(ObjectNode row) throws Exception; }
    private final Recorder recorder;
    private String previous;
    private long busySince = -1;
    private long busyBound;
    private boolean timedOut;
    private int observations, blockedObservations, records;

    L6BAdmission(Recorder recorder) { this.recorder = recorder; }

    static ArrayNode activeRuns(Connection reader) throws Exception {
        return checkedActive(JSON.readTree(L5BoundedWorkloadTest.value(reader, ACTIVE)));
    }

    private static ArrayNode checkedActive(JsonNode rows) {
        L6BContract.require(rows.isArray() && rows.size() <= 2);
        for (var row : rows) {
            L6BContract.require(!row.path("strategy_run_id").asText().isBlank()
                    && (row.path("strategy_id").asText().equals("l6-strategy-1")
                    || row.path("strategy_id").asText().equals("l6-strategy-2")));
        }
        return (ArrayNode) rows;
    }

    boolean observe(Connection reader, JsonNode sample, long elapsed) throws Exception {
        return observe(activeRuns(reader), sample.path("backlog").asLong(), sample.path("orders").asLong(), elapsed);
    }

    boolean observe(ArrayNode active, long backlog, long orders, long elapsed) throws Exception {
        if (timedOut) throw new IllegalStateException("RESTART_CONTINUITY_NOT_CONVERGED");
        checkedActive(active);
        L6BContract.require(backlog >= 0 && orders >= 0 && elapsed >= 0);
        observations++;
        boolean blocked = backlog > 0 || !active.isEmpty();
        if (blocked) {
            blockedObservations++;
            if (busySince < 0) { busySince = elapsed; busyBound = recoveryBoundNanos(orders); }
        } else busySince = -1;
        String identity = active.toString() + "/" + backlog;
        if (!identity.equals(previous)) {
            // 两个策略的状态变化有界保存，避免每次poll重复完整快照。
            L6BContract.require(++records <= 1434 * 8 + 16);
            var row = JSON.createObjectNode().put("elapsedNanos", elapsed).put("backlog", backlog)
                    .put("decision", blocked ? "BACKPRESSURE" : "ADMISSION_READY")
                    .put("reason", active.isEmpty() ? (blocked ? "ORDER_BACKLOG" : "DURABLE_CONVERGED") : "strategy_run_active")
                    .put("busySinceNanos", busySince).put("boundNanos", busyBound);
            row.set("activeRuns", active.deepCopy()); recorder.append(row); previous = identity;
        }
        if (blocked && elapsed - busySince >= busyBound) {
            timedOut = true;
            throw new IllegalStateException("RESTART_CONTINUITY_NOT_CONVERGED");
        }
        return blocked;
    }

    // 复用B既有两次fair-cursor绕行的恢复时间包络，不改订单、事务或计时预算。
    static long recoveryBoundNanos(long orders) {
        return Math.min(600, Math.max(20, 2 * Math.ceilDiv(orders, 100) * 10)) * L6BContract.SECOND;
    }

    ObjectNode summary() {
        return JSON.createObjectNode().put("observations", observations).put("blockedObservations", blockedObservations)
                .put("records", records).put("busySinceNanos", busySince).put("boundNanos", busyBound);
    }

    static String emit(ConfigurableApplicationContext context, String command) throws Exception {
        try { return L6FormalTrigger.emit(context, command); }
        catch (RuntimeException failure) {
            if (!isConfirmedBusy(failure)) throw failure;
            int slot = Integer.parseInt(command.split(" ")[1]);
            String request = String.format(java.util.Locale.ROOT, "l6p-%04d", slot);
            var jdbc = context.getBean(JdbcTemplate.class);
            // 只有canonical admission明确回滚且未创建该请求事实，才能返回无副作用backpressure。
            if (jdbc.queryForObject("SELECT count(*) FROM strategy_runs WHERE request_id=?", Long.class, request) != 0) throw failure;
            var result = JSON.createObjectNode().put("slotIndex", slot).put("outcome", "SKIPPED_BUSY")
                    .put("reason", "strategy_run_active").put("admissionRolledBack", true);
            result.set("activeRuns", checkedActive(JSON.readTree(jdbc.queryForObject(ACTIVE, String.class))));
            return result.toString();
        }
    }

    static boolean isConfirmedBusy(Throwable failure) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            if (failure instanceof PSQLException sql && "55000".equals(sql.getSQLState())
                    && sql.getServerErrorMessage() != null
                    && "strategy_run_active".equals(sql.getServerErrorMessage().getMessage())) return true;
        }
        return false;
    }
}
