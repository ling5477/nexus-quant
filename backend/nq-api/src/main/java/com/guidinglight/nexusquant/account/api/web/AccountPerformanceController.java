package com.guidinglight.nexusquant.account.api.web;

import com.guidinglight.nexusquant.account.application.service.AccountPerformanceService;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.constraints.Positive;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 当前用户账户的有界表现读取；不存在与无权访问统一为404，GET无采样副作用。 */
@RestController
@Validated
@RequestMapping("/api/exchange-accounts/{accountId}/performance")
public class AccountPerformanceController {
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    private final AccountPerformanceService performance;

    public AccountPerformanceController(
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles,
            AccountPerformanceService performance) {
        this.auth = auth;
        this.profiles = profiles;
        this.performance = performance;
    }

    /** 只读取当前用户账户的既有USDT测量；范围与覆盖由服务校验，GET不采样、不补造资产历史。 */
    @GetMapping
    @Operation(
            summary = "读取当前用户账户表现",
            description = "USDT计价的NQ管理投影；最多1000次真实采样并返回实际覆盖，不推断交易所全账户或未覆盖历史。")
    public AccountPerformanceService.View read(
            @PathVariable @Positive long accountId,
            @RequestParam(required = false) String quoteCurrency,
            @RequestParam(required = false) String valuationCurrency,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        var user =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        long owner =
                profiles.findByUsername(user.username())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .userId();
        // 兼容旧参数名；同时传入时不能静默选择一个，以免把不同计价请求混为同一范围。
        if (quoteCurrency != null
                && valuationCurrency != null
                && !quoteCurrency.equals(valuationCurrency)) {
            throw new IllegalArgumentException("VALUATION_CURRENCY_PARAMETER_CONFLICT");
        }
        String currency =
                valuationCurrency != null
                        ? valuationCurrency
                        : quoteCurrency != null ? quoteCurrency : "USDT";
        return performance.read(owner, accountId, currency, from, to);
    }
}
