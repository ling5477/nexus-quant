package com.guidinglight.nexusquant.account.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.exception.AccountFeeSyncException;
import com.guidinglight.nexusquant.account.application.service.AccountFeeService;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 不读取密文的费率历史与原操作查询；同步只委托正式受控来源。 */
@RestController
@Validated
@RequestMapping("/api/exchange-accounts/{accountId}/fees")
public class AccountFeeController {
    private final AccountFeeService service;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;

    public AccountFeeController(
            AccountFeeService service, GatewayAuthFacade auth, CurrentUserProfileService users) {
        this.service = service;
        this.auth = auth;
        this.users = users;
    }

    /** 按当前登录用户读取账户费率历史；成功空列表不替代来源不可用状态。 */
    @GetMapping
    public History history(
            @PathVariable @Positive long accountId,
            @RequestParam String symbol,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "20") int limit) {
        var history = service.history(owner(), accountId, symbol, before, limit);
        return new History(
                history.items().stream().map(Response::from).toList(),
                history.hasMore(),
                history.nextBefore(),
                history.synchronizationAvailable(),
                history.generatedAt(),
                history.scope());
    }

    /** 只返回原同步已提交回执；404表示当前未记录，不能证明在途请求失败。 */
    @GetMapping("/operations/{operationId}")
    public Response result(@PathVariable @Positive long accountId, @PathVariable UUID operationId) {
        return service.result(owner(), accountId, operationId)
                .map(Response::from)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "fee sync result is not recorded"));
    }

    /** 使用客户端稳定UUID显式同步；所有者由认证上下文提供，冲突不自动改身份或重试。 */
    @PostMapping("/operations/{operationId}")
    public Response sync(
            @PathVariable @Positive long accountId,
            @PathVariable UUID operationId,
            @Valid @RequestBody Request request) {
        try {
            return Response.from(
                    service.sync(
                            owner(),
                            accountId,
                            operationId,
                            request.credentialId(),
                            request.symbol()));
        } catch (AccountFeeSyncException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    private long owner() {
        var actor =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return users.findByUsername(actor.username())
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"))
                .userId();
    }

    /** 客户端只提交凭据版本和正式现货标的；不接收私钥、密文、供应方原始响应或用户身份。 */
    public record Request(
            @NotNull @Positive Long credentialId,
            @NotNull @Pattern(regexp = "[A-Z0-9]{1,20}-[A-Z0-9]{1,20}") String symbol) {}

    public record History(
            List<Response> items,
            boolean hasMore,
            UUID nextBefore,
            boolean synchronizationAvailable,
            Instant generatedAt,
            String scope) {}

    /** 白名单响应保留精确符号与来源时点；隐藏内部输入指纹和用户身份，不序列化凭据材料。 */
    public record Response(
            UUID observationId,
            UUID operationId,
            long exchangeAccountId,
            long credentialId,
            String exchangeCode,
            String tradeEnv,
            String symbol,
            String source,
            String rateConvention,
            BigDecimal makerProviderRate,
            BigDecimal takerProviderRate,
            BigDecimal makerCostRate,
            BigDecimal takerCostRate,
            String tier,
            Instant providerAt,
            Instant observedAt,
            Instant expiresAt,
            String sourceSha256,
            String status,
            String scope,
            String providerTimeBasis,
            JsonNode commissionTerms) {
        static Response from(AccountFeeObservation row) {
            var quote = row.quote();
            return new Response(
                    row.observationId(),
                    row.operationId(),
                    row.exchangeAccountId(),
                    row.credentialId(),
                    row.exchangeCode(),
                    row.tradeEnv(),
                    row.symbol(),
                    quote.source(),
                    quote.rateConvention(),
                    quote.makerProviderRate(),
                    quote.takerProviderRate(),
                    quote.makerCostRate(),
                    quote.takerCostRate(),
                    quote.tier(),
                    quote.providerAt(),
                    quote.observedAt(),
                    row.expiresAt(),
                    row.sourceSha256(),
                    "SUCCEEDED",
                    "CURRENT_ACCOUNT_AT_OBSERVATION",
                    quote.providerTimeBasis(),
                    quote.commissionTerms() == null
                            ? null
                            : quote.commissionTerms().snapshot(new ObjectMapper()));
        }
    }
}
