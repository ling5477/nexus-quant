package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/** 观测时间是持久事实的保守上界；窗口外、暂停内和warmup身份不得计入稳态分子。 */
final class L6CalibrationEvidence {
    record Pause(long start, long end, String reason) { }
    private final Map<String, Long> admitted = new LinkedHashMap<>();
    private final Map<String, Long> completed = new LinkedHashMap<>();
    private final List<Pause> pauses = new ArrayList<>();
    private long lastBacklog;
    private int rising;

    void admit(List<String> ids, long elapsed) {
        if (elapsed < 0 || ids.size() > 600) throw new IllegalArgumentException("admission bounds");
        ids.forEach(id -> admitted.putIfAbsent(id, elapsed));
        if (admitted.size() > 600) throw new IllegalStateException("CALIBRATION_ORDER_BUDGET");
    }

    void complete(List<String> ids, long elapsed) {
        for (var id : ids) {
            if (!admitted.containsKey(id) || elapsed < admitted.get(id)) throw new IllegalStateException("completion lineage");
            completed.putIfAbsent(id, elapsed);
        }
    }

    void pause(long start, long end, String reason) {
        if (start < 0 || end < start
                || !List.of("BACKPRESSURE", "BOUNDARY").contains(reason) || pauses.size() >= 2000) {
            throw new IllegalArgumentException("pause interval");
        }
        pauses.add(new Pause(start, end, reason));
    }

    void backlog(long value) {
        if (value < 0 || value > 600) throw new IllegalStateException("CALIBRATION_BACKLOG_BOUND");
        rising = value > lastBacklog ? rising + 1 : 0;
        lastBacklog = value;
        if (rising >= 3) throw new IllegalStateException("CALIBRATION_BACKLOG_RUNAWAY");
    }

    private static long overlap(long start, long end, List<Pause> ranges) {
        long result = 0, cursor = start;
        for (var p : ranges.stream().sorted(java.util.Comparator.comparingLong(Pause::start)).toList()) {
            long left = Math.max(cursor, p.start()), right = Math.min(end, p.end());
            if (right > left) { result += right - left; cursor = right; }
        }
        return result;
    }

    ObjectNode export(L6CalibrationContract contract, long elapsed, long missing, long cadenceViolations) {
        long start = TimeUnit.NANOSECONDS.toMillis(contract.warmup());
        long end = TimeUnit.NANOSECONDS.toMillis(contract.total());
        long paused = overlap(start, end, pauses);
        long pressure = overlap(start, end, pauses.stream().filter(p -> p.reason().equals("BACKPRESSURE")).toList());
        long eligible = end - start - paused;
        long chains = completed.entrySet().stream().filter(e -> admitted.get(e.getKey()) >= start
                && e.getValue() >= start && e.getValue() < end
                && pauses.stream().noneMatch(p -> e.getValue() >= p.start() && e.getValue() < p.end())).count();
        boolean valid = elapsed >= end && missing == 0 && cadenceViolations == 0 && chains > 0
                && eligible > 0 && paused * 2 < end - start && rising < 3;
        var out = new ObjectMapper().createObjectNode().put("mode", "CALIBRATION")
                .put("smoke", contract.smoke()).put("producerWindowStartElapsedMillis", start)
                .put("producerWindowEndElapsedMillis", end).put("producerActiveSeconds", eligible / 1000.0)
                .put("producerPausedSeconds", paused / 1000.0).put("backpressureSeconds", pressure / 1000.0)
                .put("ordersAdmittedTotal", admitted.size()).put("fullChainCompletionsTotal", completed.size())
                .put("eligibleFullChainCompletions", chains).put("rawRateEvidenceValid", valid)
                .put("formalCalibrationAccepted", false).put("l6Accepted", false)
                .put("formula", "eligible observed full-chain completions / eligible measurement seconds")
                .put("units", "orders/second").put("candidateStatus", valid ? "PENDING_CALIBRATION_TASK" : "UNAVAILABLE");
        if (valid && !contract.smoke()) out.put("healthyRateCandidate", chains / (eligible / 1000.0));
        else out.putNull("healthyRateCandidate");
        out.putNull("l6FinalArrivalRate");
        out.set("admittedObservedElapsedMillis", new ObjectMapper().valueToTree(admitted));
        out.set("completedObservedElapsedMillis", new ObjectMapper().valueToTree(completed));
        out.set("pauseIntervals", new ObjectMapper().valueToTree(pauses));
        return out;
    }
}
