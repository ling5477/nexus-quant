package com.guidinglight.nexusquant.scheduler.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.scheduler.api.dto.ScheduledJobResponse;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobOperation;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobOperationConflictException;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobOperationRequest;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobOperationService;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobRegistry;
import com.guidinglight.nexusquant.scheduler.control.SchedulerDispatcher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * ADMIN调度回执接口；原用户只能查询自己的UUID，404不表示原请求一定未曾执行。
 */
@RestController
@ConditionalOnProperty(
        name = "nq.runtime.provider-observation.enabled",
        havingValue = "false",
        matchIfMissing = true)
@RequestMapping("/api/scheduler/operations")
public class ScheduledJobOperationController {
    private final ScheduledJobOperationService operations;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;
    private final ScheduledJobRegistry registry;

    public ScheduledJobOperationController(
            ScheduledJobOperationService operations,
            GatewayAuthFacade auth,
            CurrentUserProfileService users,
            ScheduledJobRegistry registry) {
        this.operations = operations;
        this.auth = auth;
        this.users = users;
        this.registry = registry;
    }

    @PostMapping
    public Response submit(@RequestBody ScheduledJobOperationRequest request) {
        var actor = actor();
        long owner =
                users.findByUsername(actor)
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .userId();
        try {
            return response(operations.submit(request, owner, actor, TraceIdContext.getOrCreate()));
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        } catch (IllegalStateException conflict) {
            if (conflict.getMessage() != null
                    && (conflict.getMessage().equals("SCHEDULER_OPERATION_ID_CONFLICT")
                    || conflict.getMessage().equals("SCHEDULER_OPERATION_PENDING")
                    || conflict.getMessage().equals("SCHEDULER_VERSION_CONFLICT")
                    || conflict.getMessage().equals("SCHEDULER_CAPABILITY_UNAVAILABLE"))) {
                throw new ScheduledJobOperationConflictException(conflict.getMessage());
            }
            throw conflict;
        }
    }

    @GetMapping("/{operationId}")
    public Response find(@PathVariable UUID operationId) {
        long owner =
                users.findByUsername(actor())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .userId();
        return response(
                operations
                        .find(operationId, owner)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "scheduler operation result is not recorded")));
    }

    private String actor() {
        return auth.currentUser()
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"))
                .username();
    }

    private Response response(ScheduledJobOperation value) {
        var job = registry.require(value.jobKey());
        return new Response(
                value.operationId(),
                value.type(),
                value.jobKey(),
                value.status(),
                value.createdAt(),
                value.startedAt(),
                value.completedAt(),
                value.result(),
                value.reasonCode(),
                value.control() == null
                        ? null
                        : new ScheduledJobResponse(
                        value.control(), job.minDelayMs(), job.maxDelayMs()));
    }

    public record Response(
            UUID operationId,
            ScheduledJobOperation.Type type,
            String jobKey,
            ScheduledJobOperation.Status status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            SchedulerDispatcher.ExecutionOutcome result,
            String reasonCode,
            ScheduledJobResponse control) {
    }
}
