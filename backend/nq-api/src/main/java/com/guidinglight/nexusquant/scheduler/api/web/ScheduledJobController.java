package com.guidinglight.nexusquant.scheduler.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.scheduler.api.dto.ScheduledJobResponse;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobControl;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobManagementService;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobRegistry;
import com.guidinglight.nexusquant.scheduler.control.SchedulerDispatcher;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** 固定 Job 的只读状态与 ADMIN 运维操作。 */
@RestController
@ConditionalOnProperty(
        name = "nq.runtime.provider-observation.enabled",
        havingValue = "false",
        matchIfMissing = true)
@RequestMapping("/api/scheduler/jobs")
public class ScheduledJobController {
    private final ScheduledJobManagementService management;
    private final GatewayAuthFacade auth;
    private final ScheduledJobRegistry registry;

    public ScheduledJobController(
            ScheduledJobManagementService management,
            GatewayAuthFacade auth,
            ScheduledJobRegistry registry) {
        this.management = Objects.requireNonNull(management);
        this.auth = Objects.requireNonNull(auth);
        this.registry = Objects.requireNonNull(registry);
    }

    @GetMapping
    public List<ScheduledJobResponse> list() {
        return management.list().stream().map(this::response).toList();
    }

    @GetMapping("/{jobKey}")
    public ScheduledJobResponse detail(@PathVariable @NotBlank String jobKey) {
        return response(management.detail(jobKey));
    }

    @PatchMapping("/{jobKey}")
    public ScheduledJobResponse patch(
            @PathVariable @NotBlank String jobKey, @Valid @RequestBody PatchRequest request) {
        return response(
                management.patch(
                        jobKey,
                        request.enabled(),
                        request.fixedDelayMs(),
                        request.expectedVersion(),
                        actor(),
                        TraceIdContext.getOrCreate()));
    }

    /** 只读取与同一jobKey对应的目录约束；不调用eligible或runOnce，不改变任何启用/执行资格。 */
    private ScheduledJobResponse response(ScheduledJobControl control) {
        var definition = registry.require(control.jobKey());
        return new ScheduledJobResponse(control, definition.minDelayMs(), definition.maxDelayMs());
    }

    @PostMapping("/{jobKey}/run-once")
    @ResponseStatus(HttpStatus.OK)
    public RunOnceResponse runOnce(@PathVariable @NotBlank String jobKey) {
        return new RunOnceResponse(
                jobKey, management.runOnce(jobKey, actor(), TraceIdContext.getOrCreate()));
    }

    private String actor() {
        // 审计身份取认证链的规范用户名，不能序列化包含其他声明的 principal。
        return auth.currentUser()
                .map(TokenClaims::username)
                .filter(username -> !username.isBlank())
                .orElseThrow(() -> new IllegalStateException("SCHEDULER_ACTOR_UNAVAILABLE"));
    }

    public record PatchRequest(Boolean enabled, Long fixedDelayMs, @NotNull Long expectedVersion) {}

    public record RunOnceResponse(String jobKey, SchedulerDispatcher.ExecutionOutcome result) {}
}
