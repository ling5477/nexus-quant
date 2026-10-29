package com.guidinglight.nexusquant.research.application.paper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;
import com.guidinglight.nexusquant.research.domain.paper.port.EmergencyStopEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 原事件主键就是请求身份；停机和原事件原子提交，不建设第二套执行或回执事实源。 */
@Service
public class PaperEmergencyStopOperationService {
    private final EmergencyStopEventRepository events;
    private final PaperTradingMonitorService monitor;
    private final PaperTradingRunService runs;
    private final ObjectMapper mapper;

    public PaperEmergencyStopOperationService(
            EmergencyStopEventRepository events,
            PaperTradingMonitorService monitor,
            PaperTradingRunService runs,
            ObjectMapper mapper) {
        this.events = events;
        this.monitor = monitor;
        this.runs = runs;
        this.mapper = mapper;
    }

    /** 未读到原事件只代表尚未确认，不能据此推断原请求没有执行。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public Optional<EmergencyStopEvent> find(UUID id, long owner, String runId) {
        validateScope(id, owner, runId);
        return events.findById(eventId(id)).filter(e -> belongs(e, owner, runId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public EmergencyStopEvent submit(
            UUID id, long owner, String actor, String runId, String triggerType, String reason) {
        validateScope(id, owner, runId);
        if (actor == null
                || actor.isBlank()
                || actor.length() > 128
                || triggerType == null
                || !List.of("MANUAL", "RISK_LIMIT", "SYSTEM_ERROR").contains(triggerType)
                || reason == null
                || reason.isBlank()
                || reason.length() > 512)
            throw new IllegalArgumentException("INVALID_PAPER_EMERGENCY_INPUT");
        String sha = fingerprint(owner, runId, triggerType, reason);
        events.lockOperation(id);
        var existing = events.findById(eventId(id));
        if (existing.isPresent()) {
            var original = existing.get();
            if (!belongs(original, owner, runId) || !sha.equals(inputSha(original)))
                throw new IllegalStateException("PAPER_EMERGENCY_OPERATION_ID_CONFLICT");
            return original;
        }
        var run = runs.getById(runId);
        // 历史元数据停机不得替代canonical SIM的资金及持续运行停止路径。
        if (run.canonicalAccountId() != null)
            throw new IllegalArgumentException("PAPER_EMERGENCY_REQUIRES_LEGACY_RUN");
        String input =
                json(
                        Map.of(
                                "operationOwnerUserId",
                                owner,
                                "inputSha256",
                                sha,
                                "triggerType",
                                triggerType,
                                "reason",
                                reason));
        monitor.triggerEmergencyStop(runId, triggerType, reason, actor, eventId(id), input);
        // 读取数据库精度的原结果，首次响应与刷新/重启后的结果保持相同。
        return events.findById(eventId(id)).orElseThrow();
    }

    private boolean belongs(EmergencyStopEvent event, long owner, String runId) {
        if (!event.paperRunId().equals(runId)) return false;
        try {
            return mapper.readTree(event.requestJson()).path("operationOwnerUserId").asLong(-1)
                    == owner;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            // 没有稳定身份的旧事件不补猜归属，也不把它冒充新请求的原回执。
            return false;
        }
    }

    private String inputSha(EmergencyStopEvent event) {
        try {
            return mapper.readTree(event.requestJson()).path("inputSha256").asText();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("PAPER_EMERGENCY_ORIGINAL_RESULT_INVALID", ex);
        }
    }

    private String fingerprint(long owner, String runId, String triggerType, String reason) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            json(List.of(owner, runId, triggerType, reason))
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", ex);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("PAPER_EMERGENCY_ENCODING_FAILED", ex);
        }
    }

    private static void validateScope(UUID id, long owner, String runId) {
        if (id == null || owner <= 0 || runId == null || runId.isBlank() || runId.length() > 128)
            throw new IllegalArgumentException("INVALID_PAPER_EMERGENCY_SCOPE");
    }

    private static String eventId(UUID id) {
        return "es-" + id;
    }
}
