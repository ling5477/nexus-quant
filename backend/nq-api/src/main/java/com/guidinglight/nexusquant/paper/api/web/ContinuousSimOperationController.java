package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperation;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationRequest;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/** 原主体查询原回执；旧URL保留但必须传稳定编号，不再接受无法恢复的匿名状态命令。 */
@Validated
@RestController
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
@RequestMapping("/api/paper-trading/strategy-sim")
public class ContinuousSimOperationController {
    private final ContinuousSimOperationService operations;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;

    public ContinuousSimOperationController(
            ContinuousSimOperationService operations,
            GatewayAuthFacade auth,
            CurrentUserProfileService users) {
        this.operations = operations;
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/continuous-operations")
    public Response submit(@RequestBody ContinuousSimOperationRequest request) {
        return response(execute(request));
    }

    /** 404只表示该主体当前没有可读回执，不能据此换编号重复创建。 */
    @GetMapping("/continuous-operations/{operationId}")
    public Response find(@PathVariable UUID operationId) {
        return response(
                operations
                        .find(operationId, actor().owner())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "CONTINUOUS_SIM_OPERATION_RESULT_NOT_RECORDED")));
    }

    @PostMapping("/runs/continuous")
    public ContinuousSimRunService.Status create(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody StrategySimRunController.CreateRequest request) {
        return execute(
                        new ContinuousSimOperationRequest(
                                operationId,
                                ContinuousSimOperationRequest.Type.CREATE,
                                request.publishId(),
                                request.budget(),
                                null))
                .result();
    }

    @PostMapping("/runs/{paperRunId}/continuous/stop")
    public ContinuousSimRunService.Status pause(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @PathVariable @NotBlank @Size(max = 128) String paperRunId) {
        return execute(
                        new ContinuousSimOperationRequest(
                                operationId,
                                ContinuousSimOperationRequest.Type.PAUSE,
                                null,
                                null,
                                paperRunId))
                .result();
    }

    @PostMapping("/runs/{paperRunId}/continuous/resume")
    public ContinuousSimRunService.Status resume(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @PathVariable @NotBlank @Size(max = 128) String paperRunId) {
        return execute(
                        new ContinuousSimOperationRequest(
                                operationId,
                                ContinuousSimOperationRequest.Type.RESUME,
                                null,
                                null,
                                paperRunId))
                .result();
    }

    private ContinuousSimOperation execute(ContinuousSimOperationRequest request) {
        var actor = actor();
        return operations.submit(
                request, actor.owner(), actor.username(), TraceIdContext.getOrCreate());
    }

    private Actor actor() {
        String username =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .username();
        long owner =
                users.findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .userId();
        return new Actor(owner, username);
    }

    private static Response response(ContinuousSimOperation operation) {
        return new Response(
                operation.operationId(),
                operation.type(),
                operation.publishId(),
                operation.budget() == null
                        ? null
                        : operation.budget().stripTrailingZeros().toPlainString(),
                operation.paperRunId(),
                "COMPLETED",
                operation.result(),
                operation.completedAt());
    }

    private record Actor(long owner, String username) {}

    public record Response(
            UUID operationId,
            ContinuousSimOperationRequest.Type type,
            String publishId,
            String budget,
            String paperRunId,
            String status,
            ContinuousSimRunService.Status result,
            Instant completedAt) {}
}
