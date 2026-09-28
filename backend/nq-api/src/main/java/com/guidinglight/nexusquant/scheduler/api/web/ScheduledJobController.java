package com.guidinglight.nexusquant.scheduler.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobControl;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobManagementService;
import com.guidinglight.nexusquant.scheduler.control.SchedulerDispatcher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 固定 Job 的只读状态与 ADMIN 运维操作。 */
@RestController
@ConditionalOnProperty(name = "nq.runtime.provider-observation.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/scheduler/jobs")
public class ScheduledJobController {
    private final ScheduledJobManagementService management;

    public ScheduledJobController(ScheduledJobManagementService management) {
        this.management = Objects.requireNonNull(management);
    }

    @GetMapping
    public List<ScheduledJobControl> list() {
        return management.list();
    }

    @GetMapping("/{jobKey}")
    public ScheduledJobControl detail(@PathVariable @NotBlank String jobKey) {
        return management.detail(jobKey);
    }

    @PatchMapping("/{jobKey}")
    public ScheduledJobControl patch(@PathVariable @NotBlank String jobKey,
            @Valid @RequestBody PatchRequest request) {
        return management.patch(jobKey, request.enabled(), request.fixedDelayMs(),
                request.expectedVersion(), actor(), TraceIdContext.getOrCreate());
    }

    @PostMapping("/{jobKey}/run-once")
    @ResponseStatus(HttpStatus.OK)
    public RunOnceResponse runOnce(@PathVariable @NotBlank String jobKey) {
        return new RunOnceResponse(jobKey,
                management.runOnce(jobKey, actor(), TraceIdContext.getOrCreate()));
    }

    private static String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("SCHEDULER_ACTOR_UNAVAILABLE");
        }
        return auth.getName();
    }

    public record PatchRequest(Boolean enabled, Long fixedDelayMs, @NotNull Long expectedVersion) { }
    public record RunOnceResponse(String jobKey, SchedulerDispatcher.ExecutionOutcome result) { }
}
