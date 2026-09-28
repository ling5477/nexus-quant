package com.guidinglight.nexusquant.scheduler.control;

import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/** 运维入口只修改固定控制字段，执行权限仍由代码能力门槛决定。 */
@Service
@ConditionalOnProperty(name = "nq.runtime.provider-observation.enabled", havingValue = "false", matchIfMissing = true)
public class ScheduledJobManagementService {
    private final ScheduledJobRegistry registry;
    private final ScheduledJobControlRepository controls;
    private final SchedulerDispatcher dispatcher;
    private final AuditLogRepository audit;
    private final Clock clock = Clock.systemUTC();

    public ScheduledJobManagementService(ScheduledJobRegistry registry,
            ScheduledJobControlRepository controls, SchedulerDispatcher dispatcher,
            AuditLogRepository audit) {
        this.registry = registry;
        this.controls = controls;
        this.dispatcher = dispatcher;
        this.audit = audit;
    }

    public List<ScheduledJobControl> list() {
        return controls.all().stream().filter(row -> known(row.jobKey())).toList();
    }

    public ScheduledJobControl detail(String key) {
        registry.require(key);
        return controls.find(key).orElseThrow(() -> new IllegalStateException("SCHEDULER_CONTROL_MISSING"));
    }

    @Transactional
    public ScheduledJobControl patch(String key, Boolean enabled, Long fixedDelayMs,
            long expectedVersion, String actor, String traceId) {
        ScheduledJobRegistry.Job job = registry.require(key);
        ScheduledJobControl old = detail(key);
        boolean newEnabled = enabled == null ? old.enabled() : enabled;
        long newDelay = fixedDelayMs == null ? old.fixedDelayMs() : fixedDelayMs;
        job.validateDelay(newDelay);
        if (newEnabled && !job.eligible().getAsBoolean()) {
            throw new IllegalStateException("SCHEDULER_CAPABILITY_UNAVAILABLE");
        }
        if (old.enabled() == newEnabled && old.fixedDelayMs() == newDelay) {
            if (expectedVersion != old.version()) throw new IllegalStateException("SCHEDULER_VERSION_CONFLICT");
            return old;
        }
        if (!controls.patch(key, expectedVersion, newEnabled, newDelay, actor, clock.instant())) {
            throw new IllegalStateException("SCHEDULER_VERSION_CONFLICT");
        }
        ScheduledJobControl updated = detail(key);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("jobKey", key);
        fields.put("oldEnabled", old.enabled());
        fields.put("newEnabled", newEnabled);
        fields.put("oldDelayMs", old.fixedDelayMs());
        fields.put("newDelayMs", newDelay);
        fields.put("result", "SUCCESS");
        if (old.enabled() != newEnabled) {
            audit.append("SCHEDULER", newEnabled ? "ENABLE" : "DISABLE", actor, traceId, fields);
        }
        if (old.fixedDelayMs() != newDelay) {
            audit.append("SCHEDULER", "CHANGE_DELAY", actor, traceId, fields);
        }
        return updated;
    }

    public SchedulerDispatcher.ExecutionOutcome runOnce(String key, String actor, String traceId) {
        registry.require(key);
        Objects.requireNonNull(actor);
        audit.append("SCHEDULER", "RUN_ONCE_REQUESTED", actor, traceId,
                Map.of("jobKey", key, "result", "REQUESTED"));
        try {
            SchedulerDispatcher.ExecutionOutcome outcome = dispatcher.runOnce(key);
            audit.append("SCHEDULER", "RUN_ONCE", actor, traceId,
                    Map.of("jobKey", key, "result", outcome.name()));
            return outcome;
        } catch (RuntimeException failure) {
            audit.append("SCHEDULER", "RUN_ONCE", actor, traceId,
                    Map.of("jobKey", key, "result", "REJECTED"));
            throw failure;
        }
    }

    private boolean known(String key) {
        try {
            registry.require(key);
            return true;
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

}
