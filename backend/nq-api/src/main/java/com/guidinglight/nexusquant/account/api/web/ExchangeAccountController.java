package com.guidinglight.nexusquant.account.api.web;

import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountResponse;
import com.guidinglight.nexusquant.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.auth.application.GatewayAuthFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Objects;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ExchangeAccountController 提供账户上下文与账户管理页的最小查询接口。
 */
@Validated
@RestController
@RequestMapping("/api/exchange-accounts")
@Tag(name = "Exchange Account API", description = "账户上下文与账户管理页接口。")
public class ExchangeAccountController {

    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;
    private final ExchangeAccountQueryService exchangeAccountQueryService;

    public ExchangeAccountController(
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService,
            ExchangeAccountQueryService exchangeAccountQueryService
    ) {
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.currentUserProfileService = Objects.requireNonNull(
                currentUserProfileService,
                "currentUserProfileService must not be null"
        );
        this.exchangeAccountQueryService = Objects.requireNonNull(
                exchangeAccountQueryService,
                "exchangeAccountQueryService must not be null"
        );
    }

    @GetMapping
    @Operation(
            summary = "列出当前用户账户上下文",
            description = "返回当前登录用户可见的 exchange accounts 摘要。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<ExchangeAccountResponse> list() {
        var currentUser = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        var userProfile = currentUserProfileService.findByUsername(currentUser.username())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return exchangeAccountQueryService.listByOwnerUserId(userProfile.userId())
                .stream()
                .map(ExchangeAccountResponse::from)
                .toList();
    }
}



