package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunAlertAckRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunAlertCreateRequestBody;
import com.guidinglight.nexusquant.paper.api.dto.PaperRunAlertResponse;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.research.application.paper.command.PaperAlertOperationCommand;
import com.guidinglight.nexusquant.research.application.paper.service.PaperAlertOperationService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunMonitorService;
import com.guidinglight.nexusquant.research.domain.paper.PaperAlertOperation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/** 新旧告警写入口共用原编号；原主体查询历史回执，当前告警另由精确只读接口提供。 */
@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/runs/{paperRunId}")
public class PaperAlertOperationController {
    private final PaperAlertOperationService operations;
    private final PaperRunMonitorService monitor;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;

    public PaperAlertOperationController(
            PaperAlertOperationService operations,
            PaperRunMonitorService monitor,
            GatewayAuthFacade auth,
            CurrentUserProfileService users) {
        this.operations = operations;
        this.monitor = monitor;
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/alert-operations/{operationId}")
    public Response submit(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable UUID operationId,
            @RequestBody PaperAlertOperationCommand command) {
        return response(execute(paperRunId, operationId, command));
    }

    /** 缺少原回执不能推断为原请求未执行；其他主体或错误父路径均不返回该历史。 */
    @GetMapping("/alert-operations/{operationId}")
    public Response find(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable UUID operationId) {
        return response(
                operations
                        .find(operationId, actor().owner(), paperRunId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "PAPER_ALERT_OPERATION_RESULT_NOT_RECORDED")));
    }

    @GetMapping("/alerts/{alertId}")
    public PaperRunAlertResponse alert(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable @NotBlank @Size(max = 128) String alertId) {
        try {
            return PaperRunAlertResponse.from(monitor.getAlert(paperRunId, alertId));
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "PAPER_ALERT_SOURCE_NOT_FOUND", missing);
        }
    }

    @PostMapping("/alerts")
    public PaperRunAlertResponse create(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody PaperRunAlertCreateRequestBody request) {
        return PaperRunAlertResponse.from(
                execute(
                                paperRunId,
                                operationId,
                                new PaperAlertOperationCommand(
                                        PaperAlertOperationCommand.Type.CREATE,
                                        null,
                                        request.alertType(),
                                        request.severity(),
                                        request.title(),
                                        request.message(),
                                        request.source(),
                                        request.eventSnapshotJson()))
                        .result());
    }

    @PatchMapping("/alerts/{alertId}/ack")
    public PaperRunAlertResponse acknowledge(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable @NotBlank @Size(max = 128) String alertId,
            @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody(required = false) PaperRunAlertAckRequestBody request) {
        // 旧字段仍可接收以兼容调用者，但正式操作者只能来自认证上下文。
        return PaperRunAlertResponse.from(
                execute(
                                paperRunId,
                                operationId,
                                state(PaperAlertOperationCommand.Type.ACK, alertId))
                        .result());
    }

    @PatchMapping("/alerts/{alertId}/resolve")
    public PaperRunAlertResponse resolve(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable @NotBlank @Size(max = 128) String alertId,
            @RequestHeader("Idempotency-Key") UUID operationId) {
        return PaperRunAlertResponse.from(
                execute(
                                paperRunId,
                                operationId,
                                state(PaperAlertOperationCommand.Type.RESOLVE, alertId))
                        .result());
    }

    private PaperAlertOperation execute(String runId, UUID id, PaperAlertOperationCommand command) {
        Actor actor = actor();
        if (command == null) throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
        // 输入问题仍为400；业务来源不可见或子对象不属于父路径时沿用原404合同，不能泄露其owner。
        command.fingerprint(actor.owner(), runId);
        try {
            return operations.submit(
                    id,
                    actor.owner(),
                    actor.username(),
                    runId,
                    command,
                    TraceIdContext.getOrCreate());
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "PAPER_ALERT_SOURCE_NOT_FOUND", missing);
        }
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

    private static PaperAlertOperationCommand state(
            PaperAlertOperationCommand.Type type, String id) {
        return new PaperAlertOperationCommand(type, id, null, null, null, null, null, null);
    }

    private static Response response(PaperAlertOperation operation) {
        return new Response(
                operation.operationId(),
                operation.type(),
                operation.paperRunId(),
                "COMPLETED",
                operation.inputSha256(),
                PaperRunAlertResponse.from(operation.result()),
                operation.completedAt());
    }

    private record Actor(long owner, String username) {}

    public record Response(
            UUID operationId,
            String type,
            String paperRunId,
            String status,
            String inputSha256,
            PaperRunAlertResponse result,
            Instant completedAt) {}
}
