package com.guidinglight.nexusquant.account.api.web;

import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountResponse;
import com.guidinglight.nexusquant.account.application.service.AccountCommandService;
import com.guidinglight.nexusquant.account.domain.AccountCommand;
import com.guidinglight.nexusquant.account.domain.AccountCommandReceipt;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
 * 新正式命令入口复用原账户服务，旧客户端接口保留；结果查询只返回当前主体的成功回执。
 */
@RestController
@RequestMapping("/api/exchange-accounts/commands")
public class AccountCommandController {
    private final AccountCommandService commands;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;

    public AccountCommandController(
            AccountCommandService commands,
            GatewayAuthFacade auth,
            CurrentUserProfileService users) {
        this.commands = commands;
        this.auth = auth;
        this.users = users;
    }

    /**
     * 客户端稳定UUID只标识原命令；所有者来自认证，正式服务负责业务前提、幂等及事务回执。
     */
    @PostMapping("/{commandId}")
    public Response execute(@PathVariable UUID commandId, @Valid @RequestBody Request request) {
        return Response.from(
                commands.execute(
                        owner(),
                        commandId,
                        new AccountCommand(
                                request.type(),
                                request.accountId(),
                                request.exchangeCode(),
                                request.tradeEnv(),
                                request.accountAlias(),
                                request.externalAccountRef())));
    }

    /**
     * 只查当前用户的已提交回执；404仍可能是原请求在途，不提供自动重新提交授权。
     */
    @GetMapping("/{commandId}")
    public Response result(@PathVariable UUID commandId) {
        return commands.find(owner(), commandId)
                .map(Response::from)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "account command result is not recorded"));
    }

    private Long owner() {
        var user =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return users.findByUsername(user.username())
                .map(profile -> profile.userId())
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"));
    }

    public record Request(
            @NotNull AccountCommand.Type type,
            @Positive Long accountId,
            @Size(max = 32) String exchangeCode,
            @Size(max = 8) String tradeEnv,
            @Size(max = 64) String accountAlias,
            @Size(max = 128) String externalAccountRef) {
    }

    public record Response(
            UUID commandId,
            AccountCommand.Type type,
            String status,
            Instant completedAt,
            ExchangeAccountResponse result) {
        static Response from(AccountCommandReceipt receipt) {
            return new Response(
                    receipt.commandId(),
                    receipt.type(),
                    "SUCCEEDED",
                    receipt.completedAt(),
                    ExchangeAccountResponse.from(receipt.result()));
        }
    }
}
