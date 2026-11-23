package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimCancellationService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimExecutionStatusService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimOrderQuery;
import com.guidinglight.nexusquant.trading.application.result.CancelOrderResult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/paper-trading/strategy-sim/runs")
public class StrategySimOrderController {
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    private final StrategySimOrderQuery orders;
    private final StrategySimExecutionStatusService statuses;
    private StrategySimCancellationService cancellations;

    @Autowired(required = false)
    private void bindCancellations(StrategySimCancellationService service) {
        this.cancellations = service;
    }

    public StrategySimOrderController(
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles,
            StrategySimOrderQuery orders,
            StrategySimExecutionStatusService statuses) {
        this.auth = auth;
        this.profiles = profiles;
        this.orders = orders;
        this.statuses = statuses;
    }

    @GetMapping("/{paperRunId}/orders/{orderId}")
    public StrategySimOrderQuery.Details order(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable @NotBlank @Size(max = 64) String orderId) {
        return owned(paperRunId, orderId);
    }

    /** 只投影已完成的自身SIM订单；稳定订单身份使未知响应后可查询和幂等重复刷新。 */
    @PostMapping("/{paperRunId}/orders/{orderId}/refresh-execution")
    public StrategySimExecutionStatusService.Projection refresh(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable @NotBlank @Size(max = 64) String orderId) {
        return statuses.refresh(owner(), paperRunId, orderId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "SIM_ORDER_NOT_FOUND"));
    }

    /** 只撤销用户可见实例的自身订单，实际状态迁移与取消审计仍由canonical编排持有。 */
    @PostMapping("/{paperRunId}/orders/{orderId}/cancel")
    public CancelOrderResult cancel(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable @NotBlank @Size(max = 64) String orderId) {
        if (cancellations == null)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "SIM_CANCEL_UNAVAILABLE");
        return cancellations
                .cancel(owner(), paperRunId, orderId, TraceIdContext.getOrCreate())
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "SIM_ORDER_NOT_FOUND"));
    }

    private StrategySimOrderQuery.Details owned(String paperRunId, String orderId) {
        return orders.read(owner(), paperRunId, orderId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "SIM_ORDER_NOT_FOUND"));
    }

    private long owner() {
        var user =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return profiles.findByUsername(user.username())
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"))
                .userId();
    }
}
